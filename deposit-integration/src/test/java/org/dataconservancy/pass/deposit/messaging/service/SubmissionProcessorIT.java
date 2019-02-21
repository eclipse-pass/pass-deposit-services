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

import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.deposit.messaging.config.spring.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.spring.JmsConfig;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.SubmissionStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import submissions.SubmissionResourceUtil;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Set;

import static org.dataconservancy.pass.deposit.integration.shared.SubmissionUtil.getDepositUris;
import static org.dataconservancy.pass.deposit.integration.shared.SubmissionUtil.getFileUris;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.SUBMITTED;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.COMPLETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"pass.deposit.jobs.default-interval-ms=5000"})
@Import({DepositConfig.class, JmsConfig.class})
public class SubmissionProcessorIT extends AbstractSubmissionIT {

    private static final URI SUBMISSION_RESOURCES = URI.create("fake:submission11");

    @Override
    protected InputStream getSubmissionResources() {
        return SubmissionResourceUtil.lookupStream(SUBMISSION_RESOURCES);
    }

    @Test
    public void smokeSubmission() throws Exception {

        assertEquals(Boolean.FALSE, submission.getSubmitted());
        assertTrue(getFileUris(submission, passClient).size() > 0);

        triggerSubmission(submission.getId());
        
        submission = passClient.readResource(submission.getId(), Submission.class);
        assertEquals(SubmissionStatus.SUBMITTED, submission.getSubmissionStatus());

        // After successfully processing a submission to JScholarship we should observe:

        // 1. Deposit resources created for each Repository associated with the Submission

        Condition<Collection<URI>> depositUrisCondition = new Condition<>(() ->
                getDepositUris(submission, passClient), "expectedDepositCount");

        assertTrue("SubmissionProcessor did not create the expected number of Deposit resources.",
                depositUrisCondition.awaitAndVerify(depositUris ->
                        submission.getRepositories().size() == depositUris.size()));

        // 2. The Deposit resources should be in a SUBMITTED (for PubMed Central) or ACCEPTED (for JScholarship) state

        // These statuses are dependant on the transport being used - because the TransportResponse.onSuccess(...)
        // method may modify the repository resources associated with the Submission.

        /* brainstorming - must discover the Deposit resources that result from this test differently */
        // There should be one Deposit for each Repository attached to the Submission
        // Poll the index and wait for those: Poll for Deposits that link to the Submission

        Condition<Set<Deposit>> deposits = depositsForSubmission(submission.getId(), submission.getRepositories().size(),
                (deposit, repo) -> {
                    if (deposit.getDepositStatusRef() != null) {
                        if (deposit.getRepositoryCopy() == null) {
                            return false;
                        } else {
                            RepositoryCopy repoCopy = passClient.readResource(deposit.getRepositoryCopy(),
                                    RepositoryCopy.class);

                            return ACCEPTED == deposit.getDepositStatus() &&
                                    COMPLETE == repoCopy.getCopyStatus() &&
                                    repoCopy.getAccessUrl() != null &&
                                    repoCopy.getExternalIds().size() > 0;
                        }
                    }

                    return SUBMITTED == deposit.getDepositStatus() &&
                            deposit.getRepositoryCopy() == null;
                });

        deposits.await();

        // sanity check

        assertEquals(submission.getRepositories().size(), deposits.getResult().size());
        assertEquals(deposits.getResult().stream().filter(deposit -> deposit.getSubmission().equals(submission.getId())).count(), submission.getRepositories().size());
        assertTrue(deposits.getResult().stream().anyMatch(deposit -> deposit.getDepositStatusRef() != null && deposit.getDepositStatus() == ACCEPTED));
        assertTrue(deposits.getResult().stream().anyMatch(deposit -> deposit.getDepositStatusRef() == null && deposit.getDepositStatus() == SUBMITTED));

        /* brainstorming */
        // If there is a depositStatusRef on the Deposit resource, then we wait for the ACCEPTED status
        // If there is no DSR, then we wait for SUBMITTED
        // Logic independent of the Repository used.
        // This is really testing the polling of the depositStatusRef by the DefaultDepositStatusProcessor, invoked
        // by the DepositTaskHelper.processDepositStatus(URI) method, which is invoked by:
        // - SubmittedUpdateRunner
        // - DepositProcessor (*method used by this IT*)
        // - DepositUpdater

        // 2a. If the Repository for the Deposit uses SWORD (i.e. is a DSpace repository like JScholarship) then a
        // non-null 'depositStatusRef' should be present, and its RepositoryCopy should be COMPLETE

//        Repository dspaceRepo = repositoryForName(submission, J10P_REPO_NAME);
//        Deposit dspaceDeposit = depositForRepositoryUri(submission, dspaceRepo.getId());
//
//        Condition<Deposit> depositCondition = new Condition<>(() ->
//                passClient.readResource(dspaceDeposit.getId(), Deposit.class), "Get J10P Deposit");
//        assertTrue("Expected a non-null deposit status reference for " + J10P_REPO_NAME,
//                depositCondition.awaitAndVerify((deposit) -> deposit.getDepositStatusRef() != null));
//        assertTrue("Expected a RepositoryCopy to be linked to the " + J10P_REPO_NAME + " Deposit.",
//                depositCondition.awaitAndVerify(deposit -> deposit.getRepositoryCopy() != null));
//        Condition<RepositoryCopy> repositoryCopyCondition = new Condition<>(() ->
//                passClient.readResource(dspaceDeposit.getRepositoryCopy(), RepositoryCopy.class), "Get J10P Repository Copy");
//        assertTrue("Expected a \"COMPLETE\" status for the JScholarship RepositoryCopy",
//                repositoryCopyCondition.awaitAndVerify(repoCopy -> COMPLETE == repoCopy.getCopyStatus()));

        // 2b. If the Repository for the Deposit is for PubMed Central, then the 'depositStatusRef' should be null, and
        // the status of the Deposit should be SUBMITTED, and there should be no RepositoryCopy
//
//        Repository pmcRepo = repositoryForName(submission, PMC_REPO_NAME);
//        Deposit pmcDeposit = depositForRepositoryUri(submission, pmcRepo.getId());
//
//        assertNull("Expected a null deposit status reference for " + PMC_REPO_NAME,
//                pmcDeposit.getDepositStatusRef());
//        assertEquals(Deposit.DepositStatus.SUBMITTED, pmcDeposit.getDepositStatus());
//        assertNull("Expected a null RepositoryCopy resource for PubMed Central deposit.", pmcDeposit.getRepositoryCopy());

    }

}
