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
import static org.dataconservancy.pass.support.messaging.constants.Constants.PassType.SUBMISSION_RESOURCE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AgentPolicyTest {

    private final String AGENT_STRING = "pass-deposit/x.y.z";

    @Test
    public void denyFromSameUserAgent() throws Exception {
        AgentPolicy agentPolicy = mock(AgentPolicy.class);
        when(agentPolicy.test(any())).thenReturn(false);
        AgentPolicy underTest = new AgentPolicy(new ObjectMapper(), AGENT_STRING);

        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION,
                                                                                "software-agent-equals.json");
        assertFalse(underTest.test(mc));
    }

    @Test
    public void acceptFromDifferentUserAgent() throws Exception {
        AgentPolicy agentPolicy = mock(AgentPolicy.class);
        when(agentPolicy.test(any())).thenReturn(false);
        AgentPolicy underTest = new AgentPolicy(new ObjectMapper(), AGENT_STRING);

        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION,
                                                                                "software-agent-not-equal.json");
        assertTrue(underTest.test(mc));
    }

    @Test
    public void acceptAgentMissingName() throws Exception {
        AgentPolicy agentPolicy = mock(AgentPolicy.class);
        when(agentPolicy.test(any())).thenReturn(false);
        AgentPolicy underTest = new AgentPolicy(new ObjectMapper(), AGENT_STRING);

        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION,
                                                                                "software-agent-missing-name.json");
        assertTrue(underTest.test(mc));
    }

    @Test
    public void acceptAgentMissingObject() throws Exception {
        AgentPolicy agentPolicy = mock(AgentPolicy.class);
        when(agentPolicy.test(any())).thenReturn(false);
        AgentPolicy underTest = new AgentPolicy(new ObjectMapper(), AGENT_STRING);

        DepositUtil.MessageContext mc = PolicyTestUtil.withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION,
                                                                                "software-agent-missing-object.json");
        assertTrue(underTest.test(mc));
    }
}