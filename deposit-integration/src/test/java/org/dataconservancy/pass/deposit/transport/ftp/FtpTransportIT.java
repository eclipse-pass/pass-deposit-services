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

import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.PATH_SEP;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.directoryExists;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.isPathAbsolute;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.performSilently;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.input.BrokenInputStream;
import org.dataconservancy.nihms.integration.BaseIT;
import org.dataconservancy.nihms.integration.FtpBaseIT;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

public class FtpTransportIT extends FtpBaseIT {

    private static final String EXPECTED_SUCCESS = "Expected successful TransportResponse.  " +
                                                   "Underlying exception was:%n%s";

    private static final String FILE_LISTING = "Listing files in directory {}: {}";

    private static final String FTP_BASE_DIRECTORY = String.format("%s/%s", FtpBaseIT.FTP_SUBMISSION_BASE_DIRECTORY,
                                                                   FtpTransportIT.class.getSimpleName());

    private FtpTransport transport;

    private FtpTransportSession transportSession;

    /**
     * Opens an {@link FtpTransportSession} to the FTP server running in Docker.  After logging in, a directory is made
     * on the FTP server corresponding to the test class name, and that is set as the base working directory for the
     * test.
     *
     * The {@link FtpTransport} is instantiated using a mock factory, because we use the {@link BaseIT#ftpClient} for
     * performing tests.
     */
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
                put(FtpTransportHints.BASE_DIRECTORY, FTP_BASE_DIRECTORY);
            }
        });

        // Assert we were put into the correct base directory.
        assertEquals(FTP_BASE_DIRECTORY, ftpClient.printWorkingDirectory());
    }

    /**
     * If the transport session was created, close it, otherwise use the underlying FTP client to disconnect from the
     * server.  Closing the transport session should close the underlying FTP client.
     */
    @Override
    @After
    public void tearDown() throws Exception {
        if (transportSession != null) {
            transportSession.close();
            assertFalse(ftpClient.isConnected());
        } else {
            FtpUtil.disconnect(ftpClient, true);
        }
    }

    /**
     * Attempts to successfully store a file using the package-private
     * {@link FtpTransportSession#storeFile(String, InputStream)}
     * method.
     */
    @Test
    public void testStoreFile() {
        String expectedFilename = "FtpTransportIT-testStoreFile.jpg";
        TransportResponse response = transportSession.storeFile(expectedFilename,
                                                                this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);
    }

    /**
     * Attempts to successfully store a file that has path components in it.  The implementation should create
     * intermediate directories.
     */
    @Test
    public void testStoreFileWithDirectory() {
        String expectedFilename = "testStoreFileWithDirectory.jpg";
        String expectedDirectory = String.format("%s/%s", FTP_BASE_DIRECTORY, "testStoreFileWithDirectory");
        String storeFilename = String.format("%s/%s", expectedDirectory, expectedFilename);
        assertFalse("Did not expect the directory '" + expectedDirectory + "' to exist on the FTP server!",
                    directoryExists(ftpClient, expectedDirectory));

        assertTrue("Expected the store filename to be an absolute path.", isPathAbsolute(storeFilename));

        TransportResponse response = transportSession.storeFile(storeFilename,
                                                                this.getClass().getResourceAsStream("/org.jpg"));
        assertSuccessfulResponse(response);

        assertTrue("Expected the directory '" + expectedDirectory + "' to be created on the FTP server!",
                   directoryExists(ftpClient, expectedDirectory));

        FtpUtil.setWorkingDirectory(ftpClient, expectedDirectory);

        assertFileListingContains(expectedFilename);
    }

    /**
     * Attempt to store the same file twice (with the second attempt overwriting the first file) and see how the FTP
     * server responds.
     */
    @Test
    public void testStoreFileWithSameName() {
        String expectedFilename = "FtpTransportIT-testStoreFileWithSameName.jpg";
        TransportResponse response = transportSession.storeFile(expectedFilename,
                                                                this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);

        response = transportSession.storeFile(expectedFilename, this.getClass().getResourceAsStream("/org.jpg"));

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);
    }

    /**
     * Attempt to send a file to the FTP server using the <em>public</em>
     * {@link FtpTransportSession#send(PackageStream, Map)}
     * method.
     */
    @Test
    public void testSendFile() {
        String expectedFilename = "FtpTransportIT-testSendFile.jpg";
        PackageStream stream = resourceAsPackage(expectedFilename,
                                                 this.getClass().getResourceAsStream("/org.jpg"), -1);
        TransportResponse response = transportSession.send(stream, Collections.emptyMap());

        assertSuccessfulResponse(response);

        assertFileListingContains(expectedFilename);
    }

    /**
     * Attempt to send a file to the FTP server using the <em>public</em>
     * {@link FtpTransportSession#send(PackageStream, Map)}
     * method, which will fail because the file stream cannot be read.  Insure that the underlying exception can be
     * retrieved and that the transport response indicates failure.  Insure that the file is not present on the FTP
     * server.  The underlying FTP connection should still be open even though the file transfer failed.
     */
    @Test
    public void testSendFileWithException() {
        String expectedFilename = "FtpTransportIT-testSendFileWithException.jpg";
        IOException expectedException = new IOException("Broken stream.");

        PackageStream brokenStream = resourceAsPackage(expectedFilename,
                                                       new BrokenInputStream(expectedException), -1);
        TransportResponse response = transportSession.send(brokenStream, Collections.emptyMap());

        assertErrorResponse(response);
        assertEquals(expectedException, response.error().getCause());

        ftpClient.setUseEPSVwithIPv4(true);
        ftpClient.enterLocalPassiveMode();

        performSilently(() -> assertTrue(Stream.of(ftpClient.listFiles())
                                               .peek(f -> LOG.trace(FILE_LISTING, performSilently(
                                                   () -> ftpClient.printWorkingDirectory()), f.getName()))
                                               .noneMatch(candidateFile -> candidateFile.getName()
                                                                                        .endsWith(expectedFilename))));

        assertTrue(ftpClient.isConnected());
        assertTrue(performSilently(() -> ftpClient.sendNoOp()));
    }

    /**
     * A transport session should not become unusable just because a file transfer failed.
     * <p>
     * Attempt to send a file to the FTP server using the <em>public</em>
     * {@link FtpTransportSession#send(PackageStream, Map)}
     * method, which will fail because the file stream cannot be read.  Then retry, and send a file that should succeed.
     * </p>
     * Assertions that are redundant with respect to {@link #testSendFileWithException()} are not re-asserted.
     * <p>
     * This test currently fails because upon the second attempt, a data connection cannot be opened by the FTPClient.
     * FTPClient.storeFile(...) attempts to open a data connection which comes back null.  The data connection comes
     * back null because in attempting to open a data connection a PASV command is issued to the server.  According to
     * the logs on the FTP server, the PASV command succeeds, but for some reason when the FTP reply is read by
     * FTPClient, it reads back the reply from a previous command (the TYPE command with a reply of '200 TYPE is now
     * 8-bit binary').  Since the response from the TYPE command is not what is expected by the FTPClient upon issuing
     * a PASV command, the data connection comes back null.
     *
     * Something definitely seems to be off regarding the state of the control channel's reply codes and messages, but
     * for now if a file transfer fails, a new transport session needs to be re-opened, and the the tranfer re-tried
     * from the new session.
     * </p>
     */
    @Test
    @Ignore("Test currently fails")
    public void testSendFileWithExceptionAndTryAgain() throws IOException {
        String expectedFilename = "FtpTransportIT-testSendFileWithExceptionAndTryAgain.jpg";
        IOException expectedException = new IOException("Broken stream.");

        PackageStream brokenStream = resourceAsPackage(expectedFilename,
                                                       new BrokenInputStream(expectedException), -1);
        TransportResponse response = transportSession.send(brokenStream, Collections.emptyMap());

        assertErrorResponse(response);
        assertEquals(expectedException, response.error().getCause().getCause().getCause());

        PackageStream stream = resourceAsPackage(expectedFilename,
                                                 this.getClass().getResourceAsStream("/org.jpg"), -1);

        response = transportSession.send(stream, Collections.emptyMap());

        assertSuccessfulResponse(response);
        assertFileListingContains(expectedFilename);
    }

    /**
     * Attempt to send multiple files successfully using the same session.
     */
    @Test
    public void testSendMultipleFiles() {
        String expectedFilename_01 = "FtpTransportIT-testSendMultipleFiles-01.jpg";
        String expectedFilename_02 = "FtpTransportIT-testSendMultipleFiles-02.jpg";

        TransportResponse response = transportSession.send(
            resourceAsPackage(expectedFilename_01, this.getClass().getResourceAsStream("/org.jpg"), -1),
            Collections.emptyMap());

        assertSuccessfulResponse(response);
        assertFileListingContains(expectedFilename_01);

        response = transportSession.send(
            resourceAsPackage(expectedFilename_02, this.getClass().getResourceAsStream("/org.jpg"), -1),
            Collections.emptyMap());

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
        String exceptionTrace = null;
        if (response.error() != null) {
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            response.error().printStackTrace(new PrintStream(sink));
            exceptionTrace = new String(sink.toByteArray());
        }
        assertNotNull(response);
        assertTrue(String.format(EXPECTED_SUCCESS, exceptionTrace), response.success());
        assertNull(response.error());
    }

    /**
     * Lists the files in the current working directory of the FTP server, and asserts that there is at least one file
     * name that matches the prefix and the suffix of the {@code expectedFilename}.
     *
     * @param expectedFilename the file that is expected to exist in the current working directory
     */
    private void assertFileListingContains(String expectedFilename) {
        ftpClient.setUseEPSVwithIPv4(true);
        ftpClient.enterLocalPassiveMode();

        String prefix = (expectedFilename.contains(".")) ? expectedFilename.substring(0, expectedFilename.indexOf(
            ".")) : expectedFilename;
        String suffix = (expectedFilename.contains(".")) ? expectedFilename.substring(
            expectedFilename.indexOf(".")) : "";

        assertTrue("Must have a filename prefix!", prefix.length() > 0);
        assertTrue("Must have a filename suffix!", suffix.length() > 0);

        performSilently(() -> assertTrue(Stream.of(ftpClient.listFiles())
                                               .peek(f -> LOG.trace(FILE_LISTING, performSilently(
                                                   () -> ftpClient.printWorkingDirectory()), f.getName()))
                                               .anyMatch(candidateFile -> candidateFile.getName().startsWith(
                                                   prefix) && candidateFile.getName().endsWith(suffix))));
    }

    /**
     * Lists the contents of the current working directory of the FTP server, and asserts that there is at least one
     * directory
     * name that matches the prefix and the suffix of the {@code expectedFilename}. This test is a little different
     * from the file
     * name test in taht we allow directory names to not be "normal" - i.e., they may not have a suffix
     *
     * @param expectedDirectoryName the file that is expected to exist in the current working directory
     */
    private void assertDirectoryListingContains(String expectedDirectoryName) {
        ftpClient.setUseEPSVwithIPv4(true);
        ftpClient.enterLocalPassiveMode();

        String prefix = (expectedDirectoryName.contains(".")) ?
                expectedDirectoryName.substring(0, expectedDirectoryName.indexOf(".")) : expectedDirectoryName;
        String suffix = (expectedDirectoryName.contains(".")) ?
                expectedDirectoryName.substring(expectedDirectoryName.indexOf(".")) : "";

        assertTrue("Must have a filename prefix!", prefix.length() > 0);
        //directory names may not have a dot in them, so we do not check for a positive length suffix

        performSilently(() -> assertTrue(Stream.of(ftpClient.listFiles())
                                               .peek(f -> LOG.trace(FILE_LISTING, performSilently(
                                                   () -> ftpClient.printWorkingDirectory()), f.getName()))
                                               .anyMatch(candidateFile -> candidateFile.getName().startsWith(
                                                   prefix) && candidateFile.getName().endsWith(suffix))));
    }

    /**
     * Prefixes the supplied path component with a path separator.  If the path component already starts with a path
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

    /**
     * Wraps a single {@code resource} in a {@code PackageStream}.  The returned PackageStream will use
     * the supplied {@code name} as the name of the file, and will return the InputStream {@code resource} in
     * response to {@link PackageStream#open()}.
     *
     * @param name     the name of the package, used as the filename by the FtpTransport
     * @param resource the resource which is encapsulated by the PackageStream
     * @return the resource encapsulated as a PackageStream
     */
    private static PackageStream resourceAsPackage(String name, InputStream resource, long length) {

        PackageStream stream = mock(PackageStream.class);
        PackageStream.Metadata md = mock(PackageStream.Metadata.class);

        when(md.name()).thenReturn(name);
        when(md.sizeBytes()).thenReturn(length);
        when(md.mimeType()).thenReturn("application/octet-stream");
        when(md.compression()).thenReturn(Compression.OPTS.NONE);
        when(md.archive()).thenReturn(Archive.OPTS.NONE);

        when(stream.metadata()).thenReturn(md);
        when(stream.open()).thenReturn(resource);

        return stream;
    }
}
