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

import org.dataconservancy.nihms.integration.BaseIT;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FtpUtilIT extends BaseIT {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        FtpUtil.connect(ftpClient, ftpHost, ftpPort);
        FtpUtil.login(ftpClient, FTP_INTEGRATION_USERNAME, FTP_INTEGRATION_PASSWORD);
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
            fail("Expected RuntimeException, because the integration FTP server does not support compressed or block mode.");
        } catch (Exception e) {
            // expected
        }

        try {
            FtpUtil.setTransferMode(ftpClient, FtpTransportHints.MODE.block.name());
            fail("Expected RuntimeException, because the integration FTP server does not support compressed or block mode.");
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

        assertEquals("/" + directory, ftpClient.printWorkingDirectory());
        assertTrue(ftpClient.changeToParentDirectory());
        ftpClient.setUseEPSVwithIPv4(true);
        ftpClient.enterLocalPassiveMode();
        assertTrue(Stream.of(ftpClient.listFiles())
                .peek(ftpFile -> LOG.debug("{}", ftpFile.getName()))
                .anyMatch(ftpFile -> ftpFile.getName().equals(directory)));
    }
}
