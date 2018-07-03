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
package submissions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.utils.ClasspathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SharedResourceUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SharedResourceUtil.class);

    private static final Predicate<JsonNode> SUBMISSION_TYPE_FILTER = node ->
            node.has("@type") && node.get("@type").asText().equals("Submission");

    /**
     * Answers a {@code Collection} of Submission URIs that are available for use in testing. <p> URIs returned by this
     * method may be supplied as an argument to any public method which accepts a URI. </p>
     *
     * @return A Collection of submission URIs available for testing
     */
    public static Collection<URI> submissionUris() {
        Set<ElementPathPair> seen = new HashSet<>();
        Set<URI> submissionUris = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        FastClasspathScanner scanner = new FastClasspathScanner(SharedResourceUtil.class.getPackage().getName());
        scanner.matchFilenamePattern(".*.json", (classpathElement, relativePath, in, length) -> {
            LOG.trace("Processing match '{}', '{}'", classpathElement, relativePath);
            ElementPathPair pathPair = new ElementPathPair(classpathElement, relativePath);
            if (seen.contains(pathPair)) {
                // We have already scanned classpath element/path pair; it probably appears on the classpath twice.
                return;
            }

            seen.add(pathPair);

            JsonNode jsonNode = mapper.readTree(in);
            Stream<JsonNode> nodeStream = asStream(jsonNode);

            JsonNode submissionNode = getSubmissionNode(nodeStream);

            URI submissionUri = URI.create(submissionNode.get("@id").asText());

            if (submissionUris.contains(submissionUri)) {
                throw new IllegalArgumentException("Each test submission resource must have a unique submission " +
                        "URI.  Found duplicate uri '" + submissionUri + "' in test resource '" + relativePath + "'");
            }

            submissionUris.add(submissionUri);
        });

        scanner.scan();

        assertTrue("Did not find any test submission resources.", submissionUris.size() > 0);

        return submissionUris;
    }

    /**
     * Convenience method which opens an {@code InputStream} to the classpath resource containing the {@code
     * submissionUri}.
     *
     * @param submissionUri the URI of a submission that is present in one of the test resources
     * @return an InputStream to the classpath resource that contains the submission
     */
    public static InputStream lookupStream(URI submissionUri) {
        URI resourceUri = lookupUri(submissionUri);
        assertNotNull("Test resource for '" + submissionUri + "' not found.", resourceUri);

        try {
            return resourceUri.toURL().openStream();
        } catch (IOException e) {
            throw new RuntimeException("Unable to open an InputStream to classpath resource " + resourceUri);
        }
    }

    /**
     * Looks up the URI of the <em>test resource</em> that contains the JSON for the <em>submission URI</em>. <p> For
     * example, looking up Submission URI {@code fake:submission1} would return the URI of the test resource that
     * contains {@code fake:submission1}: {@code file:/submissions/sample1.json} </p>
     *
     * @param submissionUri the URI of a submission that is present in one of the test resources
     * @return the URI of the test resource that contains the {@code submissionUri}
     */
    public static URI lookupUri(URI submissionUri) {
        Set<ElementPathPair> seen = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<URL> submissionResource = new AtomicReference<>();
        FastClasspathScanner scanner = new FastClasspathScanner(SharedResourceUtil.class.getPackage().getName());
        scanner.matchFilenamePattern(".*\\.json", (cpElt, relativePath, in, length) -> {
            if (seen(seen, cpElt, relativePath)) {
                return;
            }
            JsonNode jsonNode = mapper.readTree(in);
            Stream<JsonNode> nodeStream = asStream(jsonNode);
            JsonNode submissionNode = getSubmissionNode(nodeStream);

            URI candidateUri = URI.create(submissionNode.get("@id").asText());
            if (submissionUri.equals(candidateUri)) {
                assertNull("Found duplicate submission URI '" + submissionUri + "' in test resource '" + cpElt + "', " +
                        "'" + relativePath + "'", submissionResource.get());
                submissionResource.set(ClasspathUtils.getClasspathResourceURL(cpElt, relativePath));
            }
        });

        scanner.scan();

        assertNotNull("Unable to find a JSON submission resource containing submission uri '" + submissionUri + "'",
                submissionResource.get());

        try {
            return submissionResource.get().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Searches the elements of the {@code nodeStream} for the {@code JsonNode} containing the Submission (identified
     * with an {@code "@type" : "Submission"})
     *
     * @param nodeStream the test JSON, represented as {@code Stream} of {@code JsonNode}s
     * @return the {@code JsonNode} containing the submission
     */
    public static JsonNode getSubmissionNode(Stream<JsonNode> nodeStream) {
        List<JsonNode> submissionNodes = nodeStream.filter(SUBMISSION_TYPE_FILTER).collect(Collectors.toList());

        assertEquals("Exactly one Submission node must be found in the test submission resource, " + "but found: " +
                submissionNodes.size(), 1, submissionNodes.size());

        JsonNode submissionNode = submissionNodes.get(0);
        assertTrue("Submission node must have a value for '@id'", submissionNode.has("@id"));
        assertFalse("Submission node must have a value for '@id', cannot be null", submissionNode.get("@id").isNull());

        return submissionNode;
    }

    /**
     * Provides a {@code Stream} of child elements of {@code jsonNode}.
     *
     * @param jsonNode a {@code JsonNode} that may have child elements
     * @return a {@code Stream} of child elements
     */
    public static Stream<JsonNode> asStream(JsonNode jsonNode) {
        Iterable<JsonNode> iterable = jsonNode::elements;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static InputStream toStream(String name, String variation) {
        String jsonResource = asResourcePath(name, variation);
        InputStream jsonStream = SharedResourceUtil.class.getResourceAsStream(jsonResource);

        if (jsonStream == null) {
            throw new RuntimeException("Unable to resolve PASS entities for name: '" + name + "', " + "variation: '"
                    + variation + "'; resource '" + jsonResource + "' is missing or cannot be read.");
        }
        return jsonStream;
    }

    private static String asResourcePath(String name, String variation) {
        String jsonResource;
        if (variation != null) {
            jsonResource = String.format("/submissions/%s-%s.json", name, variation);
        } else {
            jsonResource = String.format("/submissions/%s.json", name);
        }
        return jsonResource;
    }

    /**
     * Convenience method which checks for the presence of the {@code classpathElement}/{@code relativePath} pair in the
     * {@code seen} {@code Set}.
     *
     * @param seen a {@code Set} which contains all of the {@code ElementPathPair}s seen so far
     * @param classpathElement a classpath element, which combined with a relative path, may have been seen
     *         before
     * @param relativePath a relative path, when combined with a classpath element, may have been seen before
     * @return true if the {@code classpathElement}/{@code relativePath} has been seen before
     * @see ElementPathPair
     */
    private static boolean seen(Set<ElementPathPair> seen, java.io.File classpathElement, String relativePath) {
        ElementPathPair pathPair = new ElementPathPair(classpathElement, relativePath);
        if (seen.contains(pathPair)) {
            return true;
        }
        seen.add(pathPair);
        return false;
    }


    /**
     * Pairs a Classpath element with the relative path of the Class for file matched by a {@link FastClasspathScanner}
     * scan. <p> For some reason, some classpath elements can appear on the classpath twice (e.g. IDEA and Maven,
     * actually, put `target/test-classes` on the classpath twice).  The {@link ElementPathPair} is used to insure that
     * the same Classpath resource is not considered twice.  This allows the matching logic to warn users if a duplicate
     * Submission URI is found. </p>
     */
    private static class ElementPathPair {
        private java.io.File classpathElement;
        private String relativePath;

        public ElementPathPair(java.io.File classpathElement, String relativePath) {
            this.classpathElement = classpathElement;
            this.relativePath = relativePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            ElementPathPair that = (ElementPathPair) o;

            if (classpathElement != null ? !classpathElement.equals(that.classpathElement) : that.classpathElement !=
                    null)
                return false;
            return relativePath != null ? relativePath.equals(that.relativePath) : that.relativePath == null;
        }

        @Override
        public int hashCode() {
            int result = classpathElement != null ? classpathElement.hashCode() : 0;
            result = 31 * result + (relativePath != null ? relativePath.hashCode() : 0);
            return result;
        }
    }

}
