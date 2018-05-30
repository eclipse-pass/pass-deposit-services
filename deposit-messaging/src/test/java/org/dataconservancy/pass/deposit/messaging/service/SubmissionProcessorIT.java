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

import org.dataconservancy.nihms.builder.fs.FcrepoModelBuilder;
import org.dataconservancy.nihms.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.Condition;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.COMPLETE;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.NOT_STARTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "spring.jms.listener.auto-startup=false" })
public class SubmissionProcessorIT {

    private static final String SUBMISSION_RESOURCES = "SubmissionProcessorIT.json";

    private static final String EXPECTED_REPO_NAME = "JScholarship";

    private Submission submission;

    @Autowired
    @Qualifier("submissionProcessor")
    private SubmissionProcessor underTest;

    @Autowired
    private PassClient passClient;

    /**
     * Populates Fedora with a Submission, as if it was submitted interactively by a user of the PASS UI.
     *
     * @throws Exception
     */
    @Before
    public void createSubmission() throws Exception {
        PassJsonFedoraAdapter passAdapter = new PassJsonFedoraAdapter();

        // Upload sample data to Fedora repository to get its Submission URI.
        InputStream is = this.getClass().getResourceAsStream(SUBMISSION_RESOURCES);
        assertNotNull("Unable to resolve classpath resource " + SUBMISSION_RESOURCES, is);

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
        assertEquals(Boolean.TRUE, submission.getSubmitted());
        assertEquals(NOT_STARTED, submission.getAggregatedDepositStatus());

        // no Deposits pointing to the Submission
        assertTrue("Unexpected incoming links to " + submissionUri,
                getDepositUris(submission, passClient).isEmpty());

        // JScholarship repository ought to exist
        assertNotNull(submission.getRepositories());
        assertTrue(submission.getRepositories().stream()
                .map(uri -> (Repository)uriMap.get(uri))
                .anyMatch(repo -> repo.getName().equals(EXPECTED_REPO_NAME)));

    }

    @Test
    public void smokeSubmission() throws Exception {
        underTest.accept(submission);

        // After successfully processing a submission to JScholarship we should observe:

        // 1. Deposit resources created for each Repository associated with the Submission

        Condition<Collection<URI>> depositCreated = new Condition<>(() ->
                getDepositUris(submission, passClient), "expectedDepositCount");

        assertTrue("SubmissionProcessor did not create the expected number of Deposit resources.",
                depositCreated.awaitAndVerify(depositUris ->
                        submission.getRepositories().size() == depositUris.size()));

        // 2. The status of each Deposit should be 'ACCEPTED'

        Condition<Collection<URI>> allDepositsAccepted = new Condition<>(() ->
                getDepositUris(submission, passClient), "expectedDepositStatus");

        assertTrue("Unexpected number of successful Deposit resources",
                allDepositsAccepted.awaitAndVerify(depositUris ->
                        depositUris.size() == submission.getRepositories().size() &&
                        depositUris.stream().allMatch(uri ->
                                passClient.readResource(uri, Deposit.class).getDepositStatus() == ACCEPTED)));

        // 3. A RepositoryCopy created for each Deposit, with a copy status of 'COMPLETE'

        Condition<Collection<URI>> repoCopiesComplete = new Condition<>(() ->
                getDepositUris(submission, passClient).stream()
                        .map(depUri ->
                                passClient.readResource(depUri, Deposit.class).getRepositoryCopy()).collect(toSet()),
                "repoCopiesComplete");

        assertTrue("Unexpected number of successful RepositoryCopy resources",
                repoCopiesComplete.awaitAndVerify(repoCopyUris ->
                    repoCopyUris.size() == submission.getRepositories().size() &&
                            repoCopyUris.stream().allMatch(uri ->
                                    passClient.readResource(uri, RepositoryCopy.class).getCopyStatus() == COMPLETE)));
    }

    private static Collection<URI> getDepositUris(Submission submission, PassClient passClient) {
        Map<String, Collection<URI>> incoming = passClient.getIncoming(submission.getId());
        return incoming.get("submission").stream().filter(uri -> {
            try {
                passClient.readResource(uri, Deposit.class);
                return true;
            } catch (Exception e) {
                return false;
            }
        }).collect(toSet());
    }
}
