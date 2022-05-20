/*
 * Copyright 2018 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.transport.ftp;

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.PATH_SEP;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.performSilently;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.setDataType;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.setPasv;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.net.ftp.FTPClient;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates a logged-in connection to an FTP server.
 *
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
    public TransportResponse send(PackageStream packageStream, Map<String, String> metadata) {

        PackageStream.Metadata streamMetadata = packageStream.metadata();

        validateDestinationResource(streamMetadata.name());

        this.transfer = new FutureTask<>(() -> {
            try (InputStream inputStream = packageStream.open()) {
                return storeFile(streamMetadata.name(), inputStream);
            }
        });

        executorService.submit(transfer);

        try {
            return transfer.get();
        } catch (InterruptedException e) {
            LOG.info(format(ERR_TRANSFER, streamMetadata.name(), "<host>", "<port>", "transfer was cancelled!"));
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
            LOG.info(format(ERR_TRANSFER, streamMetadata.name(), "<host>", "<port>", e.getMessage()), e);
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
        }

    }

    @Override
    public boolean closed() {
        return isClosed;
    }

    @Override
    public void close() throws Exception {
        LOG.debug("Closing {}@{}...",
                  this.getClass().getSimpleName(), toHexString(identityHashCode(this)));
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

    /**
     * Streams the supplied {@code content} to t
     *
     * @param destinationResource
     * @param content
     * @return
     */
    TransportResponse storeFile(String destinationResource, InputStream content) {
        String cwd = performSilently(ftpClient, FTPClient::printWorkingDirectory);

        String directory;
        String fileName;

        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        AtomicInteger ftpReplyCode = new AtomicInteger();
        AtomicReference<String> ftpReplyString = new AtomicReference<>();

        if (!destinationResource.contains(PATH_SEP)) {
            fileName = destinationResource;
            directory = null;
        } else {
            fileName = destinationResource.substring(destinationResource.lastIndexOf(PATH_SEP) + 1);
            directory = destinationResource.substring(0, destinationResource.lastIndexOf(PATH_SEP));
        }

        try {
            if (directory != null) {
                FtpUtil.setWorkingDirectory(ftpClient, directory);
            }
            setPasv(ftpClient, true);
            setDataType(ftpClient, FtpTransportHints.TYPE.binary.name());
            boolean result = ftpClient.storeFile(fileName, content);
            success.set(result);
            ftpReplyCode.set(ftpClient.getReplyCode());
            ftpReplyString.set(ftpClient.getReplyString());
        } catch (Exception e) {
            ftpReplyCode.set(ftpClient.getReplyCode());
            ftpReplyString.set(ftpClient.getReplyString());
            caughtException.set(e);
            success.set(false);

            try {
                // If the file transfer doesn't even start we need to abort the STOR command so that the server isn't
                // expecting data from us
                performSilently(ftpClient, () -> ftpClient.abort());
            } catch (Exception innerE) {
                // ignore
            }
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
                    return new RuntimeException(
                        format(ERR_TRANSFER_WITH_CODE, destinationResource, "host", "port",
                               ftpReplyCode.get(), ftpReplyString.get()), caughtException.get());
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
