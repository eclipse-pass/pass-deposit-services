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

import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.messaging.support.Constants;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.messaging.Message;

import javax.jms.Session;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Ignore("TODO: Update to latest logic")
public class JmsSubmissionProcessorTest extends AbstractSubmissionProcessorTest {

    private String resourceType = Constants.PassType.SUBMISSION_RESOURCE;

    private String eventType = Constants.JmsFcrepoEvent.RESOURCE_MODIFICATION;

    private long now = Instant.now().toEpochMilli();

    private String id;

    private Session session;

    private Message<String> message;

    private javax.jms.Message jmsMessage;

    private DepositUtil.MessageContext mc;

    private JmsSubmissionProcessor underTest;

    private CriticalRepositoryInteraction critical;

    @Override
    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        super.setUp();

        id = UUID.randomUUID().toString();
        session = mock(Session.class);
        message = mock(Message.class);
        jmsMessage = mock(javax.jms.Message.class);
        mc = DepositUtil.toMessageContext(resourceType, eventType, now, id, session, message, jmsMessage);
        critical = mock(CriticalRepositoryInteraction.class);

        underTest = new JmsSubmissionProcessor(passClient, jsonParser, submissionBuilder, packagerRegistry,
                submissionPolicy, dirtyDepositPolicy, terminalDepositStatusPolicy, messagePolicy, taskExecutor, depositTaskHelper, dspaceStatusMapper, atomStatusParser, critical);
    }

    /**
     * When an incoming message is accepted for processing, the message is parsed and the Submission resource retrieved.
     * The Submission policy is subsequently invoked.
     *
     * @throws Exception
     */
    @Test
    public void messagePolicyAccept() throws Exception {
        URI submissionUri = URI.create(
                "http://192.168.99.100:8080/fcrepo/rest/submissions/0f/20/05/d7/0f2005d7-0dad-4ee1-812d-00509cc8f362");
        Submission submission = new Submission();
        submission.setId(submissionUri);
        when(messagePolicy.accept(mc)).thenReturn(true);
        when(message.getPayload()).thenReturn(
                IOUtils.toString(this.getClass().getResourceAsStream("message_payload.json"), "UTF-8"));
        when(jsonParser.parseId(any())).thenReturn(submissionUri.toString());
        when(passClient.readResource(submissionUri, Submission.class)).thenReturn(submission);

        underTest.processInternal(mc);

        verify(messagePolicy).accept(mc);
        verify(message).getPayload();
        verify(jsonParser).parseId(any());
        verify(passClient).readResource(submissionUri, Submission.class);
        verify(submissionPolicy).accept(submission);
        verifyZeroInteractions(submissionBuilder, taskExecutor);
    }

    @Test
    public void messagePolicyDeny() throws Exception {

    }

    @Test
    public void submissionPolicyAccept() throws Exception {

    }

    @Test
    public void submissionPolicyDeny() throws Exception {

    }
}