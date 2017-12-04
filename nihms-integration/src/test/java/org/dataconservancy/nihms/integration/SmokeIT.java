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

import com.google.common.net.InetAddresses;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SmokeIT {

    private static final String DOCKER_HOST_PROPERTY = "docker.host.address";

    private String ftpHost;

    private int ftpPort = 21;

    private FTPClient ftpClient;

    @Before
    public void setUp() throws Exception {
        ftpHost = System.getProperty(DOCKER_HOST_PROPERTY);
        assertNotNull("Missing value for system property '" + DOCKER_HOST_PROPERTY + "'", ftpHost);

        ftpClient = new FTPClient();
    }

    @After
    public void tearDown() throws Exception {
        if (ftpClient != null && ftpClient.isConnected()) {
            ftpClient.disconnect();
        }
    }

    /**
     * Insure the docker container started and that an ftp client can connect with the expected username
     * and password
     *
     * @throws IOException
     */
    @Test
    public void testConnectFtpServer() throws IOException {
        if (InetAddresses.isInetAddress(ftpHost)) {
            ftpClient.connect(InetAddresses.forString(ftpHost), ftpPort);
        } else {
            ftpClient.connect(ftpHost, ftpPort);
        }

        assertTrue(
                FTPReply.isPositiveCompletion(
                        ftpClient.getReplyCode()));

        assertTrue(ftpClient.login("nihmsftpuser", "nihmsftppass"));

        assertTrue(
                FTPReply.isPositiveCompletion(
                        ftpClient.getReplyCode()));
    }
    
}
