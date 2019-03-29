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
import java.util.stream.Collectors;

import static okhttp3.Credentials.basic;
import static okhttp3.RequestBody.create;
import static org.dataconservancy.pass.deposit.integration.shared.SubmissionUtil.getDepositUris;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.NOT_STARTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
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

    protected Submission submission;

    protected Map<URI, PassEntity> submissionResources;

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
        okHttp = builder.build();
    }

    /**
     * Populates Fedora with a Submission, as if it was submitted interactively by a user of the PASS UI.
     *
     * @throws Exception
     */
    @Before
    public void createSubmission() throws Exception {
        PassJsonFedoraAdapter passAdapter = new PassJsonFedoraAdapter();
        HashMap<URI, PassEntity> uriMap = new HashMap<>();
        URI submissionUri;

        // Upload sample data to Fedora repository to get its Submission URI.
        try (InputStream is = getSubmissionResources()) {
            submissionUri = passAdapter.jsonToFcrepo(is, uriMap);
        }

        // Find the Submission entity that was uploaded
        submission = (Submission) uriMap
                .values()
                .stream()
                .filter((entity) -> entity.getId().equals(submissionUri))
                .filter((entity) -> entity instanceof Submission)
                .findAny()
                .orElseThrow(() ->
                        new RuntimeException("Missing expected Submission; it was not added to the repository"));

        assertNotNull("Missing expected Submission; it was not added to the repository.", submission);

        // verify state of the initial Submission
        assertEquals(Submission.Source.PASS, submission.getSource());
        assertEquals(NOT_STARTED, submission.getAggregatedDepositStatus());

        // no Deposits pointing to the Submission
        assertTrue("Unexpected incoming links to " + submissionUri,
                getDepositUris(submission, passClient).isEmpty());

        submissionResources = uriMap;
    }

    protected abstract InputStream getSubmissionResources();

    protected void triggerSubmission(URI submissionUri) {
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
     * @param filter filters for Deposit resources with a desired state (e.g., a certain deposit status)
     * @return the Condition
     */
    protected Condition<Set<Deposit>> depositsForSubmission(URI submissionUri, int expectedCount,
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
                            filter.test(deposit, passClient.readResource(deposit.getRepository(), Repository.class)))
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

    private static boolean travis() {
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
     * Looks for, and returns, the Repository attached to the supplied Submission with the specified name.
     *
     * @param submission
     * @param repositoryName
     * @return
     */
    protected Repository repositoryForName(Submission submission, String repositoryName) {
        return submission
                .getRepositories()
                .stream()
                .map(uri -> passClient.readResource(uri, Repository.class))
                .filter(repo -> repositoryName.equals(repo.getName()))
                .findAny()
                .orElseThrow(() ->
                        new RuntimeException("Missing Repository with name " + repositoryName + " for Submission " +
                                submission.getId()));
    }

    /**
     * Looks for, and returns, the Deposit attached to the supplied Submission that references the specified
     * Repository.
     *
     * @param submission
     * @param repositoryUri
     * @return
     */
    protected Deposit depositForRepositoryUri(Submission submission, URI repositoryUri) {
        Collection<URI> depositUris = getDepositUris(submission, passClient);
        return depositUris
                .stream()
                .map(uri -> passClient.readResource(uri, Deposit.class))
                .filter(deposit -> repositoryUri.equals(deposit.getRepository()))
                .findAny()
                .orElseThrow(() ->
                        new RuntimeException("Missing Deposit for Repository " + repositoryUri + " on Submission " +
                                submission.getId()));
    }
}
