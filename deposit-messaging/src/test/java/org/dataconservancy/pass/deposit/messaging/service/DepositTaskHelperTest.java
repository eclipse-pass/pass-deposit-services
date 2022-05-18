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

import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomDepositStatusExcept;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomUri;
import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.RemedialDepositException;
import org.dataconservancy.pass.deposit.messaging.config.repository.DepositProcessing;
import org.dataconservancy.pass.deposit.messaging.config.repository.Repositories;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryDepositConfig;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.service.DepositTaskHelper.DepositStatusCriFunc;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusProcessor;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositTaskHelperTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private PassClient passClient;

    private TaskExecutor taskExecutor;

    private Policy<DepositStatus> intermediateDepositStatusPolicy;

    private Policy<DepositStatus> terminalDepositStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private Submission s;

    private DepositSubmission ds;

    private Repository r;

    private Packager p;

    private Deposit d;

    private DepositTaskHelper underTest;

    private Repositories repositories;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        passClient = mock(PassClient.class);
        taskExecutor = mock(TaskExecutor.class);
        intermediateDepositStatusPolicy = mock(Policy.class);
        terminalDepositStatusPolicy = mock(Policy.class);
        cri = mock(CriticalRepositoryInteraction.class);
        repositories = mock(Repositories.class);

        underTest = new DepositTaskHelper(passClient, taskExecutor, intermediateDepositStatusPolicy,
                                          terminalDepositStatusPolicy, cri, repositories);

        s = mock(Submission.class);
        ds = mock(DepositSubmission.class);
        d = mock(Deposit.class);
        r = mock(Repository.class);
        p = mock(Packager.class);
    }

    @Test
    public void j10sStatementUrlHackWithNullValues() throws Exception {
        ArgumentCaptor<DepositTask> dtCaptor = ArgumentCaptor.forClass(DepositTask.class);

        underTest.submitDeposit(s, ds, r, d, p);

        verify(taskExecutor).execute(dtCaptor.capture());

        DepositTask depositTask = dtCaptor.getValue();
        assertNotNull(depositTask);

        assertNull(depositTask.getPrefixToMatch());
        assertNull(depositTask.getReplacementPrefix());
    }

    @Test
    public void j10sStatementUrlHack() throws Exception {
        ArgumentCaptor<DepositTask> dtCaptor = ArgumentCaptor.forClass(DepositTask.class);
        String prefix = "moo";
        String replacement = "foo";

        underTest.setStatementUriPrefix(prefix);
        underTest.setStatementUriReplacement(replacement);
        underTest.submitDeposit(s, ds, r, d, p);

        verify(taskExecutor).execute(dtCaptor.capture());

        DepositTask depositTask = dtCaptor.getValue();
        assertNotNull(depositTask);

        assertEquals(prefix, depositTask.getPrefixToMatch());
        assertEquals(replacement, depositTask.getReplacementPrefix());
    }

    @Test
    public void lookupRepositoryConfigByKey() {
        String key = "repoKey";
        Repository repo = newRepositoryWithKey(key);
        Repositories repositories = newRepositoriesWithConfigFor(key);

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for key '" + key + "'"));
    }

    @Test
    public void lookupRepositoryConfigByUri() {
        String uri = "http://pass.jhu.edu/fcrepo/repositories/ab/cd/ef/gh/abcdefghilmnop";
        Repository repo = newRepositoryWithUri(uri);
        Repositories repositories = newRepositoriesWithConfigFor(uri);

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for uri '" + uri + "'"));
    }

    @Test
    public void lookupRepositoryConfigByUriPath() {
        String path = "/fcrepo/repositories/a-repository";
        String uri = "http://pass.jhu.edu" + path;
        Repository repo = newRepositoryWithUri(uri);
        Repositories repositories = newRepositoriesWithConfigFor(path);

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for path '" + path + "'"));
    }

    @Test
    public void lookupRepositoryConfigByUriPathComponent() {
        String uri = "http://pass.jhu.edu/fcrepo/repositories/a-repository";
        Repository repo = newRepositoryWithUri(uri);
        Repositories repositories = newRepositoriesWithConfigFor("a-repository");

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for path 'a-repository'"));

        repositories = newRepositoriesWithConfigFor("/a-repository");

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(
                             () -> new RuntimeException("Missing expected repository config for path '/a-repository'"));

        repositories = newRepositoriesWithConfigFor("/fcrepo/repositories/a-repository");

        DepositTaskHelper.lookupConfig(repo, repositories)
                         .orElseThrow(() -> new RuntimeException(
                             "Missing expected repository config for path '/fcrepo/repositories/a-repository'"));
    }

    /**
     * When a Deposit has:
     * - an intermediate status
     * - a non-null and non-empty status ref
     * - a repository URI
     * - a repository copy
     *
     * Then the precondition should succeed.
     */
    @Test
    public void depositCriFuncPreconditionSuccess() {
        URI repoUri = randomUri();
        URI repoCopyUri = randomUri();
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(randomUri().toString());
        when(d.getRepository()).thenReturn(repoUri);
        when(d.getRepositoryCopy()).thenReturn(repoCopyUri);
        when(passClient.readResource(repoCopyUri, RepositoryCopy.class)).thenReturn(repoCopy);

        assertTrue(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(intermediateDepositStatusPolicy).test(any());
        verify(passClient).readResource(repoCopyUri, RepositoryCopy.class);
    }

    /**
     * When a Deposit has a terminal status, the precondition should fail
     */
    @Test
    public void depositCriFuncPreconditionFailTerminalStatus() {
        when(intermediateDepositStatusPolicy.test(any())).thenReturn(false);
        when(d.getDepositStatus()).thenReturn(DepositStatus.SUBMITTED);

        // don't need any other mocking, because the test for status comes first.
        // use Mockito.verify to insure this

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));
        verify(d, times(2)).getDepositStatus(); // once for the call, once for the log message
        verify(d).getId(); // log message
        verifyNoMoreInteractions(d);
        verifyZeroInteractions(passClient);
    }

    /**
     * When the deposit has an intermediate status but a null deposit status ref, the precondition should fail
     */
    @Test
    public void depositCriFuncPreconditionFailDepositStatusRef() {
        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));

        // don't need any other mocking, because null is returned by default for the status uri
        // use Mockito.verify to insure this

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d).getDepositStatusRef();
        verify(d).getId(); // log message
        verify(intermediateDepositStatusPolicy).test(any());
        verifyNoMoreInteractions(d);
        verifyZeroInteractions(passClient);
    }

    /**
     * When the deposit has an intermediate status and a non-empty status ref but the Repository is null, the
     * precondition should fail.
     */
    @Test
    public void depositCriFuncPreconditionFailRepository() {
        URI statusRef = randomUri();

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(statusRef.toString());

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d, atLeastOnce()).getDepositStatusRef();
        verify(d).getRepository();
        verify(d).getId(); // log message

        verify(intermediateDepositStatusPolicy).test(any());
        verifyNoMoreInteractions(d);
        verifyZeroInteractions(passClient);
    }

    /**
     * When the deposit has:
     * - an intermediate status
     * - non-empty status ref
     * - non-null Repository
     *
     * but the RepositoryCopy URI is null, the precondition should fail
     */
    @Test
    public void depositCriFuncPreconditionFailNullRepoCopyUri() {
        URI statusRef = randomUri();
        URI repoUri = randomUri();

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(statusRef.toString());
        when(d.getRepository()).thenReturn(repoUri);

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d, atLeastOnce()).getDepositStatusRef();
        verify(d).getRepository();
        verify(d).getRepository();
        verify(d).getRepositoryCopy();
        verify(d).getId(); // log message

        verify(intermediateDepositStatusPolicy).test(any());
        verifyNoMoreInteractions(d);
        verifyZeroInteractions(passClient);
    }

    /***
     * When the deposit has:
     * - an intermediate status
     * - non-empty status ref
     * - non-null repository
     * - non-null repositorycopyURI
     *
     * but the RepositoryCopy is null, the precondition should fail.
     */
    @Test
    public void depositCriFuncPreconditionFailNullRepoCopy() {
        URI statusRef = randomUri();
        URI repoUri = randomUri();
        URI repoCopyUri = randomUri();

        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        when(d.getDepositStatus()).thenReturn(
            // this doesn't really matter since the status policy is mocked to always return true
            randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED));
        when(d.getDepositStatusRef()).thenReturn(statusRef.toString());
        when(d.getRepository()).thenReturn(repoUri);
        when(d.getRepositoryCopy()).thenReturn(repoCopyUri);

        assertFalse(DepositStatusCriFunc.precondition(intermediateDepositStatusPolicy, passClient).test(d));

        verify(d).getDepositStatus();
        verify(d, atLeastOnce()).getDepositStatusRef();
        verify(d).getRepository();
        verify(d).getRepository();
        verify(d).getRepositoryCopy();
        verify(d).getId(); // log message

        verify(intermediateDepositStatusPolicy).test(any());
        verify(passClient).readResource(repoCopyUri, RepositoryCopy.class);
        verifyNoMoreInteractions(d);
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails.
     * If the deposit status is REJECTED, then the returned repository copy must have a copy status of REJECTED, or the
     * post condition fails.
     * Otherwise, the post condition succeeds if the repository copy is non-null.
     */
    @Test
    public void depositCriFuncPostconditionSuccessAccepted() {
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);
        when(d.getDepositStatus()).thenReturn(DepositStatus.ACCEPTED);
        when(repoCopy.getCopyStatus()).thenReturn(CopyStatus.COMPLETE);

        assertTrue(DepositStatusCriFunc.postcondition().test(d, repoCopy));

        verify(repoCopy).getCopyStatus();
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails. If the deposit status is REJECTED, then the returned repository copy must have a copy
     * status of REJECTED, or the post condition fails. Otherwise, the post condition succeeds if the repository copy is
     * non-null.
     */
    @Test
    public void depositCriFuncPostconditionSuccessRejected() {
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);
        when(d.getDepositStatus()).thenReturn(DepositStatus.REJECTED);
        when(repoCopy.getCopyStatus()).thenReturn(CopyStatus.REJECTED);

        assertTrue(DepositStatusCriFunc.postcondition().test(d, repoCopy));

        verify(repoCopy).getCopyStatus();
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails. If the deposit status is REJECTED, then the returned repository copy must have a copy
     * status of REJECTED, or the post condition fails. Otherwise, the post condition succeeds if the repository copy is
     * non-null.
     */
    @Test
    public void depositCriFuncPostconditionSuccessIntermediate() {
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);
        when(d.getDepositStatus()).thenReturn(DepositStatus.SUBMITTED);

        assertTrue(DepositStatusCriFunc.postcondition().test(d, repoCopy));

        verifyZeroInteractions(repoCopy);
    }

    /**
     * If the deposit status is ACCEPTED, then the returned repository copy must have a copy status of COMPLETE, or the
     * post condition fails. If the deposit status is REJECTED, then the returned repository copy must have a copy
     * status of REJECTED, or the post condition fails. Otherwise, the post condition succeeds if the repository copy is
     * non-null.
     */
    @Test
    public void depositCriFuncPostconditionFailNullRepoCopy() {
        assertFalse(DepositStatusCriFunc.postcondition().test(d, null));
        verifyZeroInteractions(d);
    }

    /**
     * When the Deposit is processed as ACCEPTED, the copy status should be set to COMPLETE, and the returned
     * repository copy not null
     */
    @Test
    public void depositCriFuncCriticalSuccessAccepted() {
        CopyStatus expectedCopyStatus = CopyStatus.COMPLETE;
        DepositStatus statusProcessorResult = DepositStatus.ACCEPTED;

        testDepositCriFuncCriticalForStatus(expectedCopyStatus, statusProcessorResult, d, passClient);
    }

    /**
     * When the Deposit is processed as REJECTED, the copy status should be set to REJECTED, and the returned
     * repository copy not null
     */
    @Test
    public void depositCriFuncCriticalSuccessRejected() {
        CopyStatus expectedCopyStatus = CopyStatus.REJECTED;
        DepositStatus statusProcessorResult = DepositStatus.REJECTED;

        testDepositCriFuncCriticalForStatus(expectedCopyStatus, statusProcessorResult, d, passClient);
    }

    /**
     * When the Deposit is processed as an intermediate status, the returned RepositoryCopy must not be null in order
     * to succeed.
     */
    @Test
    public void depositCriFuncCriticalSuccessIntermediate() {
        DepositStatus statusProcessorResult = randomDepositStatusExcept(DepositStatus.ACCEPTED, DepositStatus.REJECTED);

        URI repoUri = randomUri();
        URI repoCopyUri = randomUri();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithUri(repoUri.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoUri.toString(), statusProcessor);
        RepositoryCopy repoCopy = mock(RepositoryCopy.class);

        when(d.getRepository()).thenReturn(repoUri);
        when(d.getRepositoryCopy()).thenReturn(repoCopyUri);

        when(passClient.readResource(repoUri, Repository.class)).thenReturn(repo);
        when(passClient.readResource(repoCopyUri, RepositoryCopy.class)).thenReturn(repoCopy);

        when(statusProcessor.process(eq(d), any())).thenReturn(statusProcessorResult);

        assertSame(repoCopy, DepositStatusCriFunc.critical(repos, passClient).apply(d));

        verify(passClient).readResource(repoUri, Repository.class);
        verify(passClient).readResource(repoCopyUri, RepositoryCopy.class);
        verifyNoMoreInteractions(passClient);
        verify(statusProcessor).process(eq(d), any());
        verifyZeroInteractions(repoCopy);
    }

    /**
     * When there is an error looking up the RepositoryConfig insure there is a proper error message
     */
    @Test
    public void depositCriFuncCriticalMissingRepositoryConfig() {
        URI repoUri = randomUri();

        when(d.getRepository()).thenReturn(repoUri);
        when(passClient.readResource(repoUri, Repository.class)).thenReturn(r);

        expectedException.expect(RemedialDepositException.class);
        expectedException.expectMessage("Unable to resolve Repository Configuration for Repository");

        DepositStatusCriFunc.critical(repositories, passClient).apply(d);

        verify(passClient).readResource(repoUri, Repository.class);
        verifyNoMoreInteractions(passClient);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     */
    @Test
    public void depositCriFuncCriticalNullDepositConfig() {
        URI repoUri = randomUri();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithUri(repoUri.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoUri.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repoUri);
        when(passClient.readResource(repoUri, Repository.class)).thenReturn(repo);
        repos.getConfig(repoUri.toString()).setRepositoryDepositConfig(null);

        verifyNullObjectInDepositStatusProcessorLookup(repoUri, repos);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     */
    @Test
    public void depositCriFuncCriticalNullDepositProcessing() {
        URI repoUri = randomUri();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithUri(repoUri.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoUri.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repoUri);
        when(passClient.readResource(repoUri, Repository.class)).thenReturn(repo);
        repos.getConfig(repoUri.toString()).getRepositoryDepositConfig().setDepositProcessing(null);

        verifyNullObjectInDepositStatusProcessorLookup(repoUri, repos);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     */
    @Test
    public void depositCriFuncCriticalNullDepositStatusProcessor() {
        URI repoUri = randomUri();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithUri(repoUri.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoUri.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repoUri);
        when(passClient.readResource(repoUri, Repository.class)).thenReturn(repo);
        repos.getConfig(repoUri.toString()).getRepositoryDepositConfig().getDepositProcessing().setProcessor(null);

        verifyNullObjectInDepositStatusProcessorLookup(repoUri, repos);
    }

    /**
     * When there is an error resolving the DepositStatusProcessor, insure there is a proper error message
     */
    @Test
    public void depositCriFuncCriticalDepositStatusProcessorProducesNullStatus() {
        URI repoUri = randomUri();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithUri(repoUri.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoUri.toString(), statusProcessor);

        when(d.getRepository()).thenReturn(repoUri);
        when(passClient.readResource(repoUri, Repository.class)).thenReturn(repo);
        when(statusProcessor.process(d, repos.getConfig(repoUri.toString()))).thenReturn(null);

        expectedException.expect(DepositServiceRuntimeException.class);
        expectedException.expectMessage("Failed to update deposit status");

        DepositStatusCriFunc.critical(repositories, passClient).apply(d);

        verify(d).getRepository();
        verify(passClient).readResource(repoUri, Repository.class);
        verifyNoMoreInteractions(passClient);
    }

    private void verifyNullObjectInDepositStatusProcessorLookup(URI repoUri, Repositories repos) {
        expectedException.expect(DepositServiceRuntimeException.class);
        expectedException.expectCause(isA(NullPointerException.class));
        expectedException.expectMessage("parsing the status document referenced by");

        DepositStatusCriFunc.critical(repos, passClient).apply(d);

        verify(passClient).readResource(repoUri, Repository.class);
        verifyNoMoreInteractions(passClient);
    }

    private static Repository newRepositoryWithKey(String key) {
        Repository repo = new Repository();
        repo.setRepositoryKey(key);
        return repo;
    }

    private static Repository newRepositoryWithUri(String uri) {
        Repository repo = new Repository();
        repo.setId(URI.create(uri));
        return repo;
    }

    private static Repositories newRepositoriesWithConfigFor(String key) {
        Repositories repos = new Repositories();
        RepositoryConfig config = new RepositoryConfig();
        config.setRepositoryKey(key);
        repos.addRepositoryConfig(key, config);
        return repos;
    }

    private static Repositories newRepositoriesWithConfigFor(String key, DepositStatusProcessor statusProcessor) {
        Repositories repos = newRepositoriesWithConfigFor(key);

        RepositoryConfig repoConfig = repos.getConfig(key);
        RepositoryDepositConfig depositConfig = new RepositoryDepositConfig();
        DepositProcessing depositProcessing = new DepositProcessing();

        repoConfig.setRepositoryDepositConfig(depositConfig);
        depositConfig.setDepositProcessing(depositProcessing);
        depositProcessing.setProcessor(statusProcessor);

        return repos;
    }

    private static void testDepositCriFuncCriticalForStatus(CopyStatus expectedCopyStatus,
                                                            DepositStatus statusProcessorResult,
                                                            Deposit deposit,
                                                            PassClient passClient) {
        URI repoUri = randomUri();
        URI repoCopyUri = randomUri();
        DepositStatusProcessor statusProcessor = mock(DepositStatusProcessor.class);
        Repository repo = newRepositoryWithUri(repoUri.toString());
        Repositories repos = newRepositoriesWithConfigFor(repoUri.toString(), statusProcessor);
        RepositoryCopy repoCopy = new RepositoryCopy(); // concrete to capture state changes performed by critical

        when(deposit.getRepository()).thenReturn(repoUri);
        when(deposit.getRepositoryCopy()).thenReturn(repoCopyUri);

        when(passClient.readResource(repoUri, Repository.class)).thenReturn(repo);
        when(passClient.readResource(repoCopyUri, RepositoryCopy.class)).thenReturn(repoCopy);
        when(passClient.updateAndReadResource(repoCopy, RepositoryCopy.class)).thenReturn(repoCopy);

        when(statusProcessor.process(eq(deposit), any())).thenReturn(statusProcessorResult);

        RepositoryCopy result = DepositStatusCriFunc.critical(repos, passClient).apply(deposit);

        assertEquals(expectedCopyStatus, result.getCopyStatus());

        verify(passClient).updateAndReadResource(repoCopy, RepositoryCopy.class);
        verify(statusProcessor).process(eq(deposit), any());
    }
}