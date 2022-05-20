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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.springframework.messaging.Message;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class PolicyTestUtil {

    private PolicyTestUtil() {
    }

    static DepositUtil.MessageContext withResourceAndEventType(String resourceType, String eventType)
        throws IOException {
        return withResourceAndEventType(resourceType, eventType, "software-agent-web-browser.json");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static DepositUtil.MessageContext withResourceAndEventType(String resourceType, String eventType, String
        messageBodyResource) throws IOException {
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
                SubmissionMessagePolicyTest.class.getResourceAsStream(messageBodyResource), "UTF-8"));

        return mc;
    }
}
