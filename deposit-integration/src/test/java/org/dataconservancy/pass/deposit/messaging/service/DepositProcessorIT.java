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

import java.io.InputStream;

import java.net.URI;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.deposit.messaging.config.spring.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.spring.JmsConfig;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.AggregatedDepositStatus;
import org.dataconservancy.pass.model.Submission.SubmissionStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import submissions.SubmissionResourceUtil;

import static org.dataconservancy.pass.deposit.messaging.service.SubmissionTestUtil.getDepositUris;
import static org.dataconservancy.pass.deposit.messaging.service.SubmissionTestUtil.getFileUris;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.COMPLETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Karen Hanson (karen.hanson@jhu.edu)
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"pass.deposit.jobs.default-interval-ms=5000"})
@Import({DepositConfig.class, JmsConfig.class})
public class DepositProcessorIT extends AbstractSubmissionIT {

    private static final URI SUBMISSION_RESOURCES = URI.create("fake:submission12");
    
    @Override
    protected InputStream getSubmissionResources() {
        return SubmissionResourceUtil.lookupStream(SUBMISSION_RESOURCES);
    }

    @Test
    public void submissionStatusChangeCompleteTest() throws Exception {

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

        // 2. The Deposit resources should be ACCEPTED state

        Condition<Deposit.DepositStatus> j10pStatusCondition = new Condition<>(() -> {
            Repository j10p = repositoryForName(submission, J10P_REPO_NAME);
            Deposit j10pDeposit = depositForRepositoryUri(submission, j10p.getId());
            return j10pDeposit.getDepositStatus();
        }, "J10P Deposit Status");

        assertTrue("Deposit resource with unexpected status for " + J10P_REPO_NAME,
                j10pStatusCondition.awaitAndVerify(status -> ACCEPTED == status));

        // 2a. If the Repository for the Deposit uses SWORD (i.e. is a DSpace repository like JScholarship) then a
        // non-null 'depositStatusRef' should be present, and its RepositoryCopy should be COMPLETE

        Repository dspaceRepo = repositoryForName(submission, J10P_REPO_NAME);
        Deposit dspaceDeposit = depositForRepositoryUri(submission, dspaceRepo.getId());

        Condition<Deposit> depositCondition = new Condition<>(() ->
                passClient.readResource(dspaceDeposit.getId(), Deposit.class), "Get J10P Deposit");
        assertTrue("Expected a non-null deposit status reference for " + J10P_REPO_NAME,
                depositCondition.awaitAndVerify((deposit) -> deposit.getDepositStatusRef() != null));
        assertTrue("Expected a RepositoryCopy to be linked to the " + J10P_REPO_NAME + " Deposit.",
                depositCondition.awaitAndVerify(deposit -> deposit.getRepositoryCopy() != null));
        Condition<RepositoryCopy> repositoryCopyCondition = new Condition<>(() ->
                passClient.readResource(dspaceDeposit.getRepositoryCopy(), RepositoryCopy.class), "Get J10P Repository Copy");
        assertTrue("Expected a \"COMPLETE\" status for the JScholarship RepositoryCopy",
                repositoryCopyCondition.awaitAndVerify(repoCopy -> COMPLETE == repoCopy.getCopyStatus()));

        //3. SubmissionStatus should be changed to COMPLETE, AggregatedDepositStatus to ACCEPTED
        
        Condition<Submission> submissionStatusCondition = new Condition<>(() -> 
            passClient.readResource(submission.getId(), Submission.class), "Get updated Submission");

        assertTrue("Expected an aggregatedDepositStatus of ACCEPTED", 
                   submissionStatusCondition.awaitAndVerify(sub -> 
                       AggregatedDepositStatus.ACCEPTED == sub.getAggregatedDepositStatus()));

        assertTrue("Expected a submissionStatus of COMPLETE", 
                   submissionStatusCondition.awaitAndVerify(sub -> 
                       SubmissionStatus.COMPLETE == sub.getSubmissionStatus()));
        
    }

}
