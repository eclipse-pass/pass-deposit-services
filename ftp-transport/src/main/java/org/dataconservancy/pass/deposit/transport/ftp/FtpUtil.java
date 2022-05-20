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

import static java.lang.String.format;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.net.InetAddresses;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.dataconservancy.deposit.util.function.ExceptionThrowingCommand;
import org.dataconservancy.deposit.util.function.ExceptionThrowingFunction;
import org.dataconservancy.deposit.util.function.ExceptionThrowingVoidCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpUtil {

    private FtpUtil () {
        //never called
    }

    private static final String OVERVIEW_CONNECTION_ATTEMPT = "({}) Connecting to {}:{} ...";

    private static final String CONNECTION_FAILED_WITH_EXCEPTION = "Connection *FAILED* to %s:%s; Exception was: %s";

    private static final String CONNECTION_ATTEMPT = "({}) Attempting connection to {}:{} ...";

    private static final String CONNECTION_ATTEMPT_FAILED = "({}) Connection *FAILED* to {}:{} (sleeping for {} ms) ." +
                                                            "..";

    private static final String CONNECTION_ATTEMPT_FAILED_WITH_EXCEPTION = "({}) Connection *FAILED* to {}:{} " +
                                                                           "(sleeping for {} ms); Exception was: {}";

    private static final String ERR_CONNECT = "({}) Error connecting to {}:{}; error code from the FTP server was " +
                                              "'{}', and the error string was '{}'";

    private static final String NOOP_FAILED = "({}) NOOP *FAILED*, connection to {}:{} not established.";

    private static final String ERR_REPLY = "Reply from the FTP server was '%s' (code: '%s')";

    private static final String ERR_CMD = "Exception performing FTP command; error message: %s";

    private static final Logger LOG = LoggerFactory.getLogger(FtpUtil.class);

    /**
     * Character used to separate path components: e.g. use of the forward slash in the path '/path/to/file.txt'
     * separates three components: 'path', 'to', and 'file.txt'
     */
    static final String PATH_SEP = "/";

    /**
     * Checks the most recent {@link FTPClient#getReplyCode() reply code} using the supplied {@code ftpClient}.  Returns
     * {@code true} if the reply code is considered to indicate a positive completion of the previous command.  Returns
     * {@code false} otherwise.
     */
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

    /**
     * {@link ExceptionThrowingCommand#perform() Perform} the command.  Any exceptions thrown by the command are caught
     * and re-thrown as {@link RuntimeException}.
     *
     * @param clientCommand the command to perform
     * @throws RuntimeException if the command throw a checked exception
     */
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

    /**
     * Performs the {@code clientCommand} using the supplied {@code ftpClient}.  If the command does not succeed (that
     * is, the reply code from the FTP server is not considered to be a positive completion reply code (typically a
     * code in the range of 200 - 299 inclusive)), then a {@code RuntimeException} will be thrown.
     * <p>
     * If the {@code clientCommand} throws an exception when it is executed (and the exception prevents the reply code
     * from the FTP server being retrieved), it will be wrapped as a {@link RuntimeException} and re-thrown.
     * </p>
     *
     * @param ftpClient     the FTP client used to execute the command
     * @param clientCommand the command to execute against the FTP server using the supplied client
     * @param <T>           the type returned by the command
     * @return the result of the command
     * @throws RuntimeException if the command is executed and is not considered successful, or if the command fails to
     *                          execute.
     */
    static <T> T performSilently(FTPClient ftpClient, ExceptionThrowingCommand<T> clientCommand) {
        return performSilently(ftpClient, clientCommand, ASSERT_POSITIVE_COMPLETION);
    }

    /**
     * Performs the {@code clientCommand} using the supplied {@code ftpClient}, then invokes the {@code callback}.  A
     * typical callback will use the {@code ftpClient} to check the result of the {@code clientCommand} that was just
     * executed, and take some action based on the response code.  See {@link #ASSERT_POSITIVE_COMPLETION} as an
     * example.
     * <p>
     * If the {@code clientCommand} throws an exception when it is executed (and the exception prevents the callback
     * from being executed), it will be wrapped as a {@link RuntimeException} and re-thrown.
     * </p>
     *
     * @param ftpClient     the FTP client used to execute the command
     * @param clientCommand the command to execute against the FTP server using the supplied client
     * @param <T>           the type returned by the command
     * @return the result of the command
     * @throws RuntimeException if the command is executed and is not considered successful, or if the command fails to
     *                          execute.
     */
    static <T> T performSilently(FTPClient ftpClient, ExceptionThrowingCommand<T> clientCommand,
                                 Consumer<FTPClient> callback) {
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

    static <R> R performSilently(FTPClient ftpClient, ExceptionThrowingFunction<FTPClient, R> clientCommand,
                                 Consumer<FTPClient> callback) {
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
            ftpClient.setUseEPSVwithIPv4(true);
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

        boolean success = false;

        switch (type) {
            case ascii:
                try {
                    success = ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
                } catch (IOException e) {
                    throw new RuntimeException("Error setting FTP file type to " + type, e);
                }
                break;
            case binary:
                try {
                    success = ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                } catch (IOException e) {
                    throw new RuntimeException("Error setting FTP file type to " + type, e);
                }
                break;
            default:
                throw new RuntimeException("Unsupported FTP file type '" + dataType + "'");
        }

        if (!success) {
            throw new RuntimeException("Unable to set FTP file type to " + type);
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
        LOG.trace("Setting working directory to {}", directoryPath);
        if (directoryPath == null || directoryPath.trim().length() == 0) {
            return;
        }
        makeDirectories(ftpClient, directoryPath);
        performSilently(ftpClient, () -> ftpClient.changeWorkingDirectory(directoryPath));
    }

    /**
     * Creates the directories specified in {@code directories}.
     * <h3>Example invocation: <em>FtpUtil.makeDirectories(client, "/foo/bar");</em></h3>
     * <p>
     * This method will attempt to create the directory {@code /foo/bar} on the FTP server relative to the root
     * directory (i.e. {@code /}).
     * </p>
     * <h3>Example invocation: <em>FtpUtil.makeDirectories(client, "foo/bar");</em></h3>
     * <p>
     * This method will attempt to create the directory {@code foo/bar} on the FTP server, relative to the current
     * working directory.
     * </p>
     * <h3>Example invocation: <em>FtpUtil.makeDirectories(client, "picture.jpg");</em></h3>
     * <p>
     * This method attempt to create the directory {@code picture.jpg} on the FTP server, relative to the current
     * working directory.  The implementation has no way to tell that {@code picture.jpg} probably is a file and
     * not a directory.
     * </p>
     *
     * @param ftpClient   the FTP client, which is connected and logged in to a remote FTP server
     * @param directories the directory to create, comprised of at least one path element.  Relative directories will
     *                    be created relative to the current working directory.
     */
    static void makeDirectories(FTPClient ftpClient, String directories) {
        final String origCwd = performSilently(ftpClient, FTPClient::printWorkingDirectory);
        String[] parts = directories.split(PATH_SEP);
        if (parts.length == 0) {
            return; // destination resource was "/"
        }

        LOG.trace("Creating intermediate directories for destination resource '{}' (cwd '{}')",
                  directories, origCwd);

        if (isPathAbsolute(directories)) {
            performSilently(ftpClient, () -> ftpClient.changeWorkingDirectory(PATH_SEP));
        }

        String cwd = performSilently(ftpClient, ftpClient::printWorkingDirectory);

        try {
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if ("".equals(part)) {
                    continue;
                }
                LOG.trace("-> Creating intermediate directory relative to '{}': '{}'", cwd, part);
                performSilently(ftpClient, () -> ftpClient.makeDirectory(part), ASSERT_MKD_COMPLETION);
                performSilently(ftpClient, () -> ftpClient.changeWorkingDirectory(part));
                cwd = performSilently(ftpClient, FTPClient::printWorkingDirectory);
            }
        } finally {
            LOG.trace("-> Changing back to original working directory to '{}'", origCwd);
            performSilently(ftpClient, () -> ftpClient.changeWorkingDirectory(origCwd));
        }
    }

    /**
     * Attempt to login to the FTP server.
     * <p>
     * The supplied {@code ftpClient} should already be {@link #connect(FTPClient, String, int) connected}.
     * </p>
     *
     * @param ftpClient the connected FTP client
     * @param username  the username to authenticate as
     * @param password  the password for the user
     * @throws RuntimeException if authentication fails
     */
    static void login(FTPClient ftpClient, String username, String password) {
        performSilently(ftpClient, () -> ftpClient.login(username, password));
    }

    static Function<Integer, Boolean> acceptResponseCodes(Integer... responseCodes) {
        return (candidateResponseCode) -> Stream.of(responseCodes)
                                                .anyMatch((code) -> code.equals(candidateResponseCode));
    }

    /**
     * It seems there may be some issue with the Apache FTP library re-using closed sockets when
     * establishing new connections.  Through trial and error it seems important to
     * {@link FTPClient#connect(String, int) connect}, and then issue a {@link FTPClient#noop() NOOP} <em>before</em>
     * attempting a login.  This method issues a {@code NOOP} in order to validate that the socket is actually
     * connected.
     * <p>
     * When a connection fails, it is assumed to be a transient failure (again, due to potential bugs in the underlying
     * Apache FTP library).  So this method will block and retry a failed connection up to a 30 second timeout before
     * giving up.  If this method cannot connect within the timeout period, a {@code RuntimeException} will be thrown.
     * The thrown exception will have its {@link Exception#getCause() underlying cause set}, if one was caught.
     * </p>
     *
     * @param ftpClient the FTP client instance that is not yet connected
     * @param ftpHost   the host to connect to (may be an IPv4, IPv6, or string domain name)
     * @param ftpPort   the port to connect to
     * @throws RuntimeException if the connection fails (the cause of the connection failure will be set, if one was
     *                          caught)
     */
    static void connect(FTPClient ftpClient, String ftpHost, int ftpPort) {
        LOG.debug(OVERVIEW_CONNECTION_ATTEMPT, ftpClientAsString(ftpClient), ftpHost, ftpPort);

        long waitMs = 2000;
        double backoffFactor = 1.5;
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
                    waitMs = Math.round(waitMs * backoffFactor);
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

    /**
     * Returns {@code true} if {@code path} is considered absolute.
     *
     * @param path a path, which may or may not be absolute
     * @return {@code true} if the path is absolute
     */
    public static boolean isPathAbsolute(String path) {
        if (path == null || path.trim().length() == 0) {
            throw new IllegalArgumentException("Path must not be null or an empty string");
        }
        return path.trim().startsWith(PATH_SEP);
    }

    /**
     * Uses the supplied client to verify the supplied directory exists.  If the supplied directory does not exist, a
     * {@code RuntimeException} is thrown.  If the directory is relative, then it is interpreted relative to the current
     * working directory of the {@code client}.
     *
     * @param client    an FTP client that is connected and logged in
     * @param directory the directory that must exist
     * @throws RuntimeException if the supplied {@code directory} does not exist
     */
    public static void assertDirectoryExists(FTPClient client, String directory) {
        if (client == null) {
            throw new IllegalArgumentException("FTPClient must not be null.");
        }

        if (directory == null || directory.trim().length() == 0) {
            throw new IllegalArgumentException("Directory must not be null or an empty string.");
        }

        if (directory.equals("/")) {
            LOG.trace("Supplied directory '{}' was the root of the directory hierarchy, returning 'true'",
                      directory);
            return;
        }

        String originalCwd = null;
        try {
            originalCwd = client.printWorkingDirectory();
        } catch (IOException e) {
            throw new RuntimeException("Unable to obtain the current working directory: " + e.getMessage(), e);
        }

        try {
            if (!client.changeWorkingDirectory(directory)) {
                throw new RuntimeException("Directory '" + directory + "' does not exist!");
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to change working directory to '" + directory + "': " +
                                       e.getMessage(), e);
        } finally {
            try {
                client.changeWorkingDirectory(originalCwd);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Uses the supplied client to verify the supplied directory exists.  If the directory is relative, then it is
     * interpreted relative to the current working directory of the {@code client}.
     *
     * @param client    an FTP client that is connected and logged in
     * @param directory the directory that may exist
     * @return true if the directory exists, {@code false} otherwise
     */
    public static boolean directoryExists(FTPClient client, String directory) {
        try {
            assertDirectoryExists(client, directory);
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
