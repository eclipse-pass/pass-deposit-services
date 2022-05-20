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

import static org.dataconservancy.pass.deposit.transport.ftp.FtpTestUtil.FTP_ROOT_DIR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.junit.Before;
import org.junit.Test;

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

        when(ftpClient.storeFile(any(), eq(content))).thenReturn(true);
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.setFileType(FTP.BINARY_FILE_TYPE)).thenReturn(true);

        TransportResponse response = ftpSession.storeFile(destinationResource, content);

        assertNotNull(response);
        assertTrue(response.success());
        assertNull(response.error());
        verifyDestinationResource(destinationResource, content);
        verify(ftpClient).storeFile(any(), eq(content));
        verify(ftpClient).setFileType(FTP.BINARY_FILE_TYPE);
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

        when(ftpClient.storeFile(any(), eq(content))).thenReturn(true);
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.setFileType(FTP.BINARY_FILE_TYPE)).thenReturn(true);

        TransportResponse response = ftpSession.storeFile(destinationResource, content);

        assertNotNull(response);
        assertTrue(response.success());
        assertNull(response.error());
        verify(ftpClient).changeWorkingDirectory("sub/directory");
        verifyDestinationResource("package.tar.gz", content);
        verify(ftpClient, atLeastOnce()).changeWorkingDirectory(FTP_ROOT_DIR);
        verify(ftpClient).storeFile(any(), eq(content));
        verify(ftpClient).setFileType(FTP.BINARY_FILE_TYPE);
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

        AtomicBoolean storeFileInvoked = new AtomicBoolean(false);
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);
        when(ftpClient.setFileType(FTP.BINARY_FILE_TYPE)).thenReturn(true);
        when(ftpClient.storeFile(anyString(), any(InputStream.class))).thenAnswer(inv -> {
            storeFileInvoked.set(true);
            throw expectedException;
        });
        when(ftpClient.getReplyString()).thenAnswer((invocationOnMock) -> {
            if (invocationOnMock.getMethod().getName().equals("storeFile")) {
                return "OK";
            }

            return "Transfer failed.";
        });
        when(ftpClient.getReplyCode()).thenAnswer((invocationOnMock) -> {
            if (storeFileInvoked.get()) {
                return 500;
            }
            return 200;
        });

        TransportResponse response = ftpSession.storeFile(destinationResource, content);

        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
        assertEquals(expectedException, response.error().getCause());
        assertEquals(expectedMessage, response.error().getCause().getMessage());
        verify(ftpClient).changeWorkingDirectory("sub/directory");
        verifyDestinationResource("package.tar.gz", content);
        verify(ftpClient, times(2)).changeWorkingDirectory(FTP_ROOT_DIR);
        verify(ftpClient).setFileType(FTP.BINARY_FILE_TYPE);
    }

    /**
     * Insure that an ABORT command is sent to the FTP server when an exception is thrown storing the file.
     *
     * @throws Exception
     */
    @Test
    public void testAbortWhenStoringFileThrowingException() throws Exception {
        String destinationResource = "package.tar.gz";
        String expectedMessage = "Broken stream.";
        IOException expectedException = new IOException(expectedMessage);

        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.setFileType(FTP.BINARY_FILE_TYPE)).thenReturn(true);
        when(ftpClient.storeFile(any(), any())).thenThrow(expectedException);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK); // assume all commands return OK

        InputStream content = mock(InputStream.class);
        TransportResponse response = ftpSession.storeFile(destinationResource, content);

        verifyDestinationResource(destinationResource, content);
        verify(ftpClient).abort();
        assertNotNull(response);
        assertFalse(response.success());
        assertEquals(expectedException, response.error().getCause());
        verify(ftpClient).setFileType(FTP.BINARY_FILE_TYPE);
    }

    private void verifyDestinationResource(String destinationResource) throws IOException {
        verifyDestinationResource(destinationResource, any(InputStream.class));
    }

    private void verifyDestinationResource(String destinationResource, InputStream content) throws IOException {
        String prefix = (destinationResource.contains(".")) ? destinationResource.substring(0,
                                                                                            destinationResource.indexOf(
                                                                                                ".")) :
                        destinationResource;
        String suffix = (destinationResource.contains(".")) ? destinationResource.substring(
            destinationResource.indexOf(".")) : "";

        assertTrue("Must have a filename prefix!", prefix.length() > 0); //we are requiring both a prefix
        assertTrue("Must have a filename suffix!", suffix.length() > 0); //and a suffix

        verify(ftpClient).storeFile(argThat((candidateResource) ->
                                                candidateResource.startsWith(prefix) && candidateResource.endsWith(
                                                    suffix)), eq(content));
    }

}