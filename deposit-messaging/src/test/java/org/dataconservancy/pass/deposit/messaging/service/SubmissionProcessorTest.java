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

import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.core.task.TaskRejectedException;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.model.Deposit.DepositStatus.FAILED;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SubmissionProcessorTest extends AbstractSubmissionProcessorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private SubmissionProcessor underTest;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        underTest = new SubmissionProcessor(passClient, jsonParser, submissionBuilder, packagerRegistry,
                submissionPolicy, dirtyDepositPolicy, messagePolicy, terminalDepositStatusPolicy, depositTaskHelper, dspaceStatusMapper, atomStatusParser, cri);
    }

    /**
     * Verifies the actions of SubmissionProcessor when a Submission is successful.  The SubmissionProcessor:
     * <ol>
     *     <li>Updates the aggregated deposit status of the Submission to IN-PROGRESS</li>
     *     <li>Builds a DepositSubmission from the Submission</li>
     *     <li>Creates a Deposit resource in the Fedora repository for each Repository associated with the Submission</li>
     *     <li>Resolves the Packager (used to perform the transfer of custodial content to a downstream repository) for each Repository</li>
     *     <li>Composes and submits a DepositTask to the queue for each Deposit</li>
     * </ol>
     * The issue with this test is that it doesn't test the CRI in SubmissionProcessor that updates the status of the
     * incoming Submission to "IN-PROGRESS".  The CRI is completely mocked and so:
     * <ul>
     *     <li>CRI precondition that uses the SubmissionPolicy is untested</li>
     *     <li>CRI postcondition is not tested</li>
     *     <li>The critical update that builds the DepositSubmission and sets the Submission aggregated deposit
     *         status is not tested</li>
     * </ul>
     * Not sure yet what the strategy is to test a CRI implementation.
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void submissionAcceptSuccess() throws Exception {

        // Mock the Repositories that the submission is going to

        URI repo1uri = URI.create("http://repo1.uri");
        URI repo2uri = URI.create("http://repo2.uri");
        List<URI> repositoryIds = Arrays.asList(repo1uri, repo2uri);

        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).

        URI submissionUri = URI.create("http://submission.uri");
        Submission submission = new Submission();
        submission.setId(submissionUri);
        submission.setRepositories(repositoryIds);
        submission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        // Mock the interactions with the repository that create Deposit resources, insuring the SubmissionProcessor
        // sets the correct state on newly created Deposits.

        List<Repository> createdRepositories = repositoryIds.stream().map(repoUri -> {
                    Repository r = new Repository();
                    r.setId(repoUri);
                    r.setName("Repository for " + repoUri);
                    return r;
                }).
                peek(repo -> {
                    when(passClient.readResource(repo.getId(), Repository.class)).thenReturn(repo);

                    when(passClient.createAndReadResource(
                            argThat((deposit) -> deposit != null && deposit.getRepository().equals(repo.getId())),
                            eq(Deposit.class))).then(inv -> {
                        // Assert that the Deposit being created carries the correct state
                        Deposit deposit = inv.getArgument(0);
                        assertNotNull(deposit);
                        assertEquals(repo.getId().toString(), deposit.getRepository().toString());
                        assertEquals(submissionUri.toString(), deposit.getSubmission().toString());
                        assertNull(deposit.getDepositStatus());
                        return deposit;
                    });

                    // Packagers are looked up by name of the repository

                    when(packagerRegistry.get(repo.getName())).thenReturn(mock(Packager.class));
                }).collect(Collectors.toList());


        underTest.accept(submission);

        // Verify the CRI executed successfully and the results obtained properly
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();
        verify(criResult).resource();
        verify(criResult).result();

        // Verify we created a Deposit for each Repository
        verify(passClient, times(submission.getRepositories().size()))
                .createAndReadResource(any(Deposit.class), eq(Deposit.class));

        // Verify that each Repository was read from the Fedora repository, and that a Packager for each Repository was
        // resolved from the PackagerRegistry
        createdRepositories.forEach(repo -> {
            verify(passClient).readResource(repo.getId(), Repository.class);
            verify(packagerRegistry).get(repo.getName());
        });

        // Insure that a DepositTask was submitted for each Deposit (number of Repositories == number of Deposits)
        verify(taskExecutor, times(submission.getRepositories().size())).execute(any(DepositTask.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingCriResource() throws Exception {
        URI submissionUri = URI.create("http://submission.uri");
        Submission submission = new Submission();
        submission.setId(submissionUri);

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.result()).thenReturn(Optional.of(new DepositSubmission()));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        // This should never happen, but Deposit Services checks to be sure that the resource() isn't empty.
        when(criResult.resource()).thenReturn(Optional.empty());

        thrown.expect(DepositServiceRuntimeException.class);
        thrown.expectMessage("Missing expected Submission " + submission.getId());

        underTest.accept(submission);

        // Verify the CRI execution failed
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();
        verify(criResult).result();

        // Verify nothing was sent to the DepositTask task executor
        verifyZeroInteractions(taskExecutor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingCriResult() throws Exception {
        URI submissionUri = URI.create("http://submission.uri");
        Submission submission = new Submission();
        submission.setId(submissionUri);

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult criResult = mock(CriticalResult.class);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.success()).thenReturn(true);
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        // This should never happen, but Deposit Services checks to be sure that the resource() isn't empty.
        when(criResult.result()).thenReturn(Optional.empty());

        thrown.expect(DepositServiceRuntimeException.class);
        thrown.expectMessage("Missing expected DepositSubmission");

        underTest.accept(submission);

        // Verify the CRI execution failed
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();
        verify(criResult).result();

        // Verify nothing was sent to the DepositTask task executor
        verifyZeroInteractions(taskExecutor);
    }

    /**
     * If the CRI responsible for updating Submission status and building the DepositSubmission fails, then a
     * DepositServiceRuntimeException should be thrown which contains the Submission.
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void submissionCriFailure() throws Exception {
        URI submissionUri = URI.create("http://submission.uri");
        Submission submission = new Submission();
        submission.setId(submissionUri);

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.
        // In this test the CRI fails, for whatever reason.

        CriticalResult criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(false);
        Exception expectedCause = new Exception("Failed CRI");
        when(criResult.throwable()).thenReturn(Optional.of(expectedCause));
        when(criResult.result()).thenReturn(Optional.of(new DepositSubmission()));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        thrown.expect(DepositServiceRuntimeException.class);
        thrown.expectMessage("Unable to update status of " + submission.getId());
        thrown.expectCause(is(expectedCause));

        underTest.accept(submission);

        // Verify the CRI execution failed
        verify(cri).performCritical(any(), any(), any(), any(BiPredicate.class), any());
        verify(criResult).success();
        verify(criResult).result();

        // Verify nothing was sent to the DepositTask task executor
        verifyZeroInteractions(taskExecutor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void depositCreationFailure() throws Exception {
        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).

        URI submissionUri = URI.create("http://submission.uri");
        List<URI> repositoryIds = Collections.singletonList(URI.create("http://repo.uri"));
        Submission submission = new Submission();
        submission.setId(submissionUri);
        submission.setRepositories(repositoryIds);
        submission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        RuntimeException expectedCause = new RuntimeException("Error saving Deposit resource.");
        List<Repository> createdRepositories = repositoryIds.stream().map(repoUri -> {
            Repository r = new Repository();
            r.setId(repoUri);
            r.setName("Repository for " + repoUri);
            return r;
        }).peek(repo -> {
            when(passClient.readResource(repo.getId(), Repository.class)).thenReturn(repo);
            when(passClient.createAndReadResource(any(Deposit.class), eq(Deposit.class))).thenThrow(expectedCause);
            when(packagerRegistry.get(repo.getName())).thenReturn(mock(Packager.class));
        }).collect(Collectors.toList());

        thrown.expect(DepositServiceRuntimeException.class);
        thrown.expectCause(is(expectedCause));

        underTest.accept(submission);

        verifyZeroInteractions(taskExecutor);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void taskRejection() throws Exception {
        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).

        URI submissionUri = URI.create("http://submission.uri");
        List<URI> repositoryIds = Collections.singletonList(URI.create("http://repo.uri"));
        Submission submission = new Submission();
        submission.setId(submissionUri);
        submission.setRepositories(repositoryIds);
        submission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        List<Repository> createdRepositories = repositoryIds.stream().map(repoUri -> {
            Repository r = new Repository();
            r.setId(repoUri);
            r.setName("Repository for " + repoUri);
            return r;
        }).peek(repo -> {
            when(passClient.readResource(repo.getId(), Repository.class)).thenReturn(repo);
            when(passClient.createAndReadResource(any(Deposit.class), eq(Deposit.class)))
                    .then(inv -> {
                        assertEquals(null, ((Deposit)inv.getArgument(0)).getDepositStatus());
                        ((Deposit) inv.getArgument(0)).setId(URI.create("http://deposit.uri"));
                        return inv.getArgument(0);
                    });

            when(packagerRegistry.get(repo.getName())).thenReturn(mock(Packager.class));

        }).collect(Collectors.toList());

        thrown.expect(DepositServiceRuntimeException.class);
        thrown.expectCause(isA(TaskRejectedException.class));

        doThrow(TaskRejectedException.class).when(taskExecutor).execute(any(DepositTask.class));

        underTest.accept(submission);

        verify(taskExecutor).execute(any(DepositTask.class));
    }

    /**
     * When a DepositSubmission is successfully built, a Packager is looked up from the Registry in order to create a
     * DepositTask.  If the Packager cannot be looked up, a DepositServiceRuntimeException should be thrown, a
     * DepositTask should <em>not</em> be created and <em>no</em> interaction should occur with the task executor.
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void missingPackagerFromRegistry() throws Exception {
        // Set the Repositories on the Submission, and create a DepositSubmission (the Submission mapped to the
        // Deposit Services' model).

        URI submissionUri = URI.create("http://submission.uri");
        List<URI> repositoryIds = Collections.singletonList(URI.create("http://repo.uri"));
        Submission submission = new Submission();
        submission.setId(submissionUri);
        submission.setRepositories(repositoryIds);
        submission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult criResult = mock(CriticalResult.class);
        when(criResult.success()).thenReturn(true);
        when(criResult.resource()).thenReturn(Optional.of(submission));
        when(criResult.result()).thenReturn(Optional.of(depositSubmission));
        when(cri.performCritical(any(), any(), any(), any(BiPredicate.class), any())).thenReturn(criResult);

        List<Repository> createdRepositories = repositoryIds.stream().map(repoUri -> {
            Repository r = new Repository();
            r.setId(repoUri);
            r.setName("Repository for " + repoUri);
            return r;
        }).peek(repo -> {
            when(passClient.readResource(repo.getId(), Repository.class)).thenReturn(repo);
            when(passClient.createAndReadResource(any(Deposit.class), eq(Deposit.class)))
                    .then(inv -> {
                        assertEquals(FAILED, ((Deposit)inv.getArgument(0)).getDepositStatus());
                        ((Deposit) inv.getArgument(0)).setId(URI.create("http://deposit.uri"));
                        return inv.getArgument(0);
                    });

            // Packagers are looked up by name of the repository
            // Return 'null' to mock an error in resolving the Packager
            when(packagerRegistry.get(repo.getName())).thenReturn(null);

        }).collect(Collectors.toList());

        thrown.expect(DepositServiceRuntimeException.class);
        thrown.expectCause(isA(NullPointerException.class));

        underTest.accept(submission);

        verify(packagerRegistry).get(any());
        verify(passClient).createAndReadResource(any(Deposit.class), eq(Deposit.class));
        verifyZeroInteractions(taskExecutor);
    }

}