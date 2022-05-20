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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.MessageDigestCalculatingInputStream;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.Before;
import org.junit.Test;

/**
 * Insures the FTP docker container is up and running and configured with the proper user credentials. Includes
 * additional tests that verify the use of the {@link FTPClient Apache FTP client}.
 */
public class FtpSmokeIT extends FtpBaseIT {


    /**
     * Insure the docker container started and that an ftp client can connect with the expected username
     * and password.  Set the base directory to a directory that is unique to the execution of this IT.
     *
     * @throws Exception
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        itUtil.setBaseDirectory(FtpBaseIT.FTP_SUBMISSION_BASE_DIRECTORY);
        itUtil.connect();
        itUtil.login();
    }

    /**
     * Insure we can create directories and change into them and reliably change back to the original current working
     * directory.
     *
     * @throws IOException
     */
    @Test
    public void testMakeDirectoryAndChangeDirectory() throws IOException {
        String cwd = ftpClient.printWorkingDirectory();

        assertTrue(ftpClient.makeDirectory("FtpSmokeIT-testMakeDirectoryAndChangeDirectory"));
        itUtil.assertPositiveReply();

        assertTrue(ftpClient.changeWorkingDirectory("FtpSmokeIT-testMakeDirectoryAndChangeDirectory"));
        itUtil.assertPositiveReply();

        assertTrue(ftpClient.changeWorkingDirectory(cwd));
        itUtil.assertPositiveReply();
    }

    @Test
    public void testMakeTheSameDirectoryTwice() throws Exception {
        assertTrue(ftpClient.makeDirectory("FtpSmokeIT-testMakeTheSameDirectoryTwice"));
        itUtil.assertPositiveReply();

        assertFalse(ftpClient.makeDirectory("FtpSmokeIT-testMakeTheSameDirectoryTwice"));
    }

    /**
     * Assert we can store a non-trivial sized file, and retrieve it again.
     *
     * @throws IOException
     */
    @Test
    public void testStoreFile() throws IOException, NoSuchAlgorithmException {
        assertTrue(ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE));
        assertTrue(ftpClient.setFileType(FTP.BINARY_FILE_TYPE));

        String destFile = "org.jpg";
        MessageDigestCalculatingInputStream content = new MessageDigestCalculatingInputStream(
            this.getClass().getResourceAsStream("/" + destFile));

//        TODO test quota exceeded
//        552: 552-0 files used (0%) - authorized: 1000 files
//        552-0 Kbytes used (0%) - authorized: 10240 Kb
//        552 Quota exceeded: [org.jpg] won't be saved

        ftpClient.setUseEPSVwithIPv4(true);
        ftpClient.enterLocalPassiveMode();
        boolean success = ftpClient.storeFile(destFile, content);
        itUtil.assertPositiveReply();
        assertTrue(success);
        MessageDigest expectedDigest = content.getMessageDigest();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(32 * 2 ^ 10);
        ftpClient.enterLocalPassiveMode();
        success = ftpClient.retrieveFile(destFile, baos);
        itUtil.assertPositiveReply();
        assertTrue(success);

        content = new MessageDigestCalculatingInputStream(new ByteArrayInputStream(baos.toByteArray()));
        IOUtils.copy(content, new NullOutputStream());

        assertArrayEquals(expectedDigest.digest(), content.getMessageDigest().digest());
    }

    @Test
    public void testStoreSameFileTwice() throws Exception {
        assertTrue(ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE));
        assertTrue(ftpClient.setFileType(FTP.BINARY_FILE_TYPE));

        String destFile = "foo.bin";

        ftpClient.setUseEPSVwithIPv4(true);
        ftpClient.enterLocalPassiveMode();
        boolean success = ftpClient.storeFile(destFile, new NullInputStream(2 ^ 20));
        itUtil.assertPositiveReply();
        assertTrue(success);

        ftpClient.enterLocalPassiveMode();
        success = ftpClient.storeFile(destFile, new NullInputStream(2 ^ 20));
        itUtil.assertPositiveReply();
        assertTrue(success);
    }

}
