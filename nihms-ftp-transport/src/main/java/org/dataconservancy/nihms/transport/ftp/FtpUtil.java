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

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.dataconservancy.nihms.util.function.ExceptionThrowingCommand;
import org.dataconservancy.nihms.util.function.ExceptionThrowingFunction;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;

class FtpUtil {

    private static final String ERR_REPLY = "Reply from the FTP server was '%s' (code: '%s')";

    private static final String ERR_CMD = "Exception performing FTP command; error message: %s";

    /**
     * Character used to separate path components: e.g. use of the forward slash in the path '/path/to/file.txt'
     * separates three components: 'path', 'to', and 'file.txt'
     */
    static final String PATH_SEP = "/";

    private static final Function<FTPClient, Boolean> ACCEPT_POSITIVE_COMPLETION = (ftpClient) ->
            FTPReply.isPositiveCompletion(ftpClient.getReplyCode());

    private static final Function<FTPClient, Boolean> ACCEPT_MKD_COMPLETION = (ftpClient) ->
            ACCEPT_POSITIVE_COMPLETION.apply(ftpClient) ||
                    acceptResponseCodes(550, 553).apply(ftpClient.getReplyCode());

    private static final Consumer<FTPClient> ASSERT_POSITIVE_COMPLETION = (ftpClient) -> {
        if (!ACCEPT_POSITIVE_COMPLETION.apply(ftpClient)) {
            throw new RuntimeException(String.format(ERR_REPLY, ftpClient.getReplyString(), ftpClient.getReplyCode()));
        }
    };

    private static final Consumer<FTPClient> ASSERT_MKD_COMPLETION = (ftpClient) -> {
        if (!ACCEPT_MKD_COMPLETION.apply(ftpClient)) {
            throw new RuntimeException(String.format(ERR_REPLY, ftpClient.getReplyString(), ftpClient.getReplyCode()));
        }
    };

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
            performSilently(ftpClient, ftpClient::enterRemotePassiveMode);
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

}
