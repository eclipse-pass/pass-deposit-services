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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.io.IOException;

import static org.dataconservancy.nihms.transport.ftp.FtpTestUtil.FTP_ROOT_DIR;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.MODE.block;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.MODE.compressed;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.MODE.stream;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.TYPE.ascii;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.TYPE.binary;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.PATH_SEP;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class FtpUtilTest {

    private FTPClient ftpClient;

    @Before
    public void setUp() throws Exception {
        ftpClient = mock(FTPClient.class);
    }

    /**
     * Invoking {@link FtpUtil#setPasv(FTPClient, boolean)} with a {@code true} value should result in the client
     * entering passive mode.
     *
     * @throws IOException
     */
    @Test
    public void setPasvSuccess() throws IOException {
        FtpUtil.setPasv(ftpClient, true);
        verify(ftpClient).enterLocalPassiveMode();
    }

    /**
     * Invoking {@link FtpUtil#setPasv(FTPClient, boolean)} with a {@code false} value results in the client entering
     * active mode
     *
     * @throws IOException
     */
    @Test
    public void setPasvFalse() throws IOException {
        FtpUtil.setPasv(ftpClient, false);
        verify(ftpClient).enterLocalActiveMode();
    }

    @Test(expected = RuntimeException.class)
    public void setPasvFail() throws Exception {
        doThrow(new RuntimeException("Unable to enter local passive mode")).when(ftpClient).enterLocalPassiveMode();

        FtpUtil.setPasv(ftpClient, true);
    }

    /**
     * Insure that invoking FtpUtil.setDataType(ftpClient, binary.name()) results in
     * FTPClient.type(FTP.BINARY_FILE_TYPE) being invoked.
     *
     * @throws IOException
     */
    @Test
    public void setDataTypeBinarySuccess() throws IOException {
        when(ftpClient.type(anyInt())).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        FtpUtil.setDataType(ftpClient, binary.name());

        verify(ftpClient).type(eq(FTP.BINARY_FILE_TYPE));
        verify(ftpClient).getReplyCode();
    }

    /**
     * Insure that an exception is thrown when setting the data type is denied.
     *
     * @throws IOException
     */
    @Test(expected = RuntimeException.class)
    public void setDataTypeBinaryFail() throws IOException {
        when(ftpClient.type(anyInt())).thenReturn(FTPReply.REQUEST_DENIED);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.REQUEST_DENIED);

        FtpUtil.setDataType(ftpClient, binary.name());
    }

    /**
     * Insure that invoking FtpUtil.setDataType(ftpClient, ascii.name()) results in
     * FTPClient.type(FTP.ASCII_FILE_TYPE) being invoked.
     *
     * @throws IOException
     */
    @Test
    public void setDataTypeAsciiSuccess() throws IOException {
        when(ftpClient.type(anyInt())).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        FtpUtil.setDataType(ftpClient, ascii.name());

        verify(ftpClient).type(eq(FTP.ASCII_FILE_TYPE));
        verify(ftpClient).getReplyCode();
    }

    /**
     * Insure that an exception is thrown when setting the data type to a unknown type
     *
     * @throws IOException
     */
    @Test(expected = RuntimeException.class)
    public void setDataTypeUnknown() throws IOException {
        when(ftpClient.type(anyInt())).thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        FtpUtil.setDataType(ftpClient, "foo");
    }

    /**
     * Insure that invoking FtpUtil.setTransferMode(ftpClient, stream.name()) results in
     * FTPClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE) being invoked.
     *
     * @throws IOException
     */
    @Test
    public void setTransferModeStreamSuccess() throws IOException {
        when(ftpClient.setFileTransferMode(anyInt())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        FtpUtil.setTransferMode(ftpClient, stream.name());

        verify(ftpClient).setFileTransferMode(eq(FTP.STREAM_TRANSFER_MODE));
        verify(ftpClient).getReplyCode();
    }

    /**
     * Insure that an exception is thrown when the server denies setting a file transfer mode.
     *
     * @throws IOException
     */
    @Test(expected = RuntimeException.class)
    public void setTransferModeStreamFail() throws IOException {
        when(ftpClient.setFileTransferMode(anyInt())).thenReturn(false);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.REQUEST_DENIED);

        FtpUtil.setTransferMode(ftpClient, stream.name());
    }

    /**
     * Insure that an exception is thrown when the client doesn't support the specified file transfer mode.
     *
     * @throws IOException
     */
    @Test(expected = RuntimeException.class)
    public void setTransferModeUnknown() throws IOException {
        when(ftpClient.setFileTransferMode(anyInt())).thenReturn(false);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.REQUEST_DENIED);

        FtpUtil.setTransferMode(ftpClient, "foo");
    }

    /**
     * Insure that invoking FtpUtil.setTransferMode(ftpClient, block.name()) results in
     * FTPClient.setFileTransferMode(FTP.BLOCK_TRANSFER_MODE) being invoked.
     *
     * @throws IOException
     */
    @Test
    public void setTransferModeBlockSuccess() throws IOException {
        when(ftpClient.setFileTransferMode(anyInt())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        FtpUtil.setTransferMode(ftpClient, block.name());

        verify(ftpClient).setFileTransferMode(eq(FTP.BLOCK_TRANSFER_MODE));
        verify(ftpClient).getReplyCode();
    }

    /**
     * Insure that invoking FtpUtil.setTransferMode(ftpClient, compressed.name()) results in
     * FTPClient.setFileTransferMode(FTP.COMPRESSED_TRANSFER_MODE) being invoked.
     *
     * @throws IOException
     */
    @Test
    public void setTransferModeCompressSuccess() throws Exception {
        when(ftpClient.setFileTransferMode(anyInt())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.COMMAND_OK);

        FtpUtil.setTransferMode(ftpClient, compressed.name());

        verify(ftpClient).setFileTransferMode(eq(FTP.COMPRESSED_TRANSFER_MODE));
        verify(ftpClient).getReplyCode();
    }

    /**
     * Insure a call to FtpUtil.login(ftpClient,"foo", "bar") results in FTPClient.login("foo", "bar") being invoked
     *
     * @throws IOException
     */
    @Test
    public void testLoginSuccess() throws IOException {
        when(ftpClient.login(anyString(), anyString())).thenReturn(true);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.USER_LOGGED_IN);

        FtpUtil.login(ftpClient, "foo", "bar");

        verify(ftpClient).login(eq("foo"), eq("bar"));
        verify(ftpClient).getReplyCode();
    }

    /**
     * Insure that when the server reports a login failure that an exception is thrown.
     *
     * @throws IOException
     */
    @Test(expected = RuntimeException.class)
    public void testLoginFailure() throws IOException {
        when(ftpClient.login(anyString(), anyString())).thenReturn(false);
        when(ftpClient.getReplyCode()).thenReturn(FTPReply.NOT_LOGGED_IN);

        FtpUtil.login(ftpClient, "foo", "bar");

        verify(ftpClient).login(eq("foo"), eq("bar"));
        verify(ftpClient).getReplyCode();
    }

    /**
     * Insure that when FtpUtil.makeDirectories(ftpClient, "dir") is invoked that:
     * - the current working directory is obtained
     * - the specified directory is created and reply code checked
     * - the client changes the current working directory to the newly created directory (in prep for potentially
     * creating a sub directory)
     * - changes the current working directory to the originally obtained working directory
     *
     * @throws IOException
     */
    @Test
    public void testMakeSingleDirectory() throws IOException {
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.makeDirectory(anyString())).thenReturn(true);
        when(ftpClient.getReplyCode())
                .thenReturn(FTPReply.PATHNAME_CREATED)
                .thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);

        FtpUtil.makeDirectories(ftpClient, "dir");

        verify(ftpClient).printWorkingDirectory();
        verify(ftpClient).makeDirectory(eq("dir"));
        verify(ftpClient).changeWorkingDirectory(eq("dir"));
        verify(ftpClient).changeWorkingDirectory(FTP_ROOT_DIR);
        verify(ftpClient, times(4)).getReplyCode();
    }

    /**
     * Insure that when FtpUtil.makeDirectories(ftpClient, "dir") is invoked that:
     * - the current working directory is obtained
     * - the specified directory is created and reply code checked
     * - the client changes the current working directory to the newly created directory (in prep for potentially
     * creating a sub directory)
     * - changes the current working directory to the originally obtained working directory
     *
     * @throws IOException
     */
    @Test
    public void testMakeSingleDirectoryThatAlreadyExists() throws IOException {
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.makeDirectory(anyString())).thenReturn(true);
        when(ftpClient.getReplyCode())
                .thenReturn(FTPReply.COMMAND_OK)
                .thenReturn(FTPReply.FILE_UNAVAILABLE)  // considered successful by FtpUtil.makeDirectories(...) because
                // the file may already exist on the remote system
                .thenReturn(FTPReply.FILE_UNAVAILABLE)  // Returned twice because the reply code is checked by two different callbacks
                .thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);

        FtpUtil.makeDirectories(ftpClient, "dir");

        verify(ftpClient).printWorkingDirectory();
        verify(ftpClient).makeDirectory(eq("dir"));
        verify(ftpClient).changeWorkingDirectory(eq("dir"));
        verify(ftpClient).changeWorkingDirectory(FTP_ROOT_DIR);
        verify(ftpClient, times(5)).getReplyCode();
    }

    /**
     * Insure that when FtpUtil.makeDirectories(ftpClient, "dir/subdir") is invoked that:
     * - the current working directory is obtained
     * - the specified directory is created and reply code checked
     * - the client changes the current working directory to the newly created directory (in prep for creating the sub
     * directory)
     * - the subdirectory is created, reply code checked, and changed into
     * - changes the current working directory to the originally obtained working directory
     *
     * @throws IOException
     */
    @Test
    public void testMakeNestedDirectory() throws IOException {
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.makeDirectory(anyString())).thenReturn(true);
        when(ftpClient.getReplyCode())
                .thenReturn(FTPReply.PATHNAME_CREATED)
                .thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);

        FtpUtil.makeDirectories(ftpClient, "dir" + PATH_SEP + "subdir");

        verify(ftpClient).makeDirectory(eq("dir"));
        verify(ftpClient).changeWorkingDirectory(eq("dir"));
        verify(ftpClient).makeDirectory(eq("subdir"));
        verify(ftpClient).changeWorkingDirectory(eq("subdir"));
        verify(ftpClient).changeWorkingDirectory(FTP_ROOT_DIR);
    }

    /**
     * Insure that when FtpUtil.makeDirectories(ftpClient, "/dir/subdir") is invoked that:
     * - the current working directory is obtained
     * - the specified directory is created and reply code checked
     * - the client changes the current working directory to the newly created directory (in prep for creating the sub
     * directory)
     * - the subdirectory is created, reply code checked, and changed into
     * - changes the current working directory to the originally obtained working directory
     *
     * Specifically that leading slashes in the directory name are handled properly when parsed
     * @throws IOException
     */
    @Test
    public void testMakeNestedDirectoryStartingWithPathSep() throws IOException {
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.makeDirectory(argThat(s -> s.length() > 0))).thenReturn(true);
        when(ftpClient.getReplyCode())
                .thenReturn(FTPReply.PATHNAME_CREATED)
                .thenReturn(FTPReply.COMMAND_OK);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);

        FtpUtil.makeDirectories(ftpClient, PATH_SEP + "dir" + PATH_SEP + "subdir");

        verify(ftpClient).makeDirectory(eq("dir"));
        verify(ftpClient).changeWorkingDirectory(eq("dir"));
        verify(ftpClient).makeDirectory(eq("subdir"));
        verify(ftpClient).changeWorkingDirectory(eq("subdir"));
        verify(ftpClient).changeWorkingDirectory(FTP_ROOT_DIR);
        verify(ftpClient, never()).makeDirectory(eq(""));
        verify(ftpClient, never()).makeDirectory(eq(PATH_SEP));
    }

    /**
     * Even if an exception is thrown when making a directory, the current working directory should be reset to whatever
     * it was when the mkd command was invoked.
     *
     * @throws Exception
     */
    @Test
    public void testMakeDirectoryFails() throws Exception {
        when(ftpClient.printWorkingDirectory()).thenReturn(FTP_ROOT_DIR);
        when(ftpClient.getReplyCode())
                .thenReturn(FTPReply.COMMAND_OK)
                .thenReturn(FTPReply.NOT_LOGGED_IN);
        when(ftpClient.makeDirectory(anyString())).thenReturn(true);
        when(ftpClient.changeWorkingDirectory(anyString())).thenReturn(true);

        try {
            FtpUtil.makeDirectories(ftpClient, "dir");
            fail("Expected exception to be thrown");
        } catch (Exception e) {
            // expected
        }

        verify(ftpClient).makeDirectory(eq("dir"));
        verify(ftpClient).changeWorkingDirectory(FTP_ROOT_DIR);
    }
}