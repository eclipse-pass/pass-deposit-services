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

import org.dataconservancy.pass.deposit.messaging.support.Condition;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.junit.Test;
import submissions.SharedResourceUtil;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Objects;

import static java.util.stream.Collectors.toSet;
import static org.dataconservancy.pass.deposit.messaging.service.SubmissionTestUtil.getDepositUris;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.SUBMITTED;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.COMPLETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionProcessorIT extends AbstractSubmissionIT {

    private static final URI SUBMISSION_RESOURCES = URI.create("fake:submission1");

    @Override
    protected InputStream getSubmissionResources() {
        return SharedResourceUtil.lookupStream(SUBMISSION_RESOURCES);
    }

    @Test
    public void smokeSubmission() throws Exception {
        underTest.accept(submission);

        // After successfully processing a submission to JScholarship we should observe:

        // 1. Deposit resources created for each Repository associated with the Submission

        Condition<Collection<URI>> depositUrisCondition = new Condition<>(() ->
                getDepositUris(submission, passClient), "expectedDepositCount");

        assertTrue("SubmissionProcessor did not create the expected number of Deposit resources.",
                depositUrisCondition.awaitAndVerify(depositUris ->
                        submission.getRepositories().size() == depositUris.size()));

        // 2. The Deposit resources should be in a SUBMITTED (for PubMed Central) or ACCEPTED (for JScholarship) state

        assertTrue("Deposit resource with unexpected status",
                depositUrisCondition.awaitAndVerify(depositUris ->
                                depositUris.stream().allMatch(uri -> {
                                            Deposit deposit = passClient.readResource(uri, Deposit.class);
                                            return deposit.getDepositStatus() == ACCEPTED ||
                                                    deposit.getDepositStatus() == SUBMITTED;
                                        })));

        // 2a. If the Repository for the Deposit uses SWORD (i.e. is a DSpace repository like JScholarship) then a
        // non-null 'depositStatusRef' should be present, and the status of the Deposit should be ACCEPTED, and it's
        // RepositoryCopy should be COMPLETE

        final Collection<URI> depositUris = getDepositUris(submission, passClient);

        Repository dspaceRepo = submission.getRepositories().stream()
                .map(uri -> (Repository)submissionResources.get(uri))
                .filter(repo -> repo.getName().equals(J10P_REPO_NAME))
                .findAny().orElseThrow(() ->
                        new RuntimeException("Missing expected Repository named '" + J10P_REPO_NAME + "' for " + submission.getId()));

        Deposit dspaceDeposit = depositUris.stream().map(uri -> passClient.readResource(uri, Deposit.class))
                .filter(deposit -> deposit.getRepository().toString().equals(dspaceRepo.getId().toString()))
                .findAny().orElseThrow(() ->
                        new RuntimeException("Missing Deposit for Repository '" + dspaceRepo.getName() + "', '" + dspaceRepo.getId() + "'"));

        Condition<Deposit> nonNullDepositRefCondition = new Condition<>(() ->
                passClient.readResource(dspaceDeposit.getId(), Deposit.class), "Get J10P Deposit");
        assertTrue("Expected a non-null deposit status reference for " + J10P_REPO_NAME,
                nonNullDepositRefCondition.awaitAndVerify((deposit) -> deposit.getDepositStatusRef() != null));
        assertEquals(Deposit.DepositStatus.ACCEPTED, dspaceDeposit.getDepositStatus());
        assertEquals("Expected a \"COMPLETE\" status for the JScholarship RepositoryCopy", COMPLETE,
                passClient.readResource(dspaceDeposit.getRepositoryCopy(), RepositoryCopy.class).getCopyStatus());

        // 2b. If the Repository for the Deposit is for PubMed Central, then the 'depositStatusRef' should be null, and
        // the status of the Deposit should be SUBMITTED, and there should be no RepositoryCopy

        Repository pmcRepo = submission.getRepositories().stream()
                .map(uri -> (Repository)submissionResources.get(uri))
                .filter(repo -> repo.getName().equals(PMC_REPO_NAME))
                .findAny().orElseThrow(() ->
                        new RuntimeException("Missing expected Repository named '" + PMC_REPO_NAME + "' for " + submission.getId()));

        Deposit pmcDeposit = depositUris.stream().map(uri -> passClient.readResource(uri, Deposit.class))
                .filter(deposit -> deposit.getRepository().toString().equals(pmcRepo.getId().toString()))
                .findAny().orElseThrow(() ->
                        new RuntimeException("Missing Deposit for Repository '" + pmcRepo.getName() + "', '" + pmcRepo.getId() + "'"));
        assertNull("Expected a null deposit status reference for " + PMC_REPO_NAME,
                pmcDeposit.getDepositStatusRef());
        assertEquals(Deposit.DepositStatus.SUBMITTED, pmcDeposit.getDepositStatus());

        assertNull("Expected no RepositoryCopy resource for PubMed Central deposit.", pmcDeposit.getRepositoryCopy());

    }

}
