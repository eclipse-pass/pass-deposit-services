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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.utils.ClasspathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resources.SharedResourceUtil;

/**
 * A specialized class used to look up <em>local</em> JSON representations of submission graphs.  Local means that
 * the JSON representations physically reside on the classpath as resources, not in a repository.  As a consequence,
 * the URIs identifying the PASS entities in the graph are opaque.
 * <p>
 * JSON representations for multiple submission graphs reside under the {@code submissions/} resource path, one file per
 * graph.  Each graph is rooted in a
 * <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Submission.md">Submission</a>
 * object, and contains JSON representations of the resources linked by the Submission (e.g. Repositories, Funders,
 * Grants, Files, Publications, etc).  Each submission graph is identified by the URI of the Submission that roots the
 * graph.
 * </p>
 * <p>
 * This class makes it possible to resolve submission graphs using URIs, which provides tests with a clean way of
 * sharing and retrieving submission graphs.  The {@code PassJsonFedoraAdapter} can be used to populate a Fedora
 * repository with sample submission graphs returned by this class.
 * </p>
 * <h3>Implementation notes</h3>
 * <p>
 * This class effectively scans the {@code submissions/} classpath for files ending with the {@code json} extension and
 * builds an index of {@code Submission} JSON objects.  As a developer wishing to add a new Submission graph to the
 * {@code submissions/} resource path, all you need to do is create a JSON file (ending with a {@code json} file
 * extension) containing a Submission and its linked resources.  <strong>However</strong>, the Submission must have a
 * unique URI; it cannot have the same URI as another Submission in the {@code submissions/} classpath.
 * </p>
 * <h3>Minimal example Submission graph</h3>
 * Replicated below is a sample submission graph.  The URI of the submission is {@code fake:submission10}, which is the
 * same URI a developer would use to identify the graph.  Note the JSON objects contained in the graph each have opaque
 * identifiers, and the objects are properly linked to each other according to the
 * <a href="https://github.com/OA-PASS/pass-data-model">PASS data model</a>.
 * To retrieve this graph could invoke a couple different methods depending on the need:
 * <dl>
 *     <dt>{@link #lookupStream(URI)}</dt>
 *     <dd>Get an {@code InputStream} to the Submission graph by the Submission URI.  To read the sample JSON below, you
 *         would invoke {@code lookupStream("fake:submission10")}</dd>
 *     <dt>{@link #asJson(URI)}</dt>
 *     <dd>Get the Submission graph by the Submission URI as a {@code JsonNode}.  For example: {@code
 *         asJson("fake:submission10")}</dd>
 *     <dt>{@link #lookupUri(URI)}</dt>
 *     <dd>Get the classpath resource URI that contains the Submission graph by the Submission URI.  For example:
 *         {@code lookupUri("fake:submission10").toString()} may return {@code file:/submissions/submission10.json}</dd>
 * </dl>
 * To populate a Fedora repository with this sample submission one might use the following:
 * <pre>
 * PassJsonFedoraAdapter passAdapter = .... ;
 * // A Map of local entity URIs to the entity that was created in the repository (effectively allowing you to map from
 * // the local URI of a resource to the remote URI of a resource)
 * HashMap&lt;URI, PassEntity&gt; uploadedEntities = new HashMap&lt;&gt;();
 * passAdapter.jsonToFcrepo(lookupStream("fake:submission10"), uploadedEntities);
 * </pre>
 * Example graph:
 * <pre>
 * [
 *   {
 *     "@id" : "fake:submission10",
 *     "@type" : "Submission",
 *     "source" : "pass",
 *     "submitted" : true,
 *     "submittedDate" : "2017-06-02T00:00:00.000Z",
 *     "aggregatedDepositStatus" : "not-started",
 *     "submissionStatus" : "submitted",
 *     "publication" : "fake:publication1",
 *     "repositories" : [ "fake:repository1" ],
 *     "submitter" : "fake:user1",
 *     "grants" : [ "fake:grant1" ]
 *   },
 *   {
 *     "@id" : "fake:publication1",
 *     "@type" : "Publication",
 *     "title" : "This is the first submission",
 *     "abstract" : "This is a great paper!",
 *     "doi" : "abcdef",
 *     "pmid" : "fedcba",
 *     "journal" : "fake:journal1",
 *     "volume" : "123",
 *     "issue" : "May 2015"
 *   },
 *   {
 *     "@id" : "fake:journal1",
 *     "@type" : "Journal",
 *     "name" : "AAPS PharmSci",
 *     "issns" : [ "issn123", "issn456" ],
 *     "nlmta" : "AAPS PharmSci",
 *     "pmcParticipation" : "A",
 *     "publisher" : ""
 *   },
 *   {
 *     "@id" : "fake:repository1",
 *     "@type" : "Repository",
 *     "name" : "PubMed Central",
 *     "repositoryKey" : "PubMed Central",
 *     "description" : "Contains lots of medical papers",
 *     "url" : "http://example.com",
 *     "formSchema" : "{}"
 *   },
 *   {
 *     "@id" : "fake:grant1",
 *     "@type" : "Grant",
 *     "awardNumber" : "R01EY026617",
 *     "awardStatus" : "active",
 *     "localKey" : "112233",
 *     "projectName" : "Optimal magnification and oculomotor strategies in low vision patients",
 *     "awardDate" : "2017-06-01T00:00:00.000Z",
 *     "startDate" : "2017-05-01T00:00:00.000Z",
 *     "endDate" : "2018-06-01T00:00:00.000Z",
 *     "primaryFunder" : "fake:funder1",
 *     "pi" : "fake:user1"
 *   },
 *   {
 *     "@id" : "fake:funder1",
 *     "@type" : "Funder",
 *     "name" : "National Eye Institute",
 *     "url" : "http://example.com/eyeguys",
 *     "localKey" : "aabbcc",
 *     "policy" : "fake:policy1"
 *   },
 *   {
 *     "@id" : "fake:policy1",
 *     "@type" : "Policy",
 *     "title" : "Be Nice to People With Eyes",
 *     "description" : "We only have eyes for you.",
 *     "policyUrl" : "http://theeyeshaveit.com/policy",
 *     "repositories" : [ "fake:repository1" ],
 *     "institution" : "fake:institution1",
 *     "funder" : "fake:funder1"
 *   },
 *   {
 *     "@id" : "fake:user1",
 *     "@type" : "User",
 *     "username" : "bobsmith",
 *     "firstName" : "Robert",
 *     "middleName" : "Cure",
 *     "lastName" : "Smith",
 *     "displayName" : "Bob Smith",
 *     "email" : "bobsmith@jhu.edu",
 *     "affiliation" : "Johns Hopkins",
 *     "locatorIds" : [ "johnshopkins.edu:hopkinsid:key123", "johnshopkins.edu:jhed:bs1" ],
 *     "orcidId" : "orcid123",
 *     "roles" : [ "submitter", "admin" ]
 *   },
 *   {
 *     "@id": "fake:file1",
 *     "@type": "File",
 *     "name": "test_manuscript",
 *     "uri": "classpath:/submissions/sample1/Sample2.doc",
 *     "description": "Custodial content",
 *     "fileRole": "manuscript",
 *     "mimeType": "application/msword",
 *     "submission": "fake:submission1"
 *   }
 * ]
 * </pre>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see <a href="https://github.com/OA-PASS/pass-data-model">PASS Data Model</a>
 */
