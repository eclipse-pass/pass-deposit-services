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
package org.dataconservancy.pass.deposit.messaging.service;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.dataconservancy.pass.deposit.messaging.service.SubmissionTestUtil.getDepositUris;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.NOT_STARTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractSubmissionIT {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    protected static final String J10P_REPO_NAME = "JScholarship";

    protected static final String PMC_REPO_NAME = "PubMed Central";

    private static final String MERGE_PATCH = "application/merge-patch+json";


    private static final String SUBMIT_TRUE_PATCH = "" +
            "{\n" +
            "   \"@id\": \"%s\",\n" +
            "   \"@context\": \"%s\",\n" +
            "   \"submitted\": \"true\",\n" +
            "   \"submissionStatus\": \"submitted\"\n" +
            "}";

    protected Submission submission;

    protected Map<URI, PassEntity> submissionResources;

    @Autowired
    @Qualifier("submissionProcessor")
    protected SubmissionProcessor underTest;

    @Autowired
    protected PassClient passClient;

    protected OkHttpClient okHttp;

    @Value("${pass.fedora.user}")
    private String fcrepoUser;

    @Value("${pass.fedora.password}")
    private String fcrepoPass;

    @Value("${pass.jsonld.context}")
    private String contextUri;

    /**
     * An OkHttp client used to trigger a submission.  The {@link SubmissionProcessor} will drop messages from the
     * {@link #passClient} (so it doesn't respond to changes it makes to resources).  This OkHttpClient does not share
     * the user agent string of the {@link #passClient}, so it can be used to modify a Submission, and have its messages
     * processed by {@link SubmissionProcessor}.
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
            return response.request().newBuilder()
                    .header("Authorization", Credentials.basic(fcrepoUser, fcrepoPass)).build();
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

        // Upload sample data to Fedora repository to get its Submission URI.
        InputStream is = getSubmissionResources();

        HashMap<URI, PassEntity> uriMap = new HashMap<>();
        URI submissionUri = passAdapter.jsonToFcrepo(is, uriMap);
        is.close();

        // Find the Submission entity that was uploaded
        for (URI key : uriMap.keySet()) {
            PassEntity entity = uriMap.get(key);
            if (entity.getId() == submissionUri) {
                submission = (Submission)entity;
                break;
            }
        }

        assertNotNull("Missing expected Submission; it was not added to the repository.", submission);

        // verify state of the initial Submission
        assertEquals(Submission.Source.PASS, submission.getSource());
        assertEquals(NOT_STARTED, submission.getAggregatedDepositStatus());

        // no Deposits pointing to the Submission
        assertTrue("Unexpected incoming links to " + submissionUri,
                getDepositUris(submission, passClient).isEmpty());

        // JScholarship repository ought to exist
        assertNotNull(submission.getRepositories());
        assertTrue(submission.getRepositories().stream()
                .map(uri -> (Repository)uriMap.get(uri))
                .anyMatch(repo -> repo.getName().equals(J10P_REPO_NAME)));


        submissionResources = uriMap;

    }

    protected abstract InputStream getSubmissionResources();

    protected void triggerSubmission(URI submissionUri) {
        String body = String.format(SUBMIT_TRUE_PATCH, submissionUri, contextUri);

        Request post = new Request.Builder()
                .addHeader("Content-Type", MERGE_PATCH)
                .method("PATCH", RequestBody.create(MediaType.parse(MERGE_PATCH), body))
                .url(submissionUri.toString())
                .build();

        try (Response response = okHttp.newCall(post).execute()) {
            int expected = 204;
            assertEquals("Triggering 'submitted' flag to 'true' for " + submissionUri + " failed.  " +
                    "Expected " + expected + ", got " +
                    response.code() + " (" + response.message() + ")", expected, response.code());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
