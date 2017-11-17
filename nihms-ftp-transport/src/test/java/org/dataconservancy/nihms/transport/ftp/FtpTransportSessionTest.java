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

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.dataconservancy.nihms.transport.TransportResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class FtpTransportSessionTest {

    private static long ONE_MIB = 2 ^ 20;

    private FTPClient ftpClient;

    private FtpTransportSession ftpSession;

    /**
     * Configure the FtpTransportSession under test with a mock FTPClient instance.
     */
    @Before
    public void setUp() {
        ftpClient = mock(FTPClient.class);
        ftpSession = new FtpTransportSession(ftpClient);
    }

    /**
     * Attempt to store a single file using a destination resource that names just the file.  Verify that the
     * FtpTransportSession invokes FTPClient.storeFile(...).   Because the underlying FTPClient is mocked, the stream
     * will not actually be read.
     *
     * @throws IOException
     */
    @Test
    public void testStoreFile() throws IOException {
        // Resource is a single file, expected to be placed in the current working directory of the ftp server.
        String destinationResource = "package.tar.gz";
        NullInputStream content = new NullInputStream(ONE_MIB);

        when(ftpClient.printWorkingDirectory()).thenReturn("/");
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        TransportResponse response = ftpSession.storeFile(destinationResource, content);

        assertNotNull(response);
        assertTrue(response.success());
        assertNull(response.error());
        verify(ftpClient).storeFile(destinationResource, content);
    }

    /**
     * Attempt to store a single file using a destination resource that names a file in a subdirectory.  Verify that the
     * subdirectory is changed to prior to storing the file.  Verify that after streaming the file the original working
     * directory is changed back to.  Verify that the FtpTransportSession invokes FTPClient.storeFile(...).   Because
     * the underlying FTPClient is mocked, the stream will not actually be read.
     *
     * @throws IOException
     */
    @Test
    public void testStoreFileNestedDirectory() throws Exception {
        String destinationResource = "sub/directory/package.tar.gz";
        NullInputStream content = new NullInputStream(ONE_MIB);

        when(ftpClient.printWorkingDirectory()).thenReturn("/");
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        TransportResponse response = ftpSession.storeFile(destinationResource, content);

        assertNotNull(response);
        assertTrue(response.success());
        assertNull(response.error());
        verify(ftpClient).changeWorkingDirectory("sub/directory");
        verify(ftpClient).storeFile("package.tar.gz", content);
        verify(ftpClient).changeWorkingDirectory("/");
    }

    /**
     * Attempt to store a file, and recover from a IOException thrown when storing the file.  Insure that response
     * carries the originally thrown exception.  Insure that the current working directory is reset despite the
     * exception.
     *
     * @throws IOException
     */
    @Test
    public void testStoreWithIOException() throws IOException {
        String destinationResource = "sub/directory/package.tar.gz";
        String expectedMessage = "Broken stream.";
        IOException expectedException = new IOException(expectedMessage);
        NullInputStream content = new NullInputStream(ONE_MIB);

        when(ftpClient.printWorkingDirectory()).thenReturn("/");
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);
        when(ftpClient.getReplyCode())
                .thenReturn(FTPReply.COMMAND_OK)
                .thenReturn(FTPReply.COMMAND_OK)
                .thenReturn(500);
        when(ftpClient.getReplyString()).thenReturn("Transfer failed");

        when(ftpClient.storeFile(anyString(), any(InputStream.class))).thenThrow(expectedException);

        TransportResponse response = ftpSession.storeFile(destinationResource, content);

        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
        assertEquals(expectedException, response.error().getCause().getCause());
        assertEquals(expectedMessage, response.error().getCause().getCause().getMessage());
        verify(ftpClient).changeWorkingDirectory("sub/directory");
        verify(ftpClient).storeFile("package.tar.gz", content);
        verify(ftpClient).changeWorkingDirectory("/");
    }
}