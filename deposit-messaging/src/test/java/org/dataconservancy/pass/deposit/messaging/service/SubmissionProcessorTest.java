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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomAggregatedDepositStatusExcept;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomUri;
import static org.dataconservancy.pass.deposit.messaging.service.SubmissionProcessor.CriFunc.critical;
import static org.dataconservancy.pass.deposit.messaging.service.SubmissionProcessor.CriFunc.postCondition;
import static org.dataconservancy.pass.deposit.messaging.service.SubmissionProcessor.CriFunc.preCondition;
import static org.dataconservancy.pass.deposit.messaging.service.SubmissionProcessor.getLookupKeys;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.FAILED;
import static org.dataconservancy.pass.model.Repository.IntegrationType.WEB_LINK;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static submissions.SubmissionResourceUtil.asJson;
import static submissions.SubmissionResourceUtil.asStream;
import static submissions.SubmissionResourceUtil.lookupUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.AggregatedDepositStatus;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.core.task.TaskRejectedException;

public class SubmissionProcessorTest extends AbstractSubmissionProcessorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private SubmissionProcessor underTest;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        underTest = new SubmissionProcessor(passClient, jsonParser, submissionBuilder, packagerRegistry,
                                            submissionPolicy, depositTaskHelper, cri);
    }

    /**
     * Verifies the actions of SubmissionProcessor when a Submission is successful.  The SubmissionProcessor:
     * <ol>
     *     <li>Updates the aggregated deposit status of the Submission to IN-PROGRESS</li>
     *     <li>Builds a DepositSubmission from the Submission</li>
     *     <li>Creates a Deposit resource in the Fedora repository for each Repository associated with the
     *     Submission</li>
     *     <li>Resolves the Packager (used to perform the transfer of custodial content to a downstream repository)
     *     for each Repository</li>
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
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
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
        }).peek(repo -> {
            when(passClient.readResource(repo.getId(), Repository.class)).thenReturn(repo);

            when(passClient.createAndReadResource(
                argThat((deposit) -> deposit != null && deposit.getRepository().equals(repo.getId())),
                eq(Deposit.class))).then(inv -> {
                    // Assert that the Deposit being created carries
                    // the correct state
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

    /**
     * Insures that the DepositTaskHelper does not process any [Submission, Deposit, Repository] tuples where the
     * Repository has an IntegrationType of "web-link".
     */
    @Test
    @SuppressWarnings("unchecked")
    public void filterRepositoryByIntegrationType() {

        // Mock a DepositTaskHelper for this test.
        DepositTaskHelper mockHelper = mock(DepositTaskHelper.class);
        underTest = new SubmissionProcessor(passClient, jsonParser, submissionBuilder, packagerRegistry,
                                            submissionPolicy, mockHelper, cri);

        // Boilerplate to build a sample Submission and mock the PassClient to return the Submission and Repository
        // resources when asked.
        FilesystemModelBuilder builder = new FilesystemModelBuilder();
        PassJsonAdapterBasic adapter = new PassJsonAdapterBasic();

        URI submissionUri = URI.create("fake:submission1");

        Map<URI, Submission> submissionMap = asStream(asJson(submissionUri))
                .filter(node -> node.has("@id") && node.has("@type") &&
                        node.get("@type").asText().equals("Submission")
                ).collect(Collectors.toMap(node -> URI.create(node.get("@id").asText()), node -> {
                    try {
                        return adapter.toModel(toInputStream(node.toString(), UTF_8), Submission.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

        Map<URI, Repository> repoMap = asStream(asJson(submissionUri))
                .filter(node -> node.has("@id") && node.has("@type") &&
                        node.get("@type").asText().equals("Repository"))
                .collect(Collectors.toMap(node -> URI.create(node.get("@id").asText()), node -> {
                    try {
                        return adapter.toModel(toInputStream(node.toString(), UTF_8), Repository.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

        when(passClient.readResource(any(URI.class), eq(Submission.class))).then(
            inv -> submissionMap.get(inv.getArgument(0)));

        when(passClient.readResource(any(URI.class), eq(Repository.class))).then(
            inv -> repoMap.get(inv.getArgument(0)));

        // Mock a successful CRI
        when(cri.performCritical(eq(submissionUri), eq(Submission.class), any(Predicate.class),
                any(BiPredicate.class), any(Function.class)))
            .thenAnswer(inv -> {
                return new CriticalResult<>(builder.build(lookupUri(submissionUri).toString()),
                                            submissionMap.get(submissionUri), true);
            });

        // Mock a Package registry lookup
        when(packagerRegistry.get(any())).thenReturn(mock(Packager.class));

        // Mock the DepositTaskHelper to record the Repositories it is submitting to.
        List<Repository> capturedRepos = new ArrayList<>();
        doAnswer(inv -> {
            capturedRepos.add(inv.getArgument(2));
            return null;
        }).when(mockHelper).submitDeposit(eq(submissionMap.get(submissionUri)), any(DepositSubmission.class),
                                          any(Repository.class), eq(null), any(Packager.class));

        // Total count of Repository resources attached to the Submission
        int allRepos = submissionMap.get(submissionUri).getRepositories().size();

        // Verify that we start with at least one repository that has an integration type of web-link attached
        // to the Submission.  The SubmissionProcessor should filter those repositories, and not invoke the
        // DepositTaskHelper for those [Deposit, Submission, Repository] tuples
        int weblinkRepos = (int) submissionMap.get(submissionUri)
                                              .getRepositories()
                                              .stream()
                                              .map(repoMap::get)
                                              .filter(repo -> WEB_LINK == repo.getIntegrationType())
                                              .count();
        assertTrue("Expected at least one repository with integration type " + WEB_LINK,
                   weblinkRepos > 0);

        // Invoke the SubmissionProcessor
        underTest.accept(submissionMap.get(submissionUri));

        // Verify the DepositTaskHelper was called once for each *non-web-link* Repository
        verify(mockHelper, times(allRepos - weblinkRepos))
            .submitDeposit(
                eq(submissionMap.get(submissionUri)),
                any(DepositSubmission.class),
                any(Repository.class),
                eq(null),
                any(Packager.class));

        assertEquals(allRepos - weblinkRepos, capturedRepos.size());

        capturedRepos.stream().filter(repo -> WEB_LINK == repo.getIntegrationType()).findAny().ifPresent(repo -> {
            throw new RuntimeException("Should not have any Repositories with integration type " + WEB_LINK);
        });

    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingCriResource() throws Exception {
        URI submissionUri = URI.create("http://submission.uri");
        Submission submission = new Submission();
        submission.setId(submissionUri);

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
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

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
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

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
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
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
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
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
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
                    assertEquals(null, ((Deposit) inv.getArgument(0)).getDepositStatus());
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
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
        DepositSubmission depositSubmission = new DepositSubmission();

        // Mock the CRI that returns the "In-Progress" Submission and builds the DepositSubmission.

        CriticalResult<DepositSubmission, Submission> criResult = mock(CriticalResult.class);
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
                    assertEquals(FAILED, ((Deposit) inv.getArgument(0)).getDepositStatus());
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

    /* Assure the right repo keys are used for lookup */
    @Test
    public void lookupTest() {
        Repository repo = new Repository();
        repo.setId(URI.create("http://example.org/a/b/c"));
        repo.setName("The name");
        repo.setRepositoryKey("the key");

        Collection<String> keys = getLookupKeys(repo);

        assertTrue(keys.contains(repo.getId().toString()));
        assertTrue(keys.contains(repo.getName()));
        assertTrue(keys.contains(repo.getRepositoryKey()));
        assertTrue(keys.contains("a/b/c"));
        assertTrue(keys.contains("b/c"));
        assertTrue(keys.contains("c"));
    }

    /* Just to make sure things don't blow up with null values */
    @Test
    public void lookupNullsTest() {
        Repository repo = new Repository();

        Collection<String> keys = getLookupKeys(repo);

        assertTrue(keys.isEmpty());
    }

    /**
     * The submission is accepted if the submission policy supplied to the precondition accepts the submission
     */
    @Test
    public void criFuncPreconditionSuccess() {
        Submission s = mock(Submission.class);
        when(submissionPolicy.test(s)).thenReturn(true);

        assertTrue(preCondition(submissionPolicy).test(s));

        verify(submissionPolicy).test(s);
    }

    /**
     * The submission is rejected if the submission policy supplied to the precondition rejects the submission
     */
    @Test
    public void criFuncPreconditionFail() {
        Submission s = mock(Submission.class);
        when(submissionPolicy.test(s)).thenReturn(false);

        assertFalse(preCondition(submissionPolicy).test(s));

        verify(submissionPolicy).test(s);
    }

    /**
     * Postcondition succeeds when:
     * - The Submission aggregate deposit status is IN_PROGRESS
     * - The DepositSubmission has at least one file
     * - The DepositFile has a non-empty location
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void criFuncPostconditionSuccess() {
        Submission s = mock(Submission.class);
        DepositSubmission ds = mock(DepositSubmission.class);
        DepositFile df = mock(DepositFile.class);

        when(s.getAggregatedDepositStatus()).thenReturn(AggregatedDepositStatus.IN_PROGRESS);
        when(ds.getFiles()).thenReturn(Collections.singletonList(df));
        when(df.getLocation()).thenReturn(randomUri().toString());

        assertTrue(postCondition().test(s, ds));

        verify(s).getAggregatedDepositStatus();
        verify(ds, atLeastOnce()).getFiles();
        verify(df, atLeastOnce()).getLocation();
    }

    /**
     * Postcondition fails when the AggregatedDepositStatus is not IN_PROGRESS
     */
    @Test
    public void criFuncPostconditionFailsAggregateDepositStatus() {
        Submission s = mock(Submission.class);
        DepositSubmission ds = mock(DepositSubmission.class);

        when(s.getAggregatedDepositStatus()).thenReturn(
            randomAggregatedDepositStatusExcept(AggregatedDepositStatus.IN_PROGRESS));
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("expected status 'in-progress'");

        assertFalse(postCondition().test(s, ds));

        verify(s).getAggregatedDepositStatus();
        verifyZeroInteractions(ds);
    }

    /**
     * Postcondition fails when there are no DepositFiles
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void criFuncPostconditionFailsNoDepositFiles() {
        Submission s = mock(Submission.class);
        DepositSubmission ds = mock(DepositSubmission.class);

        when(s.getAggregatedDepositStatus()).thenReturn(AggregatedDepositStatus.IN_PROGRESS);
        when(ds.getFiles()).thenReturn(Collections.emptyList());

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("no files attached");

        assertFalse(postCondition().test(s, ds));

        verify(s).getAggregatedDepositStatus();
        verify(ds).getFiles();
        verifyZeroInteractions(ds);
    }

    /**
     * Postcondition fails if any of the DepositFiles is missing a location
     */
    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void criFuncPostconditionFailsDepositFileLocation() {
        Submission s = mock(Submission.class);
        DepositSubmission ds = mock(DepositSubmission.class);
        DepositFile file1 = mock(DepositFile.class);
        DepositFile file2 = mock(DepositFile.class);

        when(s.getAggregatedDepositStatus()).thenReturn(AggregatedDepositStatus.IN_PROGRESS);
        when(ds.getFiles()).thenReturn(Arrays.asList(file1, file2));
        when(file1.getLocation()).thenReturn(randomUri().toString());
        when(file2.getLocation()).thenReturn("  ");

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("missing URIs");

        assertFalse(postCondition().test(s, ds));

        verify(s).getAggregatedDepositStatus();
        verify(ds).getFiles();
        verify(file1).getLocation();
        verify(file2).getLocation();
    }

    @Test
    public void criFuncCriticalSuccess() throws InvalidModel {
        URI submissionUri = randomUri();
        Submission s = mock(Submission.class);
        DepositSubmission ds = mock(DepositSubmission.class);

        when(s.getId()).thenReturn(submissionUri);
        when(submissionBuilder.build(submissionUri.toString())).thenReturn(ds);

        assertSame(ds, critical(submissionBuilder).apply(s));

        verify(submissionBuilder).build(submissionUri.toString());
        verify(s).setAggregatedDepositStatus(AggregatedDepositStatus.IN_PROGRESS);
    }

    @Test
    public void criFuncCriticalFailsModelBuilderException() throws InvalidModel {
        URI submissionUri = randomUri();
        Submission s = mock(Submission.class);

        when(s.getId()).thenReturn(submissionUri);
        when(submissionBuilder.build(submissionUri.toString())).thenThrow(InvalidModel.class);

        thrown.expect(RuntimeException.class);
        thrown.expectCause(isA(InvalidModel.class));

        critical(submissionBuilder).apply(s);

        verify(submissionBuilder).build(submissionUri.toString());
        verify(s).getId();
        verifyNoMoreInteractions(s);
    }
}