public class SubmissionResourceUtil {

    private SubmissionResourceUtil () {
        //never called
    }

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionResourceUtil.class);

    /**
     * Used to filter a {@code Stream<JsonNode>} for Submission JSON objects
     */
    public static final Predicate<JsonNode> SUBMISSION_TYPE_FILTER = node ->
        node.has("@type") && node.get("@type").asText().equals("Submission");

    /**
     * Used to filter a {@code Stream<JsonNode>} for Repository JSON objects
     */
    public static final Predicate<JsonNode> REPOSITORY_TYPE_FILTER = node ->
        node.has("@type") && node.get("@type").asText().equals("Repository");

    /**
     * Answers a {@code Collection} of Submission URIs that are available for use in testing. <p> URIs returned by this
     * method may be supplied as an argument to any public method which accepts a URI. </p>
     *
     * @return A Collection of submission URIs available for testing
     */
    public static Collection<URI> submissionUris() {
        Set<SharedResourceUtil.ElementPathPair> seen = new HashSet<>();
        Set<URI> submissionUris = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        FastClasspathScanner scanner = new FastClasspathScanner(SubmissionResourceUtil.class.getPackage().getName());
        scanner.matchFilenamePattern(".*.json", (classpathElement, relativePath, in, length) -> {
            LOG.trace("Processing match '{}', '{}'", classpathElement, relativePath);
            SharedResourceUtil.ElementPathPair pathPair = new SharedResourceUtil.ElementPathPair(classpathElement,
                                                                                                 relativePath);
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
                                                   "URI.  Found duplicate uri '" + submissionUri + "' in test " +
                                                   "resource '" + relativePath + "'");
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
        Set<SharedResourceUtil.ElementPathPair> seen = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<URL> submissionResource = new AtomicReference<>();
        FastClasspathScanner scanner = new FastClasspathScanner(SubmissionResourceUtil.class.getPackage().getName());
        scanner.matchFilenamePattern(".*\\.json", (cpElt, relativePath, in, length) -> {
            if (SharedResourceUtil.seen(seen, cpElt, relativePath)) {
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
     * Returns the graph of JSON objects for the supplied Submission URI.
     *
     * @param submissionUri the URI uniquely identifying the Submission
     * @return the JSON graph with the Submission object as the root node
     */
    public static JsonNode asJson(URI submissionUri) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode nodeTree = null;
        try {
            JsonParser parser = mapper.getFactory().createParser(lookupStream(submissionUri));
            nodeTree = mapper.readTree(parser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return nodeTree;
    }

    /**
     * Serializes the supplied JSON node and its children, and returns an {@code InputStream} to the bytes.
     *
     * @param n the JSON node to be serialized
     * @return the {@code InputStream} to the bytes
     */
    public static InputStream toInputStream(JsonNode n) {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes = new byte[0];
        try {
            bytes = objectMapper.writeValueAsBytes(n);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new ByteArrayInputStream(bytes);
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
        InputStream jsonStream = SubmissionResourceUtil.class.getResourceAsStream(jsonResource);

        if (jsonStream == null) {
            throw new RuntimeException("Unable to resolve PASS entities for name: '" + name + "', " + "variation: '"
                                       + variation + "'; resource '" + jsonResource + "' is missing or cannot be read" +
                                       ".");
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

}
