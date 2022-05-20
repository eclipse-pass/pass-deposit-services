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
package org.dataconservancy.nihms.integration;

import static org.junit.Assert.assertNotNull;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.After;
import org.junit.Before;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class FtpBaseIT extends BaseIT {

    protected static final String FTP_INTEGRATION_USERNAME = "nihmsftpuser";

    protected static final String FTP_INTEGRATION_PASSWORD = "nihmsftppass";

    /**
     * A string that is likely to be unique each time ITs are run.  This string will be used as the base directory for
     * any ITs that create content (directories, files) on the FTP server.
     */
    protected static final String FTP_SUBMISSION_BASE_DIRECTORY = String.format("/%s", System.currentTimeMillis());

    protected String ftpHost;

    protected int ftpPort;

    protected FTPClient ftpClient;

    protected IntegrationUtil itUtil;

    @Before
    public void setUp() throws Exception {
        ftpHost = System.getProperty(DOCKER_HOST_PROPERTY);
        ftpPort = Integer.parseInt(System.getProperty("ftp.port", "21"));
        assertNotNull("Missing value for system property '" + DOCKER_HOST_PROPERTY + "'", ftpHost);

        ftpClient = new FTPClient();

        itUtil = new IntegrationUtil(ftpHost, ftpPort, ftpClient);
    }

    @After
    public void tearDown() throws Exception {
        LOG.debug("({}) Disconnecting ...", ftpClient());
        itUtil.disconnect();
        LOG.debug("({}) Successfully disconnected.", ftpClient());
    }

    protected String ftpClient() {
        if (ftpClient == null) {
            return "null";
        } else {
            return ftpClient.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(ftpClient));
        }
    }

}
