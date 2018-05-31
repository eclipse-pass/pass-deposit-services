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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.dataconservancy.pass.deposit.messaging.support.Constants.JmsFcrepoEvent.RESOURCE_CREATION;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.JmsFcrepoEvent.RESOURCE_MODIFICATION;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.PassType.SUBMISSION_RESOURCE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class FedoraMessagePolicyTest {

    private static final String DEPOSIT_RESOURCE = "http://oapass.org/ns/pass#Deposit";

    private FedoraMessagePolicy underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new FedoraMessagePolicy(new ObjectMapper(), "pass-deposit/x.y.z");
    }

    @Test
    public void acceptCreationOfSubmission() throws Exception {
        DepositUtil.MessageContext mc = withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION);
        assertTrue(underTest.accept(mc));
    }

    @Test
    public void acceptModificationOfSubmission() throws Exception {
        DepositUtil.MessageContext mc = withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION);
        assertTrue(underTest.accept(mc));
    }

    @Test
    public void acceptCreationOfSubmissionWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", SUBMISSION_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = withResourceAndEventType(resource, RESOURCE_CREATION);
        assertTrue(underTest.accept(mc));
    }

    @Test
    public void acceptModificationOfSubmissionWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", SUBMISSION_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = withResourceAndEventType(resource, RESOURCE_MODIFICATION);
        assertTrue(underTest.accept(mc));
    }

    @Test
    public void denyModificationOfNonSubmissions() throws Exception {
        DepositUtil.MessageContext mc = withResourceAndEventType(DEPOSIT_RESOURCE, RESOURCE_CREATION);
        assertFalse(underTest.accept(mc));
    }

    @Test
    public void denyCreationOfNonSubmissions() throws Exception {
        DepositUtil.MessageContext mc = withResourceAndEventType(DEPOSIT_RESOURCE, RESOURCE_CREATION);
        assertFalse(underTest.accept(mc));
    }

    @Test
    public void denyCreationOfNonSubmissionsWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", DEPOSIT_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = withResourceAndEventType(resource, RESOURCE_CREATION);
        assertFalse(underTest.accept(mc));
    }

    @Test
    public void denyModificationsOfNonSubmissionsWithMultipleResources() throws Exception {
        String resource = String.format("%s, %s, %s", "http://foo/bar", DEPOSIT_RESOURCE, "http://biz/baz");
        DepositUtil.MessageContext mc = withResourceAndEventType(resource, RESOURCE_MODIFICATION);
        assertFalse(underTest.accept(mc));
    }

    @Test
    public void denyFromSameUserAgent() throws Exception {
        DepositUtil.MessageContext mc = withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION, "software-agent-equals.json");
        assertFalse(underTest.accept(mc));
    }

    @Test
    public void acceptFromMissingAgentName() throws Exception {
        DepositUtil.MessageContext mc = withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION, "software-agent-missing-name.json");
        assertTrue(underTest.accept(mc));
    }

    @Test
    public void acceptWhenMissingAttribution() throws Exception {
        DepositUtil.MessageContext mc = withResourceAndEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION, "software-agent-missing-object.json");
        assertTrue(underTest.accept(mc));
    }

    private static DepositUtil.MessageContext withResourceAndEventType(String resourceType, String eventType) throws IOException {
        return withResourceAndEventType(resourceType, eventType, "software-agent-web-browser.json");
    }

    private static DepositUtil.MessageContext withResourceAndEventType(String resourceType, String eventType,
                                                                       String messageBodyResource) throws IOException {
        DepositUtil.MessageContext mc = mock(DepositUtil.MessageContext.class);

        when(mc.eventType()).thenReturn(eventType);
        when(mc.resourceType()).thenReturn(resourceType);

        Instant now = Instant.now();
        when(mc.timestamp()).thenReturn(now.toEpochMilli());
        String formattedNow = DateTimeFormatter.ISO_DATE_TIME.format(now.atZone(ZoneId.of("UTC")));
        when(mc.dateTime()).thenReturn(formattedNow);

        when(mc.id()).thenReturn(UUID.randomUUID().toString());

        Message message = mock(Message.class);
        when(mc.message()).thenReturn(message);

        when(message.getPayload()).thenReturn(
                IOUtils.toString(
                        FedoraMessagePolicyTest.class.getResourceAsStream(messageBodyResource), "UTF-8"));

        return mc;
    }


}