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

import org.dataconservancy.nihms.assembler.Assembler;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.nihms.transport.Transport;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@Ignore("TODO: Update to latest logic")
public class SubmissionProcessorTest extends AbstractSubmissionProcessorTest {

    private SubmissionProcessor underTest;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        underTest = new SubmissionProcessor(passClient, jsonParser, submissionBuilder, packagerRegistry,
                submissionPolicy, dirtyDepositPolicy, messagePolicy, terminalDepositStatusPolicy, taskExecutor,
                dspaceStatusMapper, atomStatusParser, critical);
    }

    /**
     * When the submission policy accepts the incoming submission for processing, the next thing to be checked is the
     * deposit policy.  Insure that when the submission policy returns true, that the deposit policy is checked next.
     *
     * @throws Exception
     */
    @Test
    public void submissionPolicyAccept() throws Exception {
        Submission submission = new Submission();
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(Deposit.DepositStatus.SUBMITTED);

        when(submissionPolicy.accept(submission)).thenReturn(true);

        underTest.accept(submission);

        verify(submissionPolicy).accept(submission);
        verify(dirtyDepositPolicy).accept(deposit.getDepositStatus());
    }

    /**
     * Insure that when the submission policy denies the incoming submisson, that no attempt is made to build a
     * DepositSubmission, and that no deposit task is sent to the the task executor
     *
     * @throws Exception
     */
    @Test
    public void submissionPolicyDenyReturns() throws Exception {
        Submission submission = new Submission();
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(Deposit.DepositStatus.SUBMITTED);

        when(submissionPolicy.accept(submission)).thenReturn(false);

        underTest.accept(submission);

        verify(submissionPolicy).accept(submission);
        verifyZeroInteractions(submissionBuilder, taskExecutor);
    }

    /**
     * When the submission policy and the deposit policy accept the submission and deposit, we expect the
     * SubmissionBuilder to be invoked to create a DepositSubmission.
     *
     * @throws Exception
     */
    @Test
    public void dirtyDepositPolicyAccept() throws Exception {
        Submission submission = new Submission();
        submission.setId(URI.create("http://example.org/submission/1"));
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(Deposit.DepositStatus.SUBMITTED);
        URI repoUri = URI.create("http://example.org/repo/1");
        deposit.setRepository(repoUri);
        Repository repo = new Repository();
        repo.setId(repoUri);

        when(submissionPolicy.accept(submission)).thenReturn(true);
        when(dirtyDepositPolicy.accept(deposit.getDepositStatus())).thenReturn(true);

        underTest.accept(submission);

        verify(submissionPolicy).accept(submission);
        verify(dirtyDepositPolicy).accept(deposit.getDepositStatus());
        verify(submissionBuilder).build(submission.getId().toString());
    }

    /**
     * When the submission policy accepts a submission but the deposit policy denies the deposit, the submission builder
     * should not be invoked, and no DepositTask added to the task queue.
     *
     * @throws Exception
     */
    @Test
    public void dirtyDepositPolicyDenyReturns() throws Exception {
        Submission submission = new Submission();
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(Deposit.DepositStatus.SUBMITTED);

        when(submissionPolicy.accept(submission)).thenReturn(true);
        when(dirtyDepositPolicy.accept(deposit.getDepositStatus())).thenReturn(false);

        underTest.accept(submission);

        verify(submissionPolicy).accept(submission);
        verify(dirtyDepositPolicy).accept(deposit.getDepositStatus());
        verifyZeroInteractions(submissionBuilder, taskExecutor);
    }

    /**
     * If the SubmissionBuilder fails building a DepositSubmission, there should be no DepositTask sent to the task
     * executor.
     *
     * @throws Exception
     */
    @Test
    public void submissionBuilderFailureReturns() throws Exception {
        Submission submission = new Submission();
        submission.setId(URI.create("http://example.org/submission/1"));
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(Deposit.DepositStatus.SUBMITTED);

        when(submissionPolicy.accept(submission)).thenReturn(true);
        when(dirtyDepositPolicy.accept(deposit.getDepositStatus())).thenReturn(true);
        when(submissionBuilder.build(submission.getId().toString()))
                .thenThrow(new RuntimeException("expected exception"));

        underTest.accept(submission);

        verify(submissionPolicy).accept(submission);
        verify(dirtyDepositPolicy).accept(deposit.getDepositStatus());
        verify(submissionBuilder).build(submission.getId().toString());
        verifyZeroInteractions(taskExecutor);
    }

    /**
     * When a DepositSubmission is successfully built, a Packager is looked up from the Registry in order to create a
     * DepositTask.  If the Packager cannot be looked up, a DepositTask should not be created and no interaction should
     * occur with the task executor.
     *
     * @throws Exception
     */
    @Test
    public void missingPackagerFromRegistry() throws Exception {
        Submission submission = new Submission();
        submission.setId(URI.create("http://example.org/submission/1"));
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(Deposit.DepositStatus.SUBMITTED);
        URI repoUri = URI.create("http://example.org/repo/1");
        deposit.setRepository(repoUri);
        Repository repo = new Repository();
        repo.setId(repoUri);
        repo.setName("Repo Name");

        when(submissionPolicy.accept(submission)).thenReturn(true);
        when(dirtyDepositPolicy.accept(deposit.getDepositStatus())).thenReturn(true);
        when(submissionBuilder.build(submission.getId().toString())).thenReturn(new DepositSubmission());
        when(packagerRegistry.get(repo.getName())).thenReturn(null);

        underTest.accept(submission);

        verify(submissionPolicy).accept(submission);
        verify(dirtyDepositPolicy).accept(deposit.getDepositStatus());
        verify(submissionBuilder).build(submission.getId().toString());
        verify(packagerRegistry).get(repo.getName());
        verifyZeroInteractions(taskExecutor);
    }

    /**
     * A successful submission will result in a DepositTask being submitted to the task executor.  For a submission to
     * be successful:
     * <ol>
     *     <li>The submission policy must accept the submission</li>
     *     <li>The deposit policy must accept the deposit</li>
     *     <li>The submission builder must map the Submission into the deposit services model, creating a
     *         DepositSubmission.  This process includes resolving all the resources associated with a Submission and
     *         Deposit</li>
     *     <li>A Packager must be resolved based on the Submission, Deposit, and Repository.  The Packager contains
     *         references to the Assembler and Transport used to compose and stream the package to a Repository.</li>
     *     <li>After creating a so-called DepositContext, a DepositTask is created and submitted to the task queue</li>
     * </ol>
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void successfulSubmission() throws Exception {
        Submission submission = new Submission();
        submission.setId(URI.create("http://example.org/submission/1"));
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(Deposit.DepositStatus.SUBMITTED);
        URI repoUri = URI.create("http://example.org/repo/1");
        deposit.setRepository(repoUri);
        Repository repo = new Repository();
        repo.setId(repoUri);
        repo.setName("Repo Name");
        Packager packager = new Packager("Packager Name", mock(Assembler.class), mock(Transport.class),
                mock(Map.class));

        when(submissionPolicy.accept(submission)).thenReturn(true);
        when(dirtyDepositPolicy.accept(deposit.getDepositStatus())).thenReturn(true);
        when(submissionBuilder.build(submission.getId().toString())).thenReturn(new DepositSubmission());
        when(packagerRegistry.get(repo.getName())).thenReturn(packager);

        underTest.accept(submission);

        verify(submissionPolicy).accept(submission);
        verify(dirtyDepositPolicy).accept(deposit.getDepositStatus());
        verify(submissionBuilder).build(submission.getId().toString());
        verify(packagerRegistry).get(repo.getName());
        verify(taskExecutor).execute(any(DepositTask.class));
    }
}