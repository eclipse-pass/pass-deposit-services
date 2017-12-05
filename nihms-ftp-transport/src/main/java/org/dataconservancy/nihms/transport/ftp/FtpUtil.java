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

import com.google.common.net.InetAddresses;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.dataconservancy.nihms.util.function.ExceptionThrowingCommand;
import org.dataconservancy.nihms.util.function.ExceptionThrowingFunction;
import org.dataconservancy.nihms.util.function.ExceptionThrowingVoidCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;

class FtpUtil {

    private static final String OVERVIEW_CONNECTION_ATTEMPT = "({}) Connecting to {}:{} ...";

    private static final String CONNECTION_FAILED_WITH_EXCEPTION = "Connection *FAILED* to %s:%s; Exception was: %s";

    private static final String CONNECTION_ATTEMPT = "({}) Attempting connection to {}:{} ...";

    private static final String CONNECTION_ATTEMPT_FAILED = "({}) Connection *FAILED* to {}:{} (sleeping for {} ms) ...";

    private static final String CONNECTION_ATTEMPT_FAILED_WITH_EXCEPTION = "({}) Connection *FAILED* to {}:{} (sleeping for {} ms); Exception was: {}";

    private static final String ERR_CONNECT = "({}) Error connecting to {}:{}; error code from the FTP server was '{}', and the error string was '{}'";

    private static final String NOOP_FAILED = "({}) NOOP *FAILED*, connection to {}:{} not established.";

    private static final String ERR_REPLY = "Reply from the FTP server was '%s' (code: '%s')";

    private static final String ERR_CMD = "Exception performing FTP command; error message: %s";

    private static final Logger LOG = LoggerFactory.getLogger(FtpUtil.class);

    /**
     * Character used to separate path components: e.g. use of the forward slash in the path '/path/to/file.txt'
     * separates three components: 'path', 'to', and 'file.txt'
     */
    static final String PATH_SEP = "/";

    static final Function<FTPClient, Boolean> ACCEPT_POSITIVE_COMPLETION = (ftpClient) ->
            FTPReply.isPositiveCompletion(ftpClient.getReplyCode());

    static final Function<FTPClient, Boolean> ACCEPT_MKD_COMPLETION = (ftpClient) ->
            ACCEPT_POSITIVE_COMPLETION.apply(ftpClient) ||
                    acceptResponseCodes(550, 553).apply(ftpClient.getReplyCode());

    static final Consumer<FTPClient> ASSERT_POSITIVE_COMPLETION = (ftpClient) -> {
        if (!ACCEPT_POSITIVE_COMPLETION.apply(ftpClient)) {
            throw new RuntimeException(format(ERR_REPLY, ftpClient.getReplyString(), ftpClient.getReplyCode()));
        }
    };

    static final Consumer<FTPClient> ASSERT_MKD_COMPLETION = (ftpClient) -> {
        if (!ACCEPT_MKD_COMPLETION.apply(ftpClient)) {
            throw new RuntimeException(format(ERR_REPLY, ftpClient.getReplyString(), ftpClient.getReplyCode()));
        }
    };

    static void performSilently(ExceptionThrowingVoidCommand clientCommand) {
        try {
            clientCommand.perform();
        } catch (Exception e) {
            throw new RuntimeException(format(ERR_CMD, e.getMessage()), e);
        }
    }

    static <T> T performSilently(ExceptionThrowingCommand<T> clientCommand) {
        try {
            return clientCommand.perform();
        } catch (Exception e) {
            throw new RuntimeException(format(ERR_CMD, e.getMessage()), e);
        }
    }

    static <T> T performSilently(FTPClient ftpClient, ExceptionThrowingCommand<T> clientCommand) {
        return performSilently(ftpClient, clientCommand, ASSERT_POSITIVE_COMPLETION);
    }

    static <T> T performSilently(FTPClient ftpClient, ExceptionThrowingCommand<T> clientCommand, Consumer<FTPClient> callback) {
        try {
            T result = clientCommand.perform();
            callback.accept(ftpClient);
            return result;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(format(ERR_CMD, e.getMessage()), e);
        }
    }

