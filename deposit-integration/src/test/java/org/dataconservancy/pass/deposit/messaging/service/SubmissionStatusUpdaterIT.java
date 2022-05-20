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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static submissions.SubmissionResourceUtil.lookupStream;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.deposit.integration.shared.AbstractSubmissionFixture;
import org.dataconservancy.pass.deposit.messaging.config.spring.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.spring.JmsConfig;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.SubmissionStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(properties = {"pass.deposit.jobs.disabled=true"})
@Import({DepositConfig.class, JmsConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class SubmissionStatusUpdaterIT extends AbstractSubmissionFixture {

    @Autowired
    private SubmissionStatusUpdater underTest;

    private Submission submission;

    @Before
    public void submit() {
        submission = findSubmission(createSubmission(lookupStream(URI.create("fake:submission3"))));
    }

    @Test
    public void processStatusFromSubmittedToAccepted() throws Exception {

        // finds the Submission in the index, then returns the resource
        Condition<Submission> refreshSubmission = new Condition<>(
            () -> {
                URI u = passClient.findByAttribute(Submission.class, "@id", submission.getId());
                if (u != null) {
                    return passClient.readResource(u, Submission.class);
                }
                return null;
            },
            "Refresh Submission resource " + submission.getId());

        // finds all the deposits for the submission - there should be one deposit per repository
        Condition<Set<Deposit>> refreshDeposits = depositsForSubmission(
            submission.getId(),
            submission.getRepositories().size(),
            (deposit, repository) -> true);

        // trigger the submission as if it had been submitted by the PASS UI
        triggerSubmission(submission.getId());

        // wait for the deposit to succeed
        assertTrue(refreshDeposits.awaitAndVerify(deposits -> deposits.size() == submission.getRepositories().size()));
        assertTrue(refreshDeposits.awaitAndVerify(
                deposits -> deposits.stream().allMatch(d -> d.getDepositStatus() == DepositStatus.ACCEPTED)));
        assertTrue(refreshDeposits.awaitAndVerify(
                deposits -> deposits.stream().allMatch(
                        d -> passClient.readResource(d.getRepositoryCopy(), RepositoryCopy.class)
                                .getCopyStatus() == CopyStatus.COMPLETE)));

        // insure the Submission is indexed, as the SubmissionStatusUpdater.doUpdate(...) depends on it being indexed

        assertTrue(refreshSubmission.awaitAndVerify(Objects::nonNull));

        submission = refreshSubmission.getResult();

        // The CRI pre-conditions should not fail prior to invoking the update.

        assertTrue(SubmissionStatusUpdater.CriFunc.preCondition.test(submission));

        underTest.doUpdate();

        refreshSubmission.awaitAndVerify(s -> SubmissionStatus.COMPLETE == s.getSubmissionStatus());
        assertEquals(SubmissionStatus.COMPLETE, refreshSubmission.getResult().getSubmissionStatus());
    }
}
