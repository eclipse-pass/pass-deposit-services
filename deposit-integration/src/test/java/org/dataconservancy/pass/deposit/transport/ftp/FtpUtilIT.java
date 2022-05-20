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

import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.directoryExists;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.stream.Stream;

import org.apache.commons.net.ftp.FTPClient;
import org.dataconservancy.nihms.integration.FtpBaseIT;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FtpUtilIT extends FtpBaseIT {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        FtpUtil.connect(ftpClient, ftpHost, ftpPort);
        FtpUtil.login(ftpClient, FTP_INTEGRATION_USERNAME, FTP_INTEGRATION_PASSWORD);

        if (!directoryExists(ftpClient, FTP_SUBMISSION_BASE_DIRECTORY)) {
            assertTrue("Unable to create base directory '" + FTP_SUBMISSION_BASE_DIRECTORY + "'",
                       ftpClient.makeDirectory(FTP_SUBMISSION_BASE_DIRECTORY));
            LOG.trace("Created directory: '{}'", FTP_SUBMISSION_BASE_DIRECTORY);
        }

        assertTrue("Unable to set working directory '" + FTP_SUBMISSION_BASE_DIRECTORY + "'",
                   ftpClient.changeWorkingDirectory(FTP_SUBMISSION_BASE_DIRECTORY));

        assertEquals(FTP_SUBMISSION_BASE_DIRECTORY, ftpClient.printWorkingDirectory());
        LOG.trace(">>> Current working directory: '{}'", ftpClient.printWorkingDirectory());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        FtpUtil.disconnect(ftpClient);
    }

    @Test
    public void testMakeSameDirectoryTwice() throws Exception {
        FtpUtil.performSilently(() -> FtpUtil.makeDirectories(ftpClient, "FtpUtilIT-testMakeSameDirectoryTwice"));

        FtpUtil.performSilently(ftpClient, (client) -> {
            FtpUtil.makeDirectories(client, "FtpUtilIT-testMakeSameDirectoryTwice");
            return true;

        }, FtpUtil.ASSERT_MKD_COMPLETION);
    }

    @Test
    public void testSetPasv() throws Exception {
        FtpUtil.setPasv(ftpClient, true);
        FtpUtil.setPasv(ftpClient, false);
    }

    @Test
    public void testSetDataType() throws Exception {
        FtpUtil.setDataType(ftpClient, FtpTransportHints.TYPE.binary.name());
        FtpUtil.setDataType(ftpClient, FtpTransportHints.TYPE.ascii.name());
    }

    @Test
    public void testSetTransferMode() throws Exception {
        FtpUtil.setTransferMode(ftpClient, FtpTransportHints.MODE.stream.name());

        try {
            FtpUtil.setTransferMode(ftpClient, FtpTransportHints.MODE.compressed.name());
            fail(
                "Expected RuntimeException, because the integration FTP server does not support compressed or block " +
                "mode.");
        } catch (Exception e) {
            // expected
        }

        try {
            FtpUtil.setTransferMode(ftpClient, FtpTransportHints.MODE.block.name());
            fail(
                "Expected RuntimeException, because the integration FTP server does not support compressed or block " +
                "mode.");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testSetWorkingDirectory() throws Exception {
        String directory = "FtpUtilIT-testSetWorkingDirectory";
        assertTrue(ftpClient.enterRemotePassiveMode());
        assertEquals(0, ftpClient.listFiles(directory).length);

        FtpUtil.setWorkingDirectory(ftpClient, directory);

        assertEquals(String.format("%s/%s", FTP_SUBMISSION_BASE_DIRECTORY, directory),
                     ftpClient.printWorkingDirectory());
        assertTrue(ftpClient.changeToParentDirectory());
        ftpClient.setUseEPSVwithIPv4(true);
        ftpClient.enterLocalPassiveMode();
        assertTrue(Stream.of(ftpClient.listFiles())
                         .peek(ftpFile -> LOG.debug("{}", ftpFile.getName()))
                         .anyMatch(ftpFile -> ftpFile.getName().equals(directory)));
    }

    /**
     * Test that insures {@link FtpUtil#makeDirectories(FTPClient, String)} works when the {@code directory} to be made
     * includes intermediate directories that do not exist, and when the {@code directory} is relative to the current
     * working directory.
     *
     * @throws Exception
     */
    @Test
    public void testMakeIntermediateDirectoriesWithRelativePaths() throws Exception {
        String cwd = ftpClient.printWorkingDirectory();
        String intermediateDirectory = "FtpUtilIT-testMakeIntermediateDirectoriesWithRelativePaths";
        String absoluteIntermediateDirectory = String.format("%s/%s", cwd, intermediateDirectory);
        String directory = String.format("%s/%s", intermediateDirectory, "subDirectory");
        String absoluteDirectory = String.format("%s/%s", cwd, directory);

        // Insure the intermediate path doesn't exist relative to the current working directory
        assertFalse("Did not expect the intermediate directory '" + intermediateDirectory + "' to exist " +
                    "relative to the CWD '" + cwd + "'", directoryExists(ftpClient, intermediateDirectory));

        // Insure the intermediate path doesn't exist absolutely, either
        assertFalse("Did not expect the intermediate directory '" + absoluteIntermediateDirectory + "' to " +
                    "exist relative to the CWD '" + cwd + "'",
                    directoryExists(ftpClient, absoluteIntermediateDirectory));

        // Make the directory *relative to the current working directory*
        assertFalse("Expected directory '" + directory + "' to be relative.",
                    FtpUtil.isPathAbsolute(directory));
        FtpUtil.makeDirectories(ftpClient, directory);

        // Intermediate path ought to exist: relative to the cwd, and absolutely
        assertTrue("Expected intermediate directory '" + intermediateDirectory + "' to exist relative to " +
                   "the CWD '" + cwd + "'", directoryExists(ftpClient, intermediateDirectory));
        assertTrue("Expected intermediate directory '" + absoluteIntermediateDirectory + "' to exist",
                   directoryExists(ftpClient, absoluteIntermediateDirectory));

        // Insure the full path exists relative to the current working directory
        assertTrue("Expected directory '" + directory + "' to exist relative to the CWD '" + cwd + "'",
                   directoryExists(ftpClient, directory));

        // Insure the full path exists absolutely
        assertTrue("Expected directory '" + absoluteDirectory + "' to exist",
                   directoryExists(ftpClient, absoluteDirectory));
    }

    /**
     * Test that insures {@link FtpUtil#makeDirectories(FTPClient, String)} works when the {@code directory} to be made
     * includes intermediate directories that do not exist, and when the {@code directory} is absolute to the current
     * working directory.
     *
     * @throws Exception
     */
    @Test
    public void testMakeIntermediateDirectoriesWithAbsolutePaths() throws Exception {
        String cwd = ftpClient.printWorkingDirectory();
        String intermediateDirectory = "FtpUtilIT-testMakeIntermediateDirectoriesWithAbsolutePaths";
        String absoluteIntermediateDirectory = String.format("%s/%s", cwd, intermediateDirectory);
        String directory = String.format("%s/%s", intermediateDirectory, "subDirectory");
        String absoluteDirectory = String.format("%s/%s", cwd, directory);

        // Insure the intermediate path doesn't exist relative to the current working directory
        assertFalse("Did not expect the intermediate directory '" + intermediateDirectory + "' to exist " +
                    "relative to the CWD '" + cwd + "'", directoryExists(ftpClient, intermediateDirectory));

        // Insure the intermediate path doesn't exist absolutely, either
        assertFalse("Did not expect the intermediate directory '" + absoluteIntermediateDirectory + "' to " +
                    "exist relative to the CWD '" + cwd + "'",
                    directoryExists(ftpClient, absoluteIntermediateDirectory));

        // Make the directory *absolutely*, regardless of the current working directory*
        assertTrue("Expected directory '" + absoluteDirectory + "' to be absolute.",
                   FtpUtil.isPathAbsolute(absoluteDirectory));
        FtpUtil.makeDirectories(ftpClient, absoluteDirectory);

        // Intermediate path ought to exist: relative to the cwd, and absolutely
        assertTrue("Expected intermediate directory '" + intermediateDirectory + "' to exist relative to " +
                   "the CWD '" + cwd + "'", directoryExists(ftpClient, intermediateDirectory));
        assertTrue("Expected intermediate directory '" + absoluteIntermediateDirectory + "' to exist",
                   directoryExists(ftpClient, absoluteIntermediateDirectory));

        // Insure the full path exists relative to the current working directory
        assertTrue("Expected directory '" + directory + "' to exist relative to the CWD '" + cwd + "'",
                   directoryExists(ftpClient, directory));

        // Insure the full path exists absolutely
        assertTrue("Expected directory '" + absoluteDirectory + "' to exist",
                   directoryExists(ftpClient, absoluteDirectory));

    }

    @Test
    public void testMakeAbsoluteDestinationResourceWithDirectoryAndFilename() throws Exception {
        String intermediateDirectory = String.format("%s/%s", FTP_SUBMISSION_BASE_DIRECTORY,
                                                     "testMakeAbsoluteDestinationResourceWithDirectoryAndFilename");
        String destinationResource = String.format("%s/%s", intermediateDirectory, "foo/picture.jpg");

        // Insure the intermediate path doesn't exist
        assertFalse("Did not expect the intermediate directory '" + intermediateDirectory + "' to exist.",
                    directoryExists(ftpClient, intermediateDirectory));

        // Make the directory *absolutely*, regardless of the current working directory*
        FtpUtil.makeDirectories(ftpClient, destinationResource);

        // Intermediate path ought to exist
        assertTrue("Expected intermediate directory '" + intermediateDirectory + "' to exist",
                   directoryExists(ftpClient, intermediateDirectory));

        // Insure the full path exists
        assertTrue("Expected directory: '" + destinationResource + "' to exist.",
                   directoryExists(ftpClient, destinationResource));
    }
}
