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

package org.dataconservancy.pass.deposit.transport.sword2;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Base64.getEncoder;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.abdera.model.Element;
import org.apache.abdera.model.Link;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.BrokenInputStream;
import org.dataconservancy.nihms.integration.BaseIT;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Checksum;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.swordapp.client.AuthCredentials;
import org.swordapp.client.ClientConfiguration;
import org.swordapp.client.DepositReceipt;
import org.swordapp.client.ProtocolViolationException;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDClientException;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDError;
import org.swordapp.client.ServiceDocument;
import org.swordapp.client.SwordIdentifier;

public class Sword2TransportSessionIT extends BaseIT {

    /**
     * Property used by the user or test framework to specify a non-default username and password for logging into the
     * DSpace SWORD endpoint, in the form {@code username:password}.
     * <p>
     * Example usage: {@code -Ddspace.auth=adminuser:adminpass}
     * </p>
     */
    private static final String DSPACE_AUTH_PROPERTY = "dspace.auth";

    /**
     * Property used by the user or test framework to specify a non-default SWORD service document URL.
     * <p>
     * Example usage: {@code -Dsword.servicedoc=https://dspace-stage.mse.jhu.edu/swordv2/servicedocument}
     * </p>
     */
    private static final String SWORD_SERVICEDOC_PROPERTY = "sword.servicedoc";

    /**
     * Property used by the user or test framework to specify a non-default SWORD collection URL for deposit.
     * <p>
     * Example usage: {@code -Dsword.collection=https://dspace-stage.mse.jhu.edu/swordv2/collection/1774.2/46194}
     * </p>
     */
    private static final String SWORD_COLLECTION_PROPERTY = "sword.collection";

    /**
     * Property used by the user or test framework to specify an on-behalf-of user for the SWORD deposit.
     * <p>
     * Example usage: {@code -Dsword.onbehalfof=emetsger@jhu.edu}
     * </p>
     */
    private static final String SWORD_ON_BEHALF_OF_PROEPRTY = "sword.onbehalfof";

    /**
     * User the SWORD client will authenticate as
     */
    private static final String DSPACE_ADMIN_USER = getAdminUser();

    /**
     * Password the SWORD client will authenticate with
     */
    private static final String DSPACE_ADMIN_PASSWORD = getAdminPassword();

    /**
     * Property used by the test framework to specify the port that DSpace is running on.
     */
    private static final String DSPACE_PORT_PROPERTY = "dspace.port";

    /**
     * The format string used to format the <em>default</em> SWORD service document URL.  The default SWORD service
     * document URL can be overridden by defining a value for {@link #SWORD_SERVICEDOC_PROPERTY}.
     */
    private static final String DEFAULT_SERVICEDOC_URL_FORMAT = "http://%s:%s/swordv2/servicedocument";

    /**
     * A hard-coded classpath resource that resolves to a package composed of a simple zip file according to the
     * packaging specification {@link #SPEC_SIMPLE_ZIP}.
     */
    private static final String SIMPLE_ZIP_PACKAGE_RESOURCE = "simplezippackage.zip";

    /**
     * A hard-coded classpath resource that resolves to a package composed of a simple zip file according to the
     * packaging specification {@link #SPEC_SIMPLE_ZIP}.
     */
    private static final String METS_PACKAGE_RESOURCE = "dspace-mets-01.zip";

    /**
     * Package specification URI identifying a simple zip file.
     */
    private static final String SPEC_SIMPLE_ZIP = "http://purl.org/net/sword/package/SimpleZip";

    /**
     * Package specification URI identifying a DSpace METS SIP.
     */
    private static final String SPEC_DSPACE_METS = "http://purl.org/net/sword/package/METSDSpaceSIP";

    /**
     * Mime type of zip files.
     */
    private static final String APPLICATION_ZIP = "application/zip";

    /**
     * SWORD Service Document endpoint
     */
    private static final String SERVICEDOC_ENDPOINT = getServiceDocumentUrl();

