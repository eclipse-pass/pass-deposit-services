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

package org.dataconservancy.nihms.integration;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import com.google.common.net.InetAddresses;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPReply;
import org.dataconservancy.pass.deposit.transport.ftp.FtpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntegrationUtil {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationUtil.class);

    private String ftpHost;

    private int ftpPort;

    /**
     * This is the directory, if not null, that will be set as the current working directory on the FTP server after
     * a successful {@link #login()}.
     */
    private String baseDirectory;

    private FTPClient ftpClient;

    public IntegrationUtil(String ftpHost, int ftpPort, FTPClient ftpClient) {
        this.ftpHost = ftpHost;
        this.ftpPort = ftpPort;
        this.ftpClient = ftpClient;
    }

    public void assertPositiveReply() {
        int replyCode = ftpClient.getReplyCode();
        String replyString = ftpClient.getReplyString();
        assertTrue("(" + ftpClient() + ") Command failed: " + replyCode + " " + replyString,
                   FTPReply.isPositiveCompletion(replyCode));
    }

    public void connect() throws IOException {
        LOG.debug("({}) Connecting to {}:{} ...", ftpClient(), ftpHost, ftpPort);

        long waitMs = 2000;
        long start = System.currentTimeMillis();
        boolean connectionSuccess = false;

        do {
            try {
                LOG.debug("({}) Attempting connection to {}:{} ...", ftpClient(), ftpHost, ftpPort);
                if (InetAddresses.isInetAddress(ftpHost)) {
                    ftpClient.connect(InetAddresses.forString(ftpHost), ftpPort);
                } else {
                    ftpClient.connect(ftpHost, ftpPort);
                }
                if (!ftpClient.sendNoOp()) {
                    LOG.debug("({}) NOOP *FAILED*, connection to {}:{} not established.", ftpClient(), ftpHost,
                              ftpPort);
                    connectionSuccess = false;
                } else {
                    connectionSuccess = true;
                }
            } catch (FTPConnectionClosedException e) {
                // is there a bug in the Apache FTP library which attempts to re-use a socket that has been closed?
                // retry until a timeout is reached or a connection is successful.
                try {
                    LOG.debug("({}) Connection *FAILED* to {}:{}, sleeping for {} ms ...",
                              ftpClient(), ftpHost, ftpPort, waitMs);
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
        } while (!connectionSuccess && System.currentTimeMillis() - start < 30000);

        assertPositiveReply();
        LOG.debug("({}) Successfully connected to {}:{}", ftpClient(), ftpHost, ftpPort);
    }

    public void disconnect() throws IOException {
        if (ftpClient == null) {
            LOG.debug("({}) Not disconnecting because FTP client is null.", ftpClient());
        }

        if (!ftpClient.isConnected()) {
            LOG.debug("({}) Not disconnecting because the FTP client isn't connected.", ftpClient());
        }

        if (ftpClient != null && ftpClient.isConnected()) {
            ftpClient.logout();
            ftpClient.disconnect();
        }
    }

    public void login() throws IOException {
        assertTrue(ftpClient.login("nihmsftpuser", "nihmsftppass"));

        assertPositiveReply();

        if (baseDirectory != null && !FtpUtil.directoryExists(ftpClient, baseDirectory)) {
            assertTrue("Unable to create base directory '" + baseDirectory + "'",
                       ftpClient.makeDirectory(baseDirectory));
            assertTrue("Unable to set working directory to '" + baseDirectory + "'",
                       ftpClient.changeWorkingDirectory(baseDirectory));
            LOG.trace("Setting working directory to '{}'", ftpClient.printWorkingDirectory());
        } else if (baseDirectory != null) {
            assertTrue("Unable to set working directory to '" + baseDirectory + "'",
                       ftpClient.changeWorkingDirectory(baseDirectory));
            LOG.trace("Setting working directory to '{}'", ftpClient.printWorkingDirectory());
        } else {
            baseDirectory = ftpClient.printWorkingDirectory();
        }

        LOG.debug("Working directory is '{}'", ftpClient.printWorkingDirectory());
    }

    public void logout() throws IOException {
        assertTrue(ftpClient.logout());
        assertPositiveReply();
    }

    private String ftpClient() {
        if (ftpClient == null) {
            return "null";
        } else {
            return ftpClient.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(ftpClient));
        }
    }

    /**
     * This is the directory, if not null, that will be set as the current working directory on the FTP server after
     * a successful {@link #login()}.
     *
     * @return the base directory, may be {@code null}
     */
    public String getBaseDirectory() {
        return baseDirectory;
    }

    /**
     * This is the directory that will be set as the current working directory on the FTP server after a successful
     * {@link #login()}.
     *
     * @param baseDirectory the base directory, which must be absolute, and not null.
     */
    public void setBaseDirectory(String baseDirectory) {
        if (baseDirectory == null || baseDirectory.trim().length() == 0) {
            throw new IllegalArgumentException("Base directory must not be null or the empty string.");
        }

        if (!baseDirectory.startsWith("/")) {
            throw new IllegalArgumentException("Base directory must begin with a forward slash (was: '" +
                                               baseDirectory + "'");
        }
        this.baseDirectory = baseDirectory;
    }

}
