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

import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_AUTHMODE;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_PORT;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_USERNAME;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpTestUtil.FTP_ROOT_DIR;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class FtpTransportTest {

    private FtpClientFactory ftpClientFactory;

    private FtpTransport transport;

    private FTPClient ftpClient;

    /**
     * Hints are simple key-value pairs used to configure, or influence the behavior of, the underlying FTPClient.
     */
    private Map<String, String> expectedHints = new HashMap<String, String>() {
        {
            put(TRANSPORT_PROTOCOL, Transport.PROTOCOL.ftp.name());
            put(TRANSPORT_AUTHMODE, Transport.AUTHMODE.userpass.name());
            put(TRANSPORT_USERNAME, "nihmsftpuser");
            put(TRANSPORT_PASSWORD, "nihmsftppass");
            put(TRANSPORT_SERVER_FQDN, "example.ftp.submission.nih.org");
            put(TRANSPORT_SERVER_PORT, "21");
            // For simplicity of testing, stay in the base directory (no need to create directories upon open)
            put(FtpTransportHints.BASE_DIRECTORY, FTP_ROOT_DIR);
            put(FtpTransportHints.TRANSFER_MODE, FtpTransportHints.MODE.stream.name());
            put(FtpTransportHints.USE_PASV, Boolean.TRUE.toString());
            put(FtpTransportHints.DATA_TYPE, FtpTransportHints.TYPE.binary.name());
        }
    };

    /**
     * Set up a mock FtpClientFactory to supply a mock FTPClient to the FtpTransport class under test.
     */
    @Before
    public void setUp() {
        ftpClientFactory = mock(FtpClientFactory.class);
        ftpClient = mock(FTPClient.class);
        when(ftpClientFactory.newInstance(anyMap())).thenReturn(ftpClient);

        transport = new FtpTransport(ftpClientFactory);
    }

    /**
     * Open a session successfully.  User login succeeds, the file transfer mode is set, and the working directory
     * is changed to.
     *
     * @throws IOException
     */
    @Test
    public void testOpenSuccess() throws IOException {
        when(ftpClient.login(anyString(), anyString())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.USER_LOGGED_IN);
        when(ftpClient.sendNoOp()).thenReturn(true);
        when(ftpClient.setFileTransferMode(anyInt())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.changeWorkingDirectory(FTP_ROOT_DIR)).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        TransportSession session = transport.open(expectedHints);

        assertNotNull(session);
        verify(ftpClient).login("nihmsftpuser", "nihmsftppass");
        verify(ftpClient).setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        verify(ftpClient).changeWorkingDirectory(FTP_ROOT_DIR);
    }

    /**
     * A runtime exception is thrown when the login fails.
     *
     * @throws IOException
     */
    @Test
    public void testLoginFailure() throws IOException {
        when(ftpClient.sendNoOp()).thenReturn(true);
        when(ftpClient.login(anyString(), anyString())).thenReturn(false);
        when(ftpClient.getReplyCode())
            .thenReturn(200)
            .thenReturn(530);

        when(ftpClient.getReplyString())
            .thenReturn("OK")
            .thenReturn("Login authentication failed");

        try {
            transport.open(expectedHints);
            fail("Expected RuntimeException to be thrown.");
        } catch (RuntimeException e) {
            // expected
        }

        verify(ftpClient).sendNoOp();
        verify(ftpClient, atLeastOnce()).getReplyCode();
        verify(ftpClient, atLeastOnce()).getReplyString();
    }
}