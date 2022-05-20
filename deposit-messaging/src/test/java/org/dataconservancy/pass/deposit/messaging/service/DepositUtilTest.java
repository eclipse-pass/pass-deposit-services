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

import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.UNKNOWN_DATETIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import javax.jms.Session;

import org.junit.Test;
import org.springframework.boot.autoconfigure.jms.JmsProperties;
import org.springframework.messaging.Message;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositUtilTest {

    @Test
    public void testIsMessageA() {
        DepositUtil.MessageContext mc = mock(DepositUtil.MessageContext.class);
        when(mc.eventType()).thenReturn("event");
        when(mc.resourceType()).thenReturn("resource");

        assertTrue(DepositUtil.isMessageA("event", "resource", mc));
        assertFalse(DepositUtil.isMessageA("event", "foo", mc));
        assertFalse(DepositUtil.isMessageA("false", "resource", mc));
    }

    @Test
    public void testIsMessageAMultiValued() throws Exception {
        DepositUtil.MessageContext mc = mock(DepositUtil.MessageContext.class);
        when(mc.eventType()).thenReturn("event1,event2");
        when(mc.resourceType()).thenReturn("resource1,resource2,resource3");

        assertTrue(DepositUtil.isMessageA("event1", "resource3", mc));
        assertFalse(DepositUtil.isMessageA("event2", "foo", mc));
        assertFalse(DepositUtil.isMessageA("even1", "resource2", mc));
    }

    @Test
    public void testContains() throws Exception {
        assertTrue(DepositUtil.csvStringContains("event", "event"));
        assertTrue(DepositUtil.csvStringContains("event", "foo, event"));
        assertFalse(DepositUtil.csvStringContains("event", "foo"));
        assertFalse(DepositUtil.csvStringContains("event", "foo, bar"));
        assertFalse(DepositUtil.csvStringContains("event", ""));
        assertFalse(DepositUtil.csvStringContains("event", null));
    }

    @Test
    public void parseTimestamp() throws Exception {
        String dateTime = DepositUtil.parseDateTime(Instant.now().toEpochMilli());
        assertNotNull(dateTime);
        assertFalse(UNKNOWN_DATETIME.equals(dateTime));
    }

    @Test
    public void parseNegativeTimestamp() throws Exception {
        assertEquals(UNKNOWN_DATETIME, DepositUtil.parseDateTime(-1));
    }

    @Test
    public void asAcknowledgeModeValid() throws Exception {
        assertEquals(JmsProperties.AcknowledgeMode.AUTO, DepositUtil.asAcknowledgeMode(Session.AUTO_ACKNOWLEDGE));
        assertEquals(JmsProperties.AcknowledgeMode.CLIENT, DepositUtil.asAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE));
        assertEquals(JmsProperties.AcknowledgeMode.DUPS_OK, DepositUtil.asAcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE));
    }

    @Test(expected = RuntimeException.class)
    public void asAcknowledgeModeInvalid() throws Exception {
        DepositUtil.asAcknowledgeMode(-1);
    }

    @Test
    public void parseAckMode() throws Exception {
        Session session = mock(Session.class);
        when(session.getAcknowledgeMode())
            .thenReturn(Session.AUTO_ACKNOWLEDGE)
            .thenReturn(Session.CLIENT_ACKNOWLEDGE)
            .thenReturn(Session.DUPS_OK_ACKNOWLEDGE)
            .thenReturn(-1);

        assertEquals("AUTO", DepositUtil.parseAckMode(session, null, null));
        assertEquals("CLIENT", DepositUtil.parseAckMode(session, null, null));
        assertEquals("DUPS_OK", DepositUtil.parseAckMode(session, null, null));
        assertEquals("UNKNOWN", DepositUtil.parseAckMode(session, null, null));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void toMessageContext() throws Exception {
        String rType = "resource_type";
        String eType = "event_type";
        long now = Instant.now().toEpochMilli();
        String id = "id";
        Session s = mock(Session.class);
        Message m = mock(Message.class);
        javax.jms.Message jms = mock(javax.jms.Message.class);

        DepositUtil.MessageContext mc = DepositUtil.toMessageContext(rType, eType, now, id, s, m, jms);

        assertEquals(rType, mc.resourceType());
        assertEquals(eType, mc.eventType());
        assertEquals(now, mc.timestamp());
        assertEquals(id, mc.id());
        assertEquals(s, mc.session());
        assertEquals(m, mc.message());
        assertEquals(jms, mc.jmsMessage());
        assertEquals("UNKNOWN", mc.ackMode());
        assertNotNull(mc.dateTime());
    }

    @Test
    public void ackMessage() throws Exception {
        javax.jms.Message jmsMessage = mock(javax.jms.Message.class);
        DepositUtil.MessageContext mc = DepositUtil.toMessageContext("", "", -1, null, null, null, jmsMessage);

        DepositUtil.ackMessage(mc);

        verify(jmsMessage).acknowledge();
    }
}