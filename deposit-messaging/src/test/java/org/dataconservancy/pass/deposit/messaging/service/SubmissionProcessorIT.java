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
import org.dataconservancy.pass.model.RepositoryCopy;
import org.junit.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;

import static java.util.stream.Collectors.toSet;
import static org.dataconservancy.pass.deposit.messaging.service.SubmissionTestUtil.getDepositUris;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.COMPLETE;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionProcessorIT extends AbstractSubmissionIT {

    private static final String SUBMISSION_RESOURCES = "SubmissionProcessorIT.json";

    @Override
    protected InputStream getSubmissionResources() {
        return SubmissionTestUtil.getSubmissionResources(SUBMISSION_RESOURCES);
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

}
