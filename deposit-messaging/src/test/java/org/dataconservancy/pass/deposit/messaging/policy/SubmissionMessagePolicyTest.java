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
package org.dataconservancy.pass.deposit.messaging.policy;

import static org.dataconservancy.pass.support.messaging.constants.Constants.JmsFcrepoEvent.RESOURCE_CREATION;
import static org.dataconservancy.pass.support.messaging.constants.Constants.JmsFcrepoEvent.RESOURCE_MODIFICATION;
import static org.dataconservancy.pass.support.messaging.constants.Constants.PassType.SUBMISSION_RESOURCE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionMessagePolicyTest {

    private static final String DEPOSIT_RESOURCE = "http://oapass.org/ns/pass#Deposit";

    private SubmissionMessagePolicy underTest;

    @Before
    public void setUp() throws Exception {
        AgentPolicy agentPolicy = mock(AgentPolicy.class);
        when(agentPolicy.test(any())).thenReturn(true);
        underTest = new SubmissionMessagePolicy(agentPolicy);
    }

    @Test
    public void acceptCreationOfSubmission() throws Exception {
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION);
        assertTrue(underTest.test(mc));
    }

    @Test
    public void acceptModificationOfSubmission() throws Exception {
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION);
        assertTrue(underTest.test(mc));
    }

    @Test
    public void acceptCreationOfSubmissionWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", SUBMISSION_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(resource, RESOURCE_CREATION);
        assertTrue(underTest.test(mc));
    }

    @Test
    public void acceptModificationOfSubmissionWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", SUBMISSION_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(resource, RESOURCE_MODIFICATION);
        assertTrue(underTest.test(mc));
    }

    @Test
    public void denyModificationOfNonSubmissions() throws Exception {
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(DEPOSIT_RESOURCE, RESOURCE_CREATION);
        assertFalse(underTest.test(mc));
    }

    @Test
    public void denyCreationOfNonSubmissions() throws Exception {
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(DEPOSIT_RESOURCE, RESOURCE_CREATION);
        assertFalse(underTest.test(mc));
    }

    @Test
    public void denyCreationOfNonSubmissionsWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", DEPOSIT_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(resource, RESOURCE_CREATION);
        assertFalse(underTest.test(mc));
    }

    @Test
    public void denyModificationsOfNonSubmissionsWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", DEPOSIT_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(resource, RESOURCE_MODIFICATION);
        assertFalse(underTest.test(mc));
    }

    @Test
    public void denyFromSameUserAgent() throws Exception {
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION,
                                                                                "software-agent-equals.json");

        AgentPolicy agentPolicy = mock(AgentPolicy.class);
        when(agentPolicy.test(mc)).thenReturn(false);
        underTest = new SubmissionMessagePolicy(agentPolicy);

        assertFalse(underTest.test(mc));
        verify(agentPolicy).test(mc);
    }

    @Test
    public void acceptFromMissingAgentName() throws Exception {
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION,
                                                                                "software-agent-missing-name.json");
        assertTrue(underTest.test(mc));
    }

    @Test
    public void acceptWhenMissingAttribution() throws Exception {
        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION,
                                                                                "software-agent-missing-object.json");
        assertTrue(underTest.test(mc));
    }

}