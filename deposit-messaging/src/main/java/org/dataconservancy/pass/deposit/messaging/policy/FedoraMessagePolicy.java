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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.dataconservancy.pass.deposit.messaging.support.Constants;
import org.dataconservancy.pass.deposit.messaging.support.Constants.JmsFcrepoEvent;
import org.dataconservancy.pass.deposit.messaging.support.Constants.JmsFcrepoHeader;
import org.dataconservancy.pass.deposit.messaging.support.Constants.PassType;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Iterator;

import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.isMessageA;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.JmsFcrepoEvent.RESOURCE_CREATION;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.JmsFcrepoEvent.RESOURCE_MODIFICATION;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.PassType.SUBMISSION_RESOURCE;

/**
 * Accepts messages that represent the creation or modification of a PASS {@link Submission}.  Messages that do not meet
 * this {@code Policy} should be acknowledged immediately, with no further processing taking place.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class FedoraMessagePolicy implements JmsMessagePolicy {

    private static final Logger LOG = LoggerFactory.getLogger(FedoraMessagePolicy.class);

    private ObjectMapper objectMapper;

    private String depositServicesUserAgent;

    public FedoraMessagePolicy(ObjectMapper objectMapper, @Value("${pass.deposit.http.agent}") String depositServicesUserAgent) {
        if (depositServicesUserAgent == null || depositServicesUserAgent.trim().length() == 0) {
            throw new IllegalArgumentException("Deposit Services User Agent String must not be null or empty.");
        }
        this.objectMapper = objectMapper;
        this.depositServicesUserAgent = depositServicesUserAgent;
    }

    /**
     * Accepts a message if the {@link JmsFcrepoHeader#FCREPO_RESOURCE_TYPE} is a {@link PassType#SUBMISSION_RESOURCE}
     * and the {@link JmsFcrepoHeader#FCREPO_EVENT_TYPE} is a {@link JmsFcrepoEvent#RESOURCE_MODIFICATION} or {@link
     * JmsFcrepoEvent#RESOURCE_CREATION}.
     *
     * @param messageContext the {@code MessageContext} which carries the original JMS message
     * @return {@code true} if the message represents the creation or modification of a {@code Submission}
     */
    @Override
    public boolean accept(DepositUtil.MessageContext messageContext) {
        if (!isMessageA(RESOURCE_CREATION, SUBMISSION_RESOURCE, messageContext) &&
                !isMessageA(RESOURCE_MODIFICATION, SUBMISSION_RESOURCE, messageContext)) {
            LOG.trace(">>>> Dropping message (not a ({} or {}) or {}): {} {}",
                    RESOURCE_CREATION, RESOURCE_MODIFICATION, SUBMISSION_RESOURCE,
                    messageContext.dateTime(), messageContext.id());
            return false;
        }

        JsonNode attribution = null;
        try {
            attribution = objectMapper.readTree(messageContext.message().getPayload().toString()).findValue
                    ("wasAttributedTo");
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse JMS message body: " + e.getMessage(), e);
        }

        if (attribution != null) {
            for (Iterator<JsonNode> itr = attribution.elements(); itr.hasNext();) {
                JsonNode node = itr.next();
                if (node.has("type") && node.findValue("type").textValue().equals(Constants.Prov.SOFTWARE_AGENT)) {
                    if (node.has("name") && node.findValue("name").textValue().equals(depositServicesUserAgent)) {
                        LOG.trace(">>>> Dropping message that originated from this agent: {}", depositServicesUserAgent);
                        return false;
                    }
                }
            }
        }

        return true;
    }

}
