/*
 * Copyright 2017 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.nihms.transport.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.dataconservancy.nihms.transport.TransportResponse;
import org.dataconservancy.nihms.transport.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.TYPE.binary;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.PATH_SEP;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.makeDirectories;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.performSilently;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.setDataType;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.setPasv;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class FtpTransportSession implements TransportSession {

    private static final Logger LOG = LoggerFactory.getLogger(FtpTransportSession.class);

    private static final String ERR_TRANSFER_WITH_CODE = "Error transferring file %s to %s:%s; " +
            "(FTP server reply code %s) error message: %s";

    private static final String ERR_TRANSFER = "Exception transferring file %s to %s:%s; error message: %s";

    /**
     * Whether or not the {@link #ftpClient} has been closed.
     */
    private boolean isClosed = false;

    /**
     * Used to submit jobs for transferring files
     */
    private ExecutorService executorService;

    /**
     * A connected FTP client
     */
    private FTPClient ftpClient;

    /**
     * A transfer that may still be in-progress
     */
    private FutureTask<TransportResponse> transfer;

    public FtpTransportSession(FTPClient ftpClient) {
        this(ftpClient, Executors.newSingleThreadExecutor());
    }

    private FtpTransportSession(FTPClient ftpClient, ExecutorService executorService) {
        this.executorService = executorService;
        this.ftpClient = ftpClient;
    }

    @Override
    public TransportResponse send(String destinationResource, InputStream content) {

        validateDestinationResource(destinationResource);

        this.transfer = new FutureTask<>(() -> {
            try {
                // make any parent directories defensively- if they don't exist create them,
                // if they do exist, don't create them
                makeDirectories(ftpClient, destinationResource.substring(0, destinationResource.lastIndexOf(PATH_SEP)));
                setPasv(ftpClient, true);
                setDataType(ftpClient, binary.name());
                return storeFile(destinationResource, content);
            } finally {
                content.close();
            }
        });

        executorService.submit(transfer);

        try {
            return transfer.get();
        } catch (InterruptedException e) {
            LOG.info(format(ERR_TRANSFER, destinationResource, "<host>", "<port>", "transfer was cancelled!"));
            return new TransportResponse() {
                @Override
                public boolean success() {
                    return false;
                }

                @Override
                public Throwable error() {
                    return e;
                }
            };
        } catch (ExecutionException e) {
            throw new RuntimeException(format(
                    ERR_TRANSFER, destinationResource, "<host>", "<port>", e.getMessage()), e);
        }

    }

    @Override
    public TransportResponse send(String destinationResource, Map<String, String> metadata, InputStream content) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean closed() {
        return isClosed;
    }

    @Override
    public void close() throws Exception {
        if (transfer != null && !transfer.isDone()) {
            LOG.debug("Closing {}@{}, cancelling pending transfer...",
                    this.getClass().getSimpleName(), toHexString(identityHashCode(this)));
            transfer.cancel(true);
        }

        if (this.isClosed) {
            LOG.debug("{}@{} is already closed.",
                    this.getClass().getSimpleName(), toHexString(identityHashCode(this)));
            return;
        }

        try {
            FtpUtil.disconnect(ftpClient);
        } catch (IOException e) {
            LOG.debug("Exception encountered while closing {}@{}, FTP client logout failed.  " +
                            "Continuing to close the object despite the exception: {}",
                    this.getClass().getSimpleName(), toHexString(identityHashCode(this)), e.getMessage(), e);
        }

        LOG.debug("Marking {}@{} as closed.",
                this.getClass().getSimpleName(), toHexString(identityHashCode(this)));
        this.isClosed = true;
    }

    TransportResponse storeFile(String destinationResource, InputStream content) {
        String cwd = performSilently(ftpClient, FTPClient::printWorkingDirectory);

        String directory;
        String fileName;

        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<Exception> caughtException = new AtomicReference<>();

        if (!destinationResource.contains(PATH_SEP)) {
            fileName = destinationResource;
            directory = null;
        } else {
            fileName = destinationResource.substring(destinationResource.lastIndexOf(PATH_SEP) + 1);
            directory = destinationResource.substring(0, destinationResource.lastIndexOf(PATH_SEP));
        }

        try {
            if (directory != null) {
                performSilently(ftpClient, ftpClient -> ftpClient.changeWorkingDirectory(directory));
            }
            performSilently(ftpClient, ftpClient -> ftpClient.storeFile(fileName, content));
            success.set(true);
        } catch (Exception e) {
            caughtException.set(e);
            success.set(false);
        } finally {
            if (directory != null) {
                try {
                    performSilently(ftpClient, ftpClient -> ftpClient.changeWorkingDirectory(cwd));
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        TransportResponse response = new TransportResponse() {
            @Override
            public boolean success() {
                return success.get();
            }

            @Override
            public Throwable error() {
                if (!success.get()) {
                    int code = ftpClient.getReplyCode();
                    String msg = ftpClient.getReplyString();
                    return new RuntimeException(
                            format(ERR_TRANSFER_WITH_CODE, destinationResource, "host", "port", code, msg),
                            caughtException.get());
                }

                return null;
            }
        };

        return response;
    }

    void validateDestinationResource(String destinationResource) {
        // at a minimum, the destination resource must specify a file name (i.e. not end with a directory separator)
        if (destinationResource.endsWith(PATH_SEP)) {
            throw new RuntimeException(format(ERR_TRANSFER, destinationResource, "<host>", "<port>", "Destination " +
                    "resource '" + destinationResource + "' must specify a file and not a directory"));
        }
    }

}