    static <R> R performSilently(FTPClient ftpClient, ExceptionThrowingFunction<FTPClient, R> clientCommand) {
        return performSilently(ftpClient, clientCommand, ASSERT_POSITIVE_COMPLETION);
    }

    static <R> R performSilently(FTPClient ftpClient, ExceptionThrowingFunction<FTPClient, R> clientCommand, Consumer<FTPClient> callback) {
        try {
            R result = clientCommand.apply(ftpClient);
            callback.accept(ftpClient);
            return result;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            } else {
                throw new RuntimeException(format(ERR_CMD, e.getMessage()), e);
            }
        }
    }

    static void setPasv(FTPClient ftpClient, boolean usePasv) {
        if (usePasv) {
            performSilently(ftpClient::enterLocalPassiveMode);
        } else {
            performSilently(() -> {
                ftpClient.enterLocalActiveMode();
                return true;
            });
        }
    }

    static void setDataType(FTPClient ftpClient, String dataType) {
        if (dataType == null) {
            return;
        }

        FtpTransportHints.TYPE type = null;

        try {
            type = FtpTransportHints.TYPE.valueOf(dataType);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown FTP file type '" + dataType + "'");
        }

        switch (type) {
            case ascii:
                performSilently(ftpClient, () -> ftpClient.type(FTP.ASCII_FILE_TYPE));
                break;
            case binary:
                performSilently(ftpClient, () -> ftpClient.type(FTP.BINARY_FILE_TYPE));
                break;
            default:
                throw new RuntimeException("Unsupported FTP file type '" + dataType + "'");
        }
    }

    static void setTransferMode(FTPClient ftpClient, String transferMode) {
        if (transferMode == null) {
            return;
        }

        FtpTransportHints.MODE mode = null;
        try {
            mode = FtpTransportHints.MODE.valueOf(transferMode);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown FTP transfer mode '" + transferMode + "'");
        }

        switch (mode) {
            case stream:
                performSilently(ftpClient, () -> ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE));
                break;
            case block:
                performSilently(ftpClient, () -> ftpClient.setFileTransferMode(FTP.BLOCK_TRANSFER_MODE));
                break;
            case compressed:
                performSilently(ftpClient, () -> ftpClient.setFileTransferMode(FTP.COMPRESSED_TRANSFER_MODE));
                break;
            default:
                throw new RuntimeException("Unsupported FTP transfer mode '" + transferMode + "'");
        }

    }

    static void setWorkingDirectory(FTPClient ftpClient, String directoryPath) {
        makeDirectories(ftpClient, directoryPath);
        performSilently(ftpClient, () -> ftpClient.changeWorkingDirectory(directoryPath));
    }

    static void makeDirectories(FTPClient ftpClient, String destinationResource) {
        String[] parts = destinationResource.split(PATH_SEP);
        if (parts.length == 0) {
            return; // destination resource was "/"
        }

        String cwd = performSilently(ftpClient, FTPClient::printWorkingDirectory);

        try {
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                performSilently(ftpClient, () -> ftpClient.makeDirectory(part), ASSERT_MKD_COMPLETION);
                performSilently(ftpClient, () -> ftpClient.changeWorkingDirectory(part));
            }
        } finally {
            performSilently(ftpClient, () -> ftpClient.changeWorkingDirectory(cwd));
        }
    }

    static void login(FTPClient ftpClient, String username, String password) {
        performSilently(ftpClient, () -> ftpClient.login(username, password));
    }

    static Function<Integer, Boolean> acceptResponseCodes(Integer... responseCodes) {
        return (candidateResponseCode) -> Stream.of(responseCodes)
                .anyMatch((code) -> code.equals(candidateResponseCode));
    }

    /**
     * It seems there may be some issue with the Apache FTP library re-using closed sockets when
     * establishing new connections.  Through trial and error it seems important to {@link FTPClient#connect(String, int) connect}, and then issue a {@link FTPClient#noop() NOOP} <em>before</em> attempting a login.  This method issues a {@code NOOP} in order to validate that the socket is actually connected.
     * <p>
     * When a connection fails, it is assumed to be a transient failure (again, due to potential bugs in the underlying
     * Apache FTP library).  So this method will block and retry a failed connection up to a 30 second timeout before
     * giving up.
     * </p>
     * @param ftpClient the FTP client instance that is not yet connected
     * @param ftpHost the host to connect to (may be an IPv4, IPv6, or string domain name)
     * @param ftpPort the port to connect to
     */
    static void connect(FTPClient ftpClient, String ftpHost, int ftpPort) {
        LOG.debug(OVERVIEW_CONNECTION_ATTEMPT, ftpClientAsString(ftpClient), ftpHost, ftpPort);

        long waitMs = 2000;
        long start = System.currentTimeMillis();
        boolean connectionSuccess = false;
        Exception caughtException = null;

        do {
            try {
                LOG.debug(CONNECTION_ATTEMPT, ftpClientAsString(ftpClient), ftpHost, ftpPort);
                if (InetAddresses.isInetAddress(ftpHost)) {
                    ftpClient.connect(InetAddresses.forString(ftpHost), ftpPort);
                } else {
                    ftpClient.connect(ftpHost, ftpPort);
                }

                int replyCode = ftpClient.getReplyCode();
                String replyString = ftpClient.getReplyString();

                if (!FTPReply.isPositiveCompletion(replyCode)) {
                    LOG.debug(ERR_CONNECT, ftpClientAsString(ftpClient), ftpHost, ftpPort, replyCode, replyString);
                } else {
                    if (!ftpClient.sendNoOp()) {
                        LOG.debug(NOOP_FAILED, ftpClientAsString(ftpClient), ftpHost, ftpPort);
                    } else {
                        connectionSuccess = true;
                    }
                }
            } catch (Exception e) {
                caughtException = e;
                // Commonly catch FTPConnectionClosedException here
                // is there a bug in the Apache FTP library which attempts to re-use a socket that has been closed?
                // retry until a timeout is reached or a connection is successful.
                try {
                    LOG.debug(CONNECTION_ATTEMPT_FAILED_WITH_EXCEPTION,
                            ftpClientAsString(ftpClient), ftpHost, ftpPort, waitMs, e.getMessage(), e);
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
        } while (!connectionSuccess && System.currentTimeMillis() - start < 30000);

        if (!connectionSuccess) {
            if (caughtException != null) {
                throw new RuntimeException(format(CONNECTION_FAILED_WITH_EXCEPTION,
                        ftpHost, ftpPort, caughtException.getMessage()), caughtException);
            } else {
                throw new RuntimeException(format(CONNECTION_FAILED_WITH_EXCEPTION, ftpHost, ftpPort, "null"));
            }
        }

        LOG.debug("({}) Successfully connected to {}:{}", ftpClientAsString(ftpClient), ftpHost, ftpPort);
    }

    /**
     * Logs out and disconnects from the supplied FTP client, as long as {@code ftpClient.isConnected()} returns true.
     * <p>
     * This method simply invokes {@link #disconnect(FTPClient, boolean)} with {@code force} equal to {@code false}.
     * </p>
     *
     * @param ftpClient the FTP client to conditionally disconnect from
     * @throws IOException
     */
    static void disconnect(FTPClient ftpClient) throws IOException {
        disconnect(ftpClient, false);
    }

    /**
     * Conditionally log out and disconnect from the supplied FTP client.  If {@code force} is {@code false}, this
     * method first checks to see if the client is {@link FTPClient#isConnected() is connected} before attempting to
     * disconnect.  If {@code force} is {@code true}, the {@link FTPClient#isConnected() connection status} is
     * <em>not</em> considered prior to invoking {@link FTPClient#disconnect()}.
     *
     * @param ftpClient
     * @param force
     * @throws IOException
     */
    static void disconnect(FTPClient ftpClient, boolean force) throws IOException {
        if (ftpClient == null) {
            LOG.debug("({}) Not disconnecting because FTP client is null.", ftpClientAsString(ftpClient));
            return;
        }

        if (!force && !ftpClient.isConnected()) {
            LOG.debug("({}) Not disconnecting because the FTP client isn't connected.", ftpClientAsString(ftpClient));
            return;
        }

        ftpClient.logout();
        ftpClient.disconnect();
    }

    private static String ftpClientAsString(FTPClient ftpClient) {
        if (ftpClient == null) {
            return "null";
        } else {
            return ftpClient.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(ftpClient));
        }
    }

}
