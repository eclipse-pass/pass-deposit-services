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

import org.apache.commons.io.input.BrokenInputStream;
import org.apache.commons.net.ftp.FTPClient;
import org.dataconservancy.nihms.integration.BaseIT;
import org.dataconservancy.nihms.transport.Transport;
import org.dataconservancy.nihms.transport.TransportResponse;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.stream.Stream;

import static org.dataconservancy.nihms.transport.ftp.FtpUtil.PATH_SEP;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.performSilently;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class FtpTransportIT extends BaseIT {

    private static final String FILE_LISTING = "Listing files in directory {}: {}";

    private FtpTransport transport;

    private FtpTransportSession transportSession;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        transport = new FtpTransport(mock(FtpClientFactory.class));
        transportSession = transport.open(ftpClient, new HashMap<String, String>() {
            {
                put(Transport.TRANSPORT_PROTOCOL, Transport.PROTOCOL.ftp.name());
                put(Transport.TRANSPORT_AUTHMODE, Transport.AUTHMODE.userpass.name());
                put(Transport.TRANSPORT_SERVER_FQDN, ftpHost);
                put(Transport.TRANSPORT_SERVER_PORT, String.valueOf(ftpPort));
                put(Transport.TRANSPORT_USERNAME, FTP_INTEGRATION_USERNAME);
                put(Transport.TRANSPORT_PASSWORD, FTP_INTEGRATION_PASSWORD);
                put(FtpTransportHints.TRANSFER_MODE, FtpTransportHints.MODE.stream.name());

                // Opening a session puts us into this base directory, creating it if necessary.
                put(FtpTransportHints.BASE_DIRECTORY, FtpTransportIT.class.getSimpleName());
            }
        });

        // Assert we were put into the correct base directory.
        assertEquals(asDirectory(FtpTransportIT.class.getSimpleName()), ftpClient.printWorkingDirectory());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (transportSession != null) {
            transportSession.close();
        } else {
            FtpUtil.disconnect(ftpClient, true);
        }
    }

    @Test
    public void testStoreFile() throws Exception {
        String expectedFilename = "FtpTransportIT-testStoreFile.jpg";
        TransportResponse response = transportSession.storeFile(expectedFilename, this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);
    }

    @Test
    public void testStoreFileWithDirectory() throws Exception {
        String expectedDirectory = "FtpTransportIT";
        String expectedFilename = "testStoreFileWithDirectory.jpg";
        String storeFilename = expectedDirectory + "/" + expectedFilename;
        TransportResponse response = transportSession.storeFile(storeFilename, this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedDirectory);

        FtpUtil.setWorkingDirectory(ftpClient, expectedDirectory);

        assertFileListingContains(expectedFilename);
    }

    @Test
    public void testStoreFileWithSameName() throws Exception {
        String expectedFilename = "FtpTransportIT-testStoreFileWithSameName.jpg";
        TransportResponse response = transportSession.storeFile(expectedFilename, this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);

        response = transportSession.storeFile(expectedFilename, this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);
    }

    @Test
    public void testSendFile() throws Exception {
        String expectedFilename = "FtpTransportIT-testSendFile.jpg";
        TransportResponse response = transportSession.send(expectedFilename, this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);
    }

    @Test
    public void testSendFileWithException() throws Exception {
        String expectedFilename = "FtpTransportIT-testSendFileWithException.jpg";
        IOException expectedException = new IOException("Broken stream.");

        TransportResponse response = transportSession.send(expectedFilename, new BrokenInputStream(expectedException));

        assertErrorResponse(response);
        assertEquals(expectedException, response.error().getCause().getCause().getCause());

        performSilently(() -> assertTrue(Stream.of(ftpClient.listFiles())
                .peek(f -> LOG.trace(FILE_LISTING, performSilently(() -> ftpClient.printWorkingDirectory()), f.getName()))
                .noneMatch(candidateFile -> candidateFile.getName().endsWith(expectedFilename))));
    }

    @Test
    public void testSendMultipleFiles() throws Exception {
        String expectedFilename_01 = "FtpTransportIT-testSendMultipleFiles-01.jpg";
        String expectedFilename_02 = "FtpTransportIT-testSendMultipleFiles-02.jpg";


        TransportResponse response = transportSession.send(expectedFilename_01,
                this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);
        assertFileListingContains(expectedFilename_01);

        response = transportSession.send(expectedFilename_02,
                this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);
        assertFileListingContains(expectedFilename_02);
    }

    /**
     * Asserts that the supplied {@code response} represents a non-successful transaction occurred.
     *
     * @param response the transport response
     */
    private static void assertErrorResponse(TransportResponse response) {
        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
    }

    /**
     * Asserts that the supplied {@code response} represents a successful transaction occurred.
     *
     * @param response the transport response
     */
    private static void assertSuccessfulResponse(TransportResponse response) {
        assertNotNull(response);
        assertTrue(response.success());
        assertNull(response.error());
    }

    /**
     * Lists the files in the current working directory of the FTP server, and asserts that there is at least one file
     * name that matches the {@code expectedFilename}.
     *
     * @param expectedFilename the file that is expected to exist in the current working directory
     */
    private void assertFileListingContains(String expectedFilename) {
        performSilently(() -> assertTrue(Stream.of(ftpClient.listFiles())
                .peek(f -> LOG.trace(FILE_LISTING, performSilently(() -> ftpClient.printWorkingDirectory()), f.getName()))
                .anyMatch(candidateFile -> candidateFile.getName().endsWith(expectedFilename))));
    }

    /**
     * Prefixs the supplied path component with a path separator.  If the path component already starts with a path
     * separator, it is returned unchanged
     *
     * @param pathComponent a non-null path component
     * @return the path component prefixed with a path separator
     */
    private static String asDirectory(String pathComponent) {
        if (pathComponent.startsWith(PATH_SEP)) {
            return pathComponent;
        }
        return PATH_SEP + pathComponent;
    }
}