    /**
     * The <em>default</em> SWORD Collection being deposited to.  The default SWORD collection URL can be overridden
     * by defining a value or {@link #SWORD_COLLECTION_PROPERTY}.
     */
    private static final String DEFAULT_SWORD_COLLECTION_URL = formatSwordUrl(
        "http://%s:8181/swordv2/collection/123456789/2");

    /**
     * Configured SWORD client
     */
    private SWORDClient swordClient;

    /**
     * Authentication credentials used by the client when communicating with a SWORD endpoint.  This object encapsulates
     * the On-Behalf-Of semantics..
     */
    private AuthCredentials authCreds;

    /**
     * SWORD Service document retrieved from {@link #SERVICEDOC_ENDPOINT}.
     */
    private ServiceDocument serviceDoc;

    /**
     * Resolved package identified by {@link #SIMPLE_ZIP_PACKAGE_RESOURCE}
     */
    private File sampleZipPackage;

    /**
     * Resolved package identified by {@link #METS_PACKAGE_RESOURCE}
     */
    private File dspaceMetsPackage;

    /**
     * Performs basic IT setup:
     * <ul>
     *     <li>Discovers and makes available sample packages as {@code File} objects.</li>
     *     <li>Provides basic {@code SWORDClient} configuration, including authentication credentials.</li>
     *     <li>Retrieves the {@code ServiceDocument}.</li>
     * </ul>
     */
    @Before
    public void setUp() {
        sampleZipPackage = new File(this.getClass().getResource(SIMPLE_ZIP_PACKAGE_RESOURCE).getPath());
        assertNotNull(sampleZipPackage);
        assertTrue("Missing sample package; cannot resolve '" + SIMPLE_ZIP_PACKAGE_RESOURCE +
                   "' as a class path resource.", sampleZipPackage.exists());

        dspaceMetsPackage = new File(this.getClass().getResource(METS_PACKAGE_RESOURCE).getPath());
        assertNotNull(dspaceMetsPackage);
        assertTrue("Missing sample package; cannot resolve '" + METS_PACKAGE_RESOURCE +
                   "' as a class path resource.", dspaceMetsPackage.exists());

        ClientConfiguration swordConfig = new ClientConfiguration();
        swordConfig.setReturnDepositReceipt(true);
        swordConfig.setUserAgent("oapass/SWORDv2");

        if (onBehalfOf() == null) {
            authCreds = new AuthCredentials(DSPACE_ADMIN_USER, DSPACE_ADMIN_PASSWORD);
        } else {
            authCreds = new AuthCredentials(DSPACE_ADMIN_USER, DSPACE_ADMIN_PASSWORD, onBehalfOf());
        }

        swordClient = new SWORDClient(swordConfig);

        serviceDoc = getServiceDocument(swordClient, SERVICEDOC_ENDPOINT, authCreds);
    }

    /**
     * Deposits a package using the 'SimpleZip' packaging specification.  The response should be successful, and the
     * {@link org.swordapp.client.DepositReceipt} non-null.
     */
    @Test
    public void testSimple() throws FileNotFoundException {
        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL,
                        getSwordCollection(serviceDoc, APPLICATION_ZIP));

