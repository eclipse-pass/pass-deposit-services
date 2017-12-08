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
package org.dataconservancy.nihms.cli;

import org.dataconservancy.nihms.integration.BaseIT;
import org.dataconservancy.nihms.submission.SubmissionEngine;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsSubmissionAppIT extends BaseIT {

    private static String SUBMISSION_PROPERTIES_RESOURCE = "FilesystemModelBuilderTest.properties";

    private URL submissionProperties;

    /**
     * Insure that the classpath resource which embodies a submission is available.
     * Connect and login to the FTP server.
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();
        submissionProperties = this.getClass().getResource(SUBMISSION_PROPERTIES_RESOURCE);
        assertNotNull("Unable to locate " + SUBMISSION_PROPERTIES_RESOURCE + " as a classpath resource",
                submissionProperties);
        itUtil.connect();
        itUtil.login();
    }

    /**
     * Asserts that the base directory for the submission does not yet exist (this assertion only works because there
     * are no other tests in this class so far).  It is important to logout because the FTP server is configured to only
     * allow one connection at a time (helps discover connection leaks).
     *
     * Boots up the submission app and performs a submission.  The submission app will connect, login, submit, then
     * logout and disconnect.
     *
     * Connect and login to the FTP server, and verify that the submission base directory is there, and that it contains
     * a single file, presumably the package that was just put there by the app.
     *
     * @throws Exception
     */
    @Test
    public void testSubmissionFromCli() throws Exception {
        assertFalse(ftpClient.changeWorkingDirectory(SubmissionEngine.BASE_DIRECTORY));
        itUtil.logout();

        NihmsSubmissionApp app = new NihmsSubmissionApp(new File(submissionProperties.getPath()), "local");
        app.run();

        itUtil.connect();
        itUtil.login();
        assertTrue(ftpClient.changeWorkingDirectory(SubmissionEngine.BASE_DIRECTORY));
        assertEquals(1, ftpClient.listFiles().length);
    }
}
