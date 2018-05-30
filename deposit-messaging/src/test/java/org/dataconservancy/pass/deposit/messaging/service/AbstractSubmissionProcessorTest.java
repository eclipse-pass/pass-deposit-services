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

import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.policy.SubmissionPolicy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.JsonParser;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.junit.Before;
import org.springframework.core.task.TaskExecutor;

import java.net.URI;

import static org.mockito.Mockito.mock;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractSubmissionProcessorTest {
    
    PassClient passClient;

    JsonParser jsonParser;

    SubmissionBuilder submissionBuilder;

    Registry<Packager> packagerRegistry;

    SubmissionPolicy submissionPolicy;

    Policy<Deposit.DepositStatus> dirtyDepositPolicy;

    JmsMessagePolicy messagePolicy;

    TaskExecutor taskExecutor;

    DepositStatusMapper<SwordDspaceDepositStatus> dspaceStatusMapper;

    DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser;

    CriticalRepositoryInteraction critical;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        passClient = mock(PassClient.class);
        jsonParser = mock(JsonParser.class);
        submissionBuilder = mock(SubmissionBuilder.class);
        packagerRegistry = mock(Registry.class);
        submissionPolicy = mock(SubmissionPolicy.class);
        dirtyDepositPolicy = mock(Policy.class);
        messagePolicy = mock(JmsMessagePolicy.class);
        taskExecutor = mock(TaskExecutor.class);
        dspaceStatusMapper = mock(DepositStatusMapper.class);
        atomStatusParser = mock(DepositStatusParser.class);
        critical = mock(CriticalRepositoryInteraction.class);
    }
    
}