        Sword2DepositReceiptResponse response = (Sword2DepositReceiptResponse)
            underTest.send(packageStream, transportMd);
        assertNotNull(response);
        assertTrue(response.success());
        assertNotNull(response.getReceipt());
    }

    /**
     * Deposits a package using the 'METSDSpaceSIP' packaging specification.  The response should be successful, and the
     * {@link org.swordapp.client.DepositReceipt} non-null.
     */
    @Test
    public void testDspaceMets() throws FileNotFoundException, SWORDClientException {
        PackageStream.Metadata md = preparePackageMd(dspaceMetsPackage, SPEC_DSPACE_METS, APPLICATION_ZIP);
        PackageStream packageStream = preparePackageStream(md, dspaceMetsPackage);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL,
                        getSwordCollection(serviceDoc, APPLICATION_ZIP));

        TransportResponse response = underTest.send(packageStream, transportMd);
        assertNotNull(response);
        assertTrue("Expected a successful response, but it errored with: " + asString(response.error()),
                   response.success());
        assertTrue(response instanceof Sword2DepositReceiptResponse);

        assertNotNull(((Sword2DepositReceiptResponse) response).getReceipt());

        DepositReceipt receipt = ((Sword2DepositReceiptResponse) response).getReceipt();

        String desc = null;
        try {
            desc = receipt.getVerboseDescription();
            assertNotNull(desc);
        } catch (NullPointerException e) {
            LOG.debug("**** DepositReceipt verbose description was null!");
        }
        SwordIdentifier content = receipt.getContentLink();
        String treatement = receipt.getTreatment();
        List<Element> dc = receipt.getDublinCore();
        SwordIdentifier atomStatement = receipt.getAtomStatementLink();
        Link alt = receipt.getEntry().getAlternateLink();

        assertNotNull(content);
        assertNotNull(treatement);
        assertNotNull(dc);
        assertFalse(dc.isEmpty());
        assertNotNull(atomStatement);
        assertNotNull(alt);

        System.err.println(">>>> (Receipt?) Location: " + receipt.getLocation());
        System.err.println(">>>> Content link: " + content.getHref());
        System.err.println(">>>> Atom Statement (Atom): " + atomStatement.getHref());
        System.err.println(">>>> Atom Statement (RDF): " + receipt.getOREStatementLink().getHref());
        System.err.println(">>>> Alt link: " + receipt.getEntry().getAlternateLink().getHref().toASCIIString());
        System.err.println(">>>> Verbose description: " + desc);
        System.err.println(">>>> Treatment: " + treatement);
        System.err.println(">>>> DC fields: ");
        dc.forEach(e -> System.err.println(
            "    " + String.format("{%s}%s: %s", e.getQName().getNamespaceURI(), e.getQName().getLocalPart(),
                                   e.getText())));

        // Retrieve all the URLs we can.
        OkHttpClient okHttp = newOkHttpClient(authCreds);

        Stream<String> toRetrieve = Stream.of(receipt.getLocation(), content.getHref(), atomStatement.getHref(),
                                              receipt.getOREStatementLink()
                                                     .getHref(),
                                              receipt.getEntry().getAlternateLink().getHref().toString());

        toRetrieve.forEach(url -> {
            LOG.debug("Retrieving '{}' ...", url);
            try (Response res = okHttp.newCall(new Request.Builder().url(url).build()).execute()) {
                int code = res.code();
                LOG.debug("Retrieved '{}', {}", url, code);
                String message = res.message();
                ResponseBody body = res.body();
                String bodyString = body == null ? "Response body was null " : body.string();

                assertTrue(
                    "Unexpected response code '" + code + "' when GETting '" + url + "': " + message + "\n" +
                            bodyString,
                    199 < code && code < 300);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("{}", (body == null ? "Response body was null" : bodyString));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }

    /**
     * Purposefully supply an invalid checksum when depositing a package.  The response should indicate failure, and
     * provide a Throwable that contains the {@link org.swordapp.client.SWORDError#errorBody error body}, with a message
     * indicating a checksum failure.
     */
    @Test
    public void testWithBadMd5Checksum() throws FileNotFoundException {
        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        PackageStream.Checksum invalidChecksum = new PackageStream.Checksum() {
            @Override
            public Checksum.OPTS algorithm() {
                return Checksum.OPTS.MD5;
            }

            @Override
            public byte[] value() {
                return new byte[128];
            }

            @Override
            public String asBase64() {
                return encodeBase64String(value());
            }

            @Override
            public String asHex() {
                return DigestUtils.md5Hex("hello world!");
            }
        };
        when(md.checksum()).thenReturn(invalidChecksum);
        when(md.checksums()).thenReturn(Collections.singletonList(invalidChecksum));

        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL,
                        getSwordCollection(serviceDoc, APPLICATION_ZIP));

        TransportResponse response = underTest.send(packageStream, transportMd);

        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
        assertTrue(response.error().getMessage().contains("MD5 checksum for the deposited file did not match"));
    }

    /**
     * Purposefully supply an invalid packaging specification when depositing a package.  The response should indicate
     * failure, and provide a Throwable that contains the {@link org.swordapp.client.SWORDError#errorBody error body},
     * with a message indicating an invalid packaging spec was supplied.
     */
    @Test
    public void testWithUnsupportedPackagingType() throws FileNotFoundException {
        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        when(md.spec()).thenReturn("http://invalid.spec/url");
        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL,
                        getSwordCollection(serviceDoc, APPLICATION_ZIP));

        TransportResponse response = underTest.send(packageStream, transportMd);

        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
        assertTrue(response.error().getMessage().contains("Unacceptable packaging type in deposit request"));
    }

    /**
     * Purposefully deposit to an invalid collection URL.  Response should indicate failure, and the cause of the
     * failure should be an {@link InvalidCollectionUrl} exception.
     */
    @Test
    public void testDepositToNonExistentCollectionUrl() throws FileNotFoundException {
        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);
        String invalidCollectionUrl = DEFAULT_SWORD_COLLECTION_URL + "/123456";

        Map<String, String> transportMd = new HashMap<>();
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL, invalidCollectionUrl);

        TransportResponse response = underTest.send(packageStream, transportMd);

        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
        assertTrue(response.error() instanceof InvalidCollectionUrl);
    }

    /**
     * Perform a deposit when collection hints are provided by the submission, and a matching hint is configured in
     * deposit services.  The package should be deposited into the collection specified by the configured hint.
     */
    @Test
    public void testDepositWithCollectionHints()
        throws FileNotFoundException, ProtocolViolationException, SWORDError, SWORDClientException {
        String submissionMetaStr = "{\n" +
                                   "    \"$schema\": \"https://oa-pass.github.io/metadata-schemas/jhu/global.json\"," +
                                   "\n" +
                                   "    \"title\": \"The title of the article\",\n" +
                                   "    \"journal-title\": \"A Terrific Journal\",\n" +
                                   "    \"hints\": {\n" +
                                   "        \"collection-tags\": [\n" +
                                   "            \"covid\",\n" +
                                   "            \"nobel\"\n" +
                                   "        ]\n" +
                                   "    }\n" + "}";

        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        JsonObject submissionMeta = new JsonParser().parse(submissionMetaStr).getAsJsonObject();
        when(md.submissionMeta()).thenReturn(submissionMeta);

        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        swordClient = spy(swordClient);
        ArgumentCaptor<SWORDCollection> captor = ArgumentCaptor.forClass(SWORDCollection.class);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        String collectionUrl = getSwordCollection(serviceDoc, APPLICATION_ZIP);
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL, collectionUrl);
        String configuredUrl = collectionUrl.replace("/2", "/4");
        String configuredHints = String.format("%s%s%s", "covid", Sword2TransportHints.HINT_URL_SEPARATOR,
                                               configuredUrl);
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_HINTS, configuredHints);

        Sword2DepositReceiptResponse response = (Sword2DepositReceiptResponse) underTest.send(packageStream,
                                                                                              transportMd);

        assertNotNull(response);
        assertTrue(response.success());
        assertNull(response.error());

        verify(swordClient).deposit(captor.capture(), any(), any());

        assertEquals(configuredUrl, captor.getValue().getHref().toString());
        assertTrue(configuredUrl.endsWith("/4"));
    }

    /**
     * Perform a deposit when collection hints are provided by the submission, but no configured hints match.  The
     * deposit should go to the default collection url.
     */
    @Test
    public void testDepositWithCollectionHintsNoMatch() throws FileNotFoundException, ProtocolViolationException,
        SWORDError, SWORDClientException {
        String submissionMetaStr = "{\n" +
                                   "    \"$schema\": \"https://oa-pass.github.io/metadata-schemas/jhu/global.json\"," +
                                   "\n" +
                                   "    \"title\": \"The title of the article\",\n" +
                                   "    \"journal-title\": \"A Terrific Journal\",\n" +
                                   "    \"hints\": {\n" +
                                   "        \"collection-tags\": [\n" +
                                   "            \"covid\",\n" +
                                   "            \"nobel\"\n" +
                                   "        ]\n" +
                                   "    }\n" + "}";

        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        JsonObject submissionMeta = new JsonParser().parse(submissionMetaStr).getAsJsonObject();
        when(md.submissionMeta()).thenReturn(submissionMeta);

        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        swordClient = spy(swordClient);
        ArgumentCaptor<SWORDCollection> captor = ArgumentCaptor.forClass(SWORDCollection.class);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        String defaultCollectionUrl = getSwordCollection(serviceDoc, APPLICATION_ZIP);
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL, defaultCollectionUrl);
        TransportResponse response = underTest.send(packageStream, transportMd);

        assertNotNull(response);
        assertTrue(response.success());
        assertNull(response.error());

        verify(swordClient).deposit(captor.capture(), any(), any());

        assertEquals(defaultCollectionUrl, captor.getValue().getHref().toString());
    }

    /**
     * Purposefully throw an exception when the PackageStream is opened.  The underlying error is available as
     * as suppressed exception.
     */
    @Test
    public void testIOException() throws FileNotFoundException {
        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        final IOException expectedException = new IOException("Expected Exception!");
        BrokenInputStream brokenIn = new BrokenInputStream(expectedException);
        when(packageStream.open()).thenReturn(brokenIn);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL, getSwordCollection(serviceDoc, APPLICATION_ZIP));

        TransportResponse response = underTest.send(packageStream, transportMd);

        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
        assertNotNull(response.error().getCause());

        // due to use of try-with-resources blocks
        assertEquals(1, response.error().getCause().getSuppressed().length);
        assertSame(expectedException, response.error().getCause().getSuppressed()[0]);
    }

    /**
     * Purposefully throw a RuntimeException when the Package is deposited.
     */
    @Test
    public void testGenericException() throws FileNotFoundException, ProtocolViolationException, SWORDError,
        SWORDClientException {
        PackageStream.Metadata md = preparePackageMd(sampleZipPackage, SPEC_SIMPLE_ZIP, APPLICATION_ZIP);
        PackageStream packageStream = preparePackageStream(md, sampleZipPackage);

        RuntimeException expectedException = new RuntimeException("Expected exception!");
        SWORDClient swordClient = mock(SWORDClient.class);
        when(swordClient.deposit(any(SWORDCollection.class), any(), eq(authCreds))).thenThrow(expectedException);

        Sword2TransportSession underTest = new Sword2TransportSession(swordClient, serviceDoc, authCreds);

        Map<String, String> transportMd = new HashMap<>();
        transportMd.put(Sword2TransportHints.SWORD_COLLECTION_URL, getSwordCollection(serviceDoc, APPLICATION_ZIP));

        TransportResponse response = underTest.send(packageStream, transportMd);

        assertNotNull(response);
        assertFalse(response.success());
        assertNotNull(response.error());
        assertSame(expectedException, response.error().getCause());
    }

    /**
     * Generate the package stream for the supplied file.  The supplied {@code md} will be returned in response to
     * {@link PackageStream#metadata()}.
     *
     * @param md
     * @param packageFile
     * @return
     * @throws FileNotFoundException
     */
    private static PackageStream preparePackageStream(PackageStream.Metadata md, File packageFile)
        throws FileNotFoundException {
        PackageStream packageStream = mock(PackageStream.class);

        when(packageStream.open()).thenReturn(new FileInputStream(packageFile));
        when(packageStream.metadata()).thenReturn(md);
        return packageStream;
    }

    /**
     * Generate the package metadata for the supplied file.  The returned metadata will use {@code packageSpec} for the
     * {@link PackageStream.Metadata#spec()}, and {@code packageMimeType} for {@link PackageStream.Metadata#mimeType()}.
     *
     * @param packageFile
     * @param packageSpec
     * @param packageMimeType
     * @return
     */
    private static PackageStream.Metadata preparePackageMd(File packageFile, String packageSpec,
                                                           String packageMimeType) {
        PackageStream.Metadata md = mock(PackageStream.Metadata.class);
        when(md.name()).thenReturn(packageFile.getName());
        when(md.spec()).thenReturn(packageSpec);
        when(md.archive()).thenReturn(Archive.OPTS.ZIP);
        when(md.archived()).thenReturn(true);
        when(md.compression()).thenReturn(Compression.OPTS.ZIP);
        when(md.compressed()).thenReturn(true);
        when(md.sizeBytes()).thenReturn(packageFile.length());
        when(md.mimeType()).thenReturn(packageMimeType);
        final PackageStream.Checksum md5 = new PackageStream.Checksum() {
            @Override
            public Checksum.OPTS algorithm() {
                return Checksum.OPTS.MD5;
            }

            @Override
            public byte[] value() {
                try {
                    MessageDigest digest = MessageDigest.getInstance("MD5");
                    return DigestUtils.digest(digest, new FileInputStream(packageFile));
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }

            @Override
            public String asBase64() {
                return encodeBase64String(value());
            }

            @Override
            public String asHex() {
                String hex = null;
                try {
                    hex = DigestUtils.md5Hex(new FileInputStream(packageFile));
                } catch (IOException e) {
                    throw new RuntimeException("Error calculating the MD5 checksum for '" +
                                               packageFile.getPath() + "'");
                }
                return hex;
            }
        };
        when(md.checksum()).thenReturn(md5);
        when(md.checksums()).thenReturn(Collections.singletonList(md5));

        return md;
    }

    /**
     * Retrieve the service document from {@code serviceDocEndpoint} using the supplied {@code swordClient} and
     * {@code authCreds}.
     *
     * @param swordClient
     * @param serviceDocEndpoint
     * @param authCreds
     * @return
     */
    private static ServiceDocument getServiceDocument(SWORDClient swordClient, String serviceDocEndpoint,
                                                      AuthCredentials authCreds) {
        ServiceDocument serviceDoc = null;
        try {
            serviceDoc = swordClient.getServiceDocument(serviceDocEndpoint, authCreds);
            assertNotNull("SWORD Service Document obtained from '" + serviceDocEndpoint + "' (auth creds: " +
                                "'" + authCreds.getUsername() + "':'" + authCreds.getPassword() + "' (on-behalf-of: '" +
                                authCreds.getOnBehalfOf() + "') was null.",
                          serviceDoc);
        } catch (Exception e) {
            String msg = String.format("Failed to connect to %s: %s", serviceDocEndpoint, e.getMessage());
            LOG.error(msg, e);
            fail(msg);
        }
        return serviceDoc;
    }

    /**
     * Returns the user from the system property {@link #DSPACE_AUTH_PROPERTY}, or a default value if the system
     * property is not present.
     *
     * @return the DSpace admin user
     */
    private static String getAdminUser() {
        return System.getProperty(DSPACE_AUTH_PROPERTY,
                                  "dspace-admin@oapass.org").split(":")[0];
    }

    /**
     * Returns the password from the system property {@link #DSPACE_AUTH_PROPERTY}, or a default value if the system
     * property is not present or does not contain a password.
     *
     * @return the DSpace admin password
     */
    private static String getAdminPassword() {

        String value = System.getProperty(DSPACE_AUTH_PROPERTY, "foobar");
        if (value.contains(":")) {
            return value.split(":")[1];
        }

        return value;
    }

    /**
     * Returns the SWORD service document URL from the system property {@link #SWORD_SERVICEDOC_PROPERTY}, or a default
     * value if the system property is not present.
     * <p>
     * The default SWORD service document URL can be overridden by defining a value for
     * {@link #SWORD_SERVICEDOC_PROPERTY}.
     * </p>
     *
     * @return the SWORD service document URL
     */
    private static String getServiceDocumentUrl() {
        if (System.getProperty(SWORD_SERVICEDOC_PROPERTY) != null) {
            return System.getProperty(SWORD_SERVICEDOC_PROPERTY);
        }

        return formatSwordUrl(DEFAULT_SERVICEDOC_URL_FORMAT);
    }

    /**
     * Returns the SWORD collection URL from the system property {@link #SWORD_COLLECTION_PROPERTY}, or discovers the
     * collection based on the supplied {@code packaging}.
     *
     * @param packaging the string identifying the packaging that the collection must accept
     * @return the SWORD collection URL
     */
    private static String getSwordCollection(ServiceDocument serviceDoc, String packaging) {
        if (System.getProperty(SWORD_COLLECTION_PROPERTY) != null) {
            return System.getProperty(SWORD_COLLECTION_PROPERTY);
        }

        Collection<SWORDCollection> collections = serviceDoc.getCollectionsThatAccept(packaging);
        if (collections.isEmpty()) {
            fail(String.format("Unable to discover a collection from %s that supports packaging %s",
                               serviceDoc.getService().getBaseUri(), packaging));
        }

        return collections.iterator().next().getHref().toASCIIString();
    }

    /**
     * Replaces the placeholder for the hostname in the format string with the value from {@link #DOCKER_HOST_PROPERTY}
     * or {@code localhost}.
     *
     * @param format format string for a SWORD url, containing single placeholder for the hostname
     * @return the formatted URL string
     */
    private static String formatSwordUrl(String format) {
        return String.format(format,
                             System.getProperty(DOCKER_HOST_PROPERTY, "localhost"),
                             System.getProperty(DSPACE_PORT_PROPERTY, "8181"));
    }

    /**
     * Returns the SWORD on-behalf-of user from the system property {@link #SWORD_ON_BEHALF_OF_PROEPRTY}.
     *
     * @return the on-behalf-of user, or {@code null}
     */
    private static String onBehalfOf() {
        return System.getProperty(SWORD_ON_BEHALF_OF_PROEPRTY);
    }

    private static String asString(Throwable t) {
        if (t == null) {
            return "Throwable was null!";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        t.printStackTrace(new PrintStream(out, true));
        return new String(out.toByteArray());
    }

    private OkHttpClient newOkHttpClient(AuthCredentials authCreds) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(120, TimeUnit.SECONDS);

        String builderName = builder.getClass().getSimpleName();
        String builderHashcode = toHexString(identityHashCode(builder.getClass()));

        if (authCreds != null && authCreds.getUsername() != null) {
            LOG.trace("{}:{} adding Authorization interceptor", builderName, builderHashcode);
            builder.addInterceptor((chain) -> {
                Request request = chain.request();
                Request.Builder reqBuilder = request.newBuilder();
                byte[] bytes = String.format("%s:%s", authCreds.getUsername(), authCreds.getPassword()).getBytes();
                return chain.proceed(reqBuilder
                                         .addHeader("Authorization",
                                                    "Basic " + getEncoder().encodeToString(bytes)).build());
            });
        }

        if (LOG.isDebugEnabled()) {
            LOG.trace("{}:{} adding Logging interceptor", builderName, builderHashcode);
            HttpLoggingInterceptor httpLogger = new HttpLoggingInterceptor(LOG::debug);
            builder.addInterceptor(httpLogger);
        }

        LOG.trace("{}:{} adding User-Agent interceptor", builderName, builderHashcode);
        builder.addInterceptor((chain) -> {
            Request.Builder reqBuilder = chain.request().newBuilder();
            reqBuilder.removeHeader("User-Agent");
            reqBuilder.addHeader("User-Agent", this.getClass().getName());
            return chain.proceed(reqBuilder.build());
        });

        OkHttpClient client = builder.build();
        LOG.trace("{}:{} built OkHttpClient {}:{}", builderName, builderHashcode,
                  client.getClass().getSimpleName(), toHexString(identityHashCode(client.getClass())));

        return client;
    }

}
