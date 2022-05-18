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
package org.dataconservancy.deposit.util.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.MessageDigestCalculatingInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * These tests show that the {@link EncodingClassPathResource} can properly resolve class path resources that contain
 * spaces or or characters that are forbidden in URIs.
 * <p>
 * The issues are esoteric, but this class primarily exists to support test infrastructure in Deposit Services.  Mock
 * submissions are created from JSON representations, and so mock File objects serialized as JSON must support
 * class path references to byte streams.  The class path reference to a byte stream <em>must</em> be a valid URI
 * (this is enforced when Jackson maps the JSON to a File).
 * </p>
 * <p>
 * To support tests with Files containing spaces, URIs for these files must be encoded (e.g. '%20' for the space
 * character), but the Spring Resource framework doesn't handle this well.  The Spring {@code ClassPathResource} cannot
 * resolve class path resource strings that are encoded (arguably correct behavior).  The {@link
 * EncodingClassPathResource} acts like a Spring {@code ClassPathResource}, but is able to resolve resource paths that
 * are encoded.
 * </p>
 * <p>
 * <em>If you have the luxury</em>, use Spring's {@code UrlResource} instead.  Unfortunately, the test infrastructure in
 * Deposit Services does not lend itself to use of {@code UrlResource} (because the relative class path resource needs
 * to be resolved to an absolute URL before constructing the {@code UrlResource}).
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class EncodingClassPathResourceTest {

    private static final Logger LOG = LoggerFactory.getLogger(EncodingClassPathResourceTest.class);

    private static final String SPACE_RESOURCE = "ilford panf.pdf";

    private static final String SPACE_RESOURCE_CHECKSUM = "8fdab9d9de5f63bc51b8c732eb471940d7a521da";

    private static final String UNDERBAR_RESOURCE = "ilford_panf.pdf";

    private static final String UNDERBAR_RESOURCE_CHECKSUM = "b99e2a38b326ca0e38a6fa2f1d1bcbdd74a222ad";

    private static final String PERCENT_RESOURCE_DOUBLE_ENCODED = "ilford%2520panf.pdf";

    private static final String PERCENT_RESOURCE = "ilford%20panf.pdf";

    private static final String PERCENT_RESOURCE_CHECKSUM = "0a4d46e8750080f08e45e581f06a1ddd7a5bc4b9";

    /**
     * Attempt to resolve a classpath resource named with a space.
     *
     * The proper thing to do when using EncodingClassPathResource is to encode the space prior to constructing the
     * ECPR, but if the caller mistakenly supplies the unencoded version, the resource should still be resolvable.  The
     * URL returned by {@link EncodingClassPathResource#getURL()} may not be what the caller expects.
     *
     * This test demonstrates that supplying unencoded class path resources is OK, as long as the caller doesn't have
     * any expectations of the URL returned by ECPR.
     */
    @Test
    public void spaceResourceRootClasspath() throws Exception {
        // Reference the file named 'ilford panf.pdf' on the filesystem.
        // We *should* encode this before constructing the EncodingClassPathResource, but let's see what happens

        String resourcePath = String.format("/%s", SPACE_RESOURCE);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath.substring(1), "ilford%20panf.pdf", SPACE_RESOURCE_CHECKSUM);
    }

    /**
     * Attempt to resolve a classpath resource named with a space.  Properly encode the resource path before
     * constructing the ECPR.
     *
     * Supplying the encoded class path resource is OK, and the caller can expect the encoded resource path to be
     * present in the returned URL.
     */
    @Test
    public void percentResourceRootClasspath() throws Exception {
        // Reference the file named 'ilford panf.pdf' on the filesystem.
        // Encode the resource path when constructing EncodingClassPathResource

        String resourcePath = String.format("/%s", PERCENT_RESOURCE);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath.substring(1), PERCENT_RESOURCE, SPACE_RESOURCE_CHECKSUM);
    }

    /**
     * Attempt to resolve a classpath resource that does not need to be encoded prior to constructing the ECPR.
     *
     * ECPR acts normally, just like any other resource.
     */
    @Test
    public void underbarResourceRootClasspath() throws Exception {
        // Reference the file named 'ilford_panf.pdf' on the filesystem.
        // No need to encode the resource prior to creating the EncodingClassPathResource, as there are no illegal
        // characters in the resource name

        String resourcePath = String.format("/%s", UNDERBAR_RESOURCE);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath.substring(1), UNDERBAR_RESOURCE, UNDERBAR_RESOURCE_CHECKSUM);
    }

    /**
     * Attempt to resolve a classpath resource that is literally named with a `%20` in its name.  The resource path must
     * be encoded prior to creating the ECPR, encoding the '%' in the name.
     */
    @Test
    public void doubleEncodedResourceRootClasspath() throws Exception {
        // Reference the file named 'ilford%20panf.pdf' on the filesystem. (a file with a literal '%20' in the name)
        // Encode the resource path when constructing EncodingClassPathResource

        String resourcePath = String.format("/%s", PERCENT_RESOURCE_DOUBLE_ENCODED);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath.substring(1), PERCENT_RESOURCE_DOUBLE_ENCODED,
                       PERCENT_RESOURCE_CHECKSUM);
    }

    /**
     * Attempt to resolve a classpath resource named with a space.
     *
     * The proper thing to do when using EncodingClassPathResource is to encode the space prior to constructing the
     * ECPR, but if the caller mistakenly supplies the unencoded version, the resource should still be resolvable.  The
     * URL returned by {@link EncodingClassPathResource#getURL()} may not be what the caller expects.
     *
     * This test demonstrates that supplying unencoded class path resources is OK, as long as the caller doesn't have
     * any expectations of the URL returned by ECPR.
     */
    @Test
    public void spaceResourceRelativeClasspath() throws Exception {
        // Reference the file named 'ilford panf.pdf' on the filesystem.
        // We *should* encode this before constructing the EncodingClassPathResource, but let's see what happens

        String resourcePath = String.format("%s/%s",
                                            EncodingClassPathResource.class.getPackage().getName().replace(".", "/"),
                                            SPACE_RESOURCE);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath, "ilford%20panf.pdf", SPACE_RESOURCE_CHECKSUM);
    }

    /**
     * Attempt to resolve a classpath resource named with a space.  Properly encode the resource path before
     * constructing the ECPR.
     *
     * Supplying the encoded class path resource is OK, and the caller can expect the encoded resource path to be
     * present in the returned URL.
     */
    @Test
    public void percentResourceRelativeClasspath() throws Exception {
        // Reference the file named 'ilford panf.pdf' on the filesystem.
        // Encode the resource path when constructing EncodingClassPathResource

        String resourcePath = String.format("%s/%s",
                                            EncodingClassPathResource.class.getPackage().getName().replace(".", "/"),
                                            PERCENT_RESOURCE);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath, PERCENT_RESOURCE, SPACE_RESOURCE_CHECKSUM);
    }

    /**
     * Attempt to resolve a classpath resource that does not need to be encoded prior to constructing the ECPR.
     *
     * ECPR acts normally, just like any other resource.
     */
    @Test
    public void underbarResourceRelativeClasspath() throws Exception {
        // Reference the file named 'ilford_panf.pdf' on the filesystem.
        // No need to encode the resource prior to creating the EncodingClassPathResource, as there are no illegal
        // characters in the resource name

        String resourcePath = String.format("%s/%s",
                                            EncodingClassPathResource.class.getPackage().getName().replace(".", "/"),
                                            UNDERBAR_RESOURCE);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath, UNDERBAR_RESOURCE, UNDERBAR_RESOURCE_CHECKSUM);
    }

    /**
     * Attempt to resolve a classpath resource that is literally named with a `%20` in its name.  The resource path must
     * be encoded prior to creating the ECPR, encoding the '%' in the name.
     */
    @Test
    public void doubleEncodedResourceRelativeClasspath() throws Exception {
        // Reference the file named 'ilford%20panf.pdf' on the filesystem. (a file with a literal '%20' in the name)
        // Encode the resource path when constructing EncodingClassPathResource

        String resourcePath = String.format("/%s", PERCENT_RESOURCE_DOUBLE_ENCODED);
        EncodingClassPathResource underTest = new EncodingClassPathResource(resourcePath);
        verifyResource(underTest, resourcePath.substring(1), PERCENT_RESOURCE_DOUBLE_ENCODED,
                       PERCENT_RESOURCE_CHECKSUM);
    }

    private static void verifyResource(EncodingClassPathResource underTest, String expectedPath,
                                       String expectedUrlPath, String expectedChecksum) throws Exception {
        try (InputStream in = underTest.getInputStream()) {
            assertNotNull(in);
            try (MessageDigestCalculatingInputStream digestIn = new MessageDigestCalculatingInputStream(in, "SHA-1")) {
                IOUtils.copy(digestIn, new NullOutputStream());
                MessageDigest md = digestIn.getMessageDigest();
                assertEquals(expectedChecksum, Hex.encodeHexString(md.digest()));
            }
        }

        assertNotNull(underTest.getURL());
        assertNotNull(underTest.getPath());
        assertNotNull(underTest.resolveURL());
        assertNotNull(underTest.getFilename());
        assertNotNull(underTest.getFile());

        assertEquals(expectedPath, underTest.getPath());

        LOG.debug("Expected path: '{}' getURL().getPath(): '{}'", expectedPath, underTest.getURL().getPath());
        assertTrue(underTest.getURL().getPath().endsWith(expectedUrlPath));

        String expectedName = expectedPath;
        if (expectedPath.contains("/")) {
            expectedName = expectedPath.substring(expectedPath.lastIndexOf("/") + 1);
        }

        assertTrue(underTest.getFilename().endsWith(expectedName));
    }
}