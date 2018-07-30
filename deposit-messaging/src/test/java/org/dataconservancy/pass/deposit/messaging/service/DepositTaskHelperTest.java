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

import org.dataconservancy.pass.deposit.messaging.config.repository.Repositories;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositTaskHelperTest {

    private PassClient passClient;

    private TaskExecutor taskExecutor;

    private Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy;

    private Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private Registry<Packager> packagerRegistry;

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
        packagerRegistry = mock(Registry.class);
        repositories = mock(Repositories.class);

        underTest = new DepositTaskHelper(passClient, taskExecutor, intermediateDepositStatusPolicy,
                terminalDepositStatusPolicy, cri, packagerRegistry, repositories);

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

}