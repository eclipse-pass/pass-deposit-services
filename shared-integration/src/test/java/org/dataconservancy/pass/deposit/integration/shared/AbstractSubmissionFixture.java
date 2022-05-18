/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.integration.shared;

import static okhttp3.Credentials.basic;
import static okhttp3.RequestBody.create;
import static org.dataconservancy.pass.deposit.integration.shared.SubmissionUtil.getDepositUris;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.NOT_STARTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Provides convenience methods for depositing Submission graphs in Fedora.  <strong>N.B.</strong> this test fixture
 * works best coupled with the {@code SpringJunit4ClassRunner}.  If this fixture is <em>not</em> using the Spring
 * Runner, then the caller <em>must</em> set the following properties for proper operation:
 * <ul>
 *     <li>{@link #fcrepoBaseUrl}, {@link #fcrepoUser}, {@link #fcrepoPass}</li>
 *     <li>{@link #contextUri}</li>
 *     <li>{@link #passClient}</li>
 *     <li>{@link #okHttp} always supplied by {@link #setUpOkHttp()} (this client must have an {@code Interceptor}
 *         configured for authenticating to Fedora)</li>
 * </ul>
 * <p>
 * When <em>using</em> the Spring Runner, the dependencies are either {@code @Autowired} or supplied by {@code @Value}.
 * </p>
 * <ul>
 *      <li>{@link #fcrepoBaseUrl}, {@link #fcrepoUser}, {@link #fcrepoPass} supplied by the respective {@code @Value}
 *           expressions {@code ${pass.fedora.baseurl}}, {@code ${pass.fedora.user}},
 *           {@code ${pass.fedora.password}}</li>
 *      <li>{@link #contextUri} supplied by the {@code @Value} expression {@code ${pass.jsonld.context}}</li>
 *      <li>{@link #passClient} supplied by {@code @Autowired}</li>
 *      <li>{@link #okHttp} always supplied by {@link #setUpOkHttp()} (this client must have an {@code Interceptor}
 *          configured for authenticating to Fedora)</li>
 *  </ul>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractSubmissionFixture {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static Logger LOG = LoggerFactory.getLogger(AbstractSubmissionFixture.class);

    private static final String MERGE_PATCH = "application/merge-patch+json";

    private static final String SUBMIT_TRUE_PATCH = "" +
                                                    "{\n" + "   \"@id\": \"%s\",\n" +
                                                    "   \"@context\": \"%s\",\n" +
                                                    "   \"submitted\": \"true\",\n" +
                                                    "   \"submissionStatus\": \"submitted\"\n" +
                                                    "}";

    private static final long TRAVIS_CONDITION_TIMEOUT_MS = 180 * 1000;

    @Autowired
    protected PassClient passClient;

    protected OkHttpClient okHttp;

    @Value("${pass.fedora.baseurl}")
    protected String fcrepoBaseUrl;

    @Value("${pass.fedora.user}")
    protected String fcrepoUser;

    @Value("${pass.fedora.password}")
    protected String fcrepoPass;

    @Value("${pass.jsonld.context}")
    protected String contextUri;

    /**
     * An OkHttp client used to trigger a submission.
     * <p>
     * The {@code SubmissionProcessor} will drop messages from the {@link #passClient} (so it doesn't respond to changes
     * it makes to resources).  This OkHttpClient does not share the user agent string of the {@link #passClient}, so it
     * can be used to modify a Submission, and have its messages processed by {@code SubmissionProcessor}.
     * </p>
     *
     * @throws Exception
     */
    @Before
    public void setUpOkHttp() throws Exception {
        okHttp = fcrepoClient(fcrepoUser, fcrepoPass);
    }

    /**
     * Populates Fedora with a Submission graph serialized as JSON, as if it was submitted interactively by a user of
     * the PASS UI.
     * <p>
     * The submission graph supplied by the {@code InputStream} must satisfy the following conditions, or an {@code
     * AssertionError} will be thrown:
     * </p>
     * <ul>
     *     <li>The {@code Submission.source} must be {@code Submission.Source.PASS}</li>
     *     <li>The {@code Submission.aggregatedDepositStatus} must be {@code
     *         Submission.AggregatedDepositStatus.NOT_STARTED}</li>
     * </ul>
     */
    public Map<URI, PassEntity> createSubmission(InputStream submissionGraph) {
        PassJsonFedoraAdapter passAdapter = new PassJsonFedoraAdapter();
        HashMap<URI, PassEntity> uriMap = new HashMap<>();

        // Upload sample data to Fedora repository to get its Submission URI.
        URI submissionUri = passAdapter.jsonToFcrepo(submissionGraph, uriMap).getId();

        // Find the Submission entity that was uploaded
        Submission submission = findSubmission(uriMap);

        // verify state of the initial Submission
        assertEquals("Submission must have a Submission.source = Submission.Source.PASS",
                     Submission.Source.PASS, submission.getSource());
        assertEquals("Submission must have a Submission.aggregatedDepositStatus = " +
                     "Submission.AggregatedDepositStatus.NOT_STARTED",
                     NOT_STARTED, submission.getAggregatedDepositStatus());

        // no Deposits pointing to the Submission
        assertTrue("Unexpected incoming links to " + submissionUri,
                   getDepositUris(submission, passClient).isEmpty());

        return uriMap;
    }

    /**
     * Returns the {@code Submission} from a {@code Map} of entities that represents the graph of entities linked to
     * by the {@code Submission}.
     * <p>
     * The supplied {@code Map} must contain exactly one {@code Submission}, or an {@code AssertionError} is thrown.
     * </p>
     *
     * @param entities a map of entities that comprise a graph rooted in the {@code Submission}
     * @return the {@code Submission}
     * @throws AssertionError if zero or more than one {@code Submission} is contained in the supplied entity {@code
     *                        Map}
     */
    public static Submission findSubmission(Map<URI, PassEntity> entities) {
        Predicate<PassEntity> submissionFilter = (entity) -> entity instanceof Submission;

        long count = entities
            .values()
            .stream()
            .filter(submissionFilter)
            .count();

        assertEquals("Found " + count + " Submission resources, expected exactly 1", count, 1);

        return (Submission) entities
            .values()
            .stream()
            .filter(submissionFilter)
            .findAny()
            .get();
    }

    /**
     * Flips the {@code Submission.submitted} flag to {@code true}, at which point Deposit Services will process
     * the {@code Submission}.  The {@code Submission} should be considered read-only to clients external to the Deposit
     * Services runtime after invoking this method.   This means <strong>prior</strong> to invoking this method, the
     * {@code Submission} should be fully linked to all necessary resources, including {@link
     * org.dataconservancy.pass.model.File file} content and any {@link Repository repositories}.  The {@link
     * Submission#getSource() source} and {@link Submission#getAggregatedDepositStatus() deposit status} should also be
     * set properly.  If the Ember UI is being emulated then the proper values are:
     * <dl>
     *     <dt>source</dt>
     *     <dd>{@link Submission.Source#PASS}</dd>
     *     <dt>deposit status</dt>
     *     <dd>{@link Submission.AggregatedDepositStatus#NOT_STARTED}</dd>
     * </dl>
     * Note: Deposit Services doesn't care about, or examine, {@link Submission.SubmissionStatus}.
     *
     * @param submissionUri the URI of a {@code Submission} that is ready for processing
     */
    public void triggerSubmission(URI submissionUri) {
        String body = String.format(SUBMIT_TRUE_PATCH, submissionUri, contextUri);

        Request post = new Request.Builder()
            .addHeader("Content-Type", MERGE_PATCH)
            .method("PATCH", create(MediaType.parse(MERGE_PATCH), body))
            .url(submissionUri.toString())
            .build();

        try (Response response = okHttp.newCall(post).execute()) {
            int expected = 204;
            assertEquals("Triggering 'submitted' flag to 'true' for " + submissionUri + " failed.  " +
                         "Expected " + expected + ", got " + response.code() + " (" + response.message() + ")",
                         expected, response.code());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Answers a Condition that will await the creation of {@code expectedCount} {@code Deposit} resources that meet
     * the requirements of the supplied {@code filter}.
     *
     * @param submissionUri the URI of the Submission
     * @param expectedCount the number of Deposit resources expected for the Submission (normally equal to the number of
     *                      Repository resources present on the Submission)
     * @param filter        filters for Deposit resources with a desired state (e.g., a certain deposit status)
     * @return the Condition
     */
    public Condition<Set<Deposit>> depositsForSubmission(URI submissionUri, int expectedCount,
                                                         BiPredicate<Deposit, Repository> filter) {
        Callable<Set<Deposit>> deposits = () -> {
            Set<URI> depositUris = passClient.findAllByAttributes(Deposit.class, new HashMap<String, Object>() {
                {
                    put("submission", submissionUri.toString());
                }
            });

            return depositUris.stream()
                              .map(uri -> passClient.readResource(uri, Deposit.class))
                              .filter(deposit ->
                                          filter.test(deposit, passClient.readResource(deposit.getRepository(),
                                                                                       Repository.class)))
                              .collect(Collectors.toSet());
        };

        Function<Set<Deposit>, Boolean> verification = (depositSet) -> depositSet.size() == expectedCount;

        String name = String.format("Searching for %s Deposits for Submission URI %s", expectedCount, submissionUri);

        Condition<Set<Deposit>> condition = new Condition<>(deposits, verification, name);

        if (travis()) {
            LOG.info("Travis detected.");
            if (condition.getTimeoutThresholdMs() < TRAVIS_CONDITION_TIMEOUT_MS) {
                LOG.info("Setting Condition timeout to {} ms", TRAVIS_CONDITION_TIMEOUT_MS);
                condition.setTimeoutThresholdMs(TRAVIS_CONDITION_TIMEOUT_MS);
            }
        }

        return condition;
    }

    /**
     * Looks for, and returns, the Repository attached to the supplied Submission with the specified name.
     *
     * @param submission
     * @param repositoryName
     * @return the Repository
     * @throws RuntimeException if the Repository cannot be found
     */
    public Repository repositoryForName(Submission submission, String repositoryName) {
        return submission
            .getRepositories()
            .stream()
            .map(uri -> passClient.readResource(uri, Repository.class))
            .filter(repo -> repositoryName.equals(repo.getName()))
            .findAny()
            .orElseThrow(() ->
                             new RuntimeException(
                                 "Missing Repository with name " + repositoryName + " for Submission " +
                                 submission.getId()));
    }

    /**
     * Looks for, and returns, the Deposit attached to the supplied Submission that references the specified
     * Repository.
     *
     * @param submission
     * @param repositoryUri
     * @return the Deposit
     * @throws RuntimeException if the Deposit cannot be found
     */
    public Deposit depositForRepositoryUri(Submission submission, URI repositoryUri) {
        Collection<URI> depositUris = getDepositUris(submission, passClient);
        return depositUris
            .stream()
            .map(uri -> passClient.readResource(uri, Deposit.class))
            .filter(deposit -> repositoryUri.equals(deposit.getRepository()))
            .findAny()
            .orElseThrow(() ->
                             new RuntimeException(
                                 "Missing Deposit for Repository " + repositoryUri + " on Submission " +
                                 submission.getId()));
    }

    /**
     * Method for determining whether the runtime platform is Travis.  Useful for conditionally extending timeouts.
     *
     * @return true if the runtime platform is the Travis integration environment
     */
    public static boolean travis() {
        if (System.getenv("TRAVIS") != null &&
            System.getenv("TRAVIS").equalsIgnoreCase("true")) {
            return true;
        }

        if (System.getProperty("TRAVIS") != null && System.getProperty("TRAVIS").equalsIgnoreCase("true")) {
            return true;
        }

        if (System.getProperty("travis") != null && System.getProperty("travis").equalsIgnoreCase("true")) {
            return true;
        }

        return false;
    }

    /**
     * Answers an OkHttpClient for communicating with a Fedora repository.  It will automatically supply an {@code
     * Authorization} header on the HTTP response if required.
     *
     * @param fcrepoUser the user to use for authentication to fedora, may be {@code null} if Fedora doesn't require
     *                   authn
     * @param fcrepoPass the password to use for authentication to fedora, may be {@code null} if Fedora doesn't require
     *                   authn
     * @return an OkHttpClient configured for communicating with a Fedora repository
     */
    public OkHttpClient fcrepoClient(String fcrepoUser, String fcrepoPass) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.authenticator((route, response) -> {
            if (response.header("Authorization") != null) {
                return null;
            }
            return response
                .request()
                .newBuilder()
                .header("Authorization", basic(fcrepoUser, fcrepoPass))
                .build();
        });

        return builder.build();
    }
}
