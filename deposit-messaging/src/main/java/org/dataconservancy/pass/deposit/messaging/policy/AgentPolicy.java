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

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.dataconservancy.pass.support.messaging.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <em>Rejects</em> JMS messages that originate from the user agent supplied on construction.
 * <p>
 * The intent of this policy is to reject JMS messages that are emitted due to the actions of Deposit Services on
 * Fedora resources.  If Deposit Services modifies a {@code Submission}, for example, it will receive a JMS notification
 * for that modification.  Currently, there is no need to process those messages, and this policy insures that they will
 * be dropped.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class AgentPolicy implements Policy<DepositUtil.MessageContext> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentPolicy.class);

    private ObjectMapper objectMapper;

    private String depositServicesUserAgent;

    /**
     * Constructs a new policy using the supplied {@code ObjectMapper} for parsing the user agent from the JMS message.
     *
     * @param objectMapper parses the JMS message body
     * @param userAgent    the user agent used by Deposit Services when interacting with the Fedora repository
     */
    public AgentPolicy(ObjectMapper objectMapper, @Value("${pass.deposit.http.agent}") String userAgent) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null.");
        }
        if (userAgent == null || userAgent.trim().length() == 0) {
            throw new IllegalArgumentException("User Agent String must not be null or empty.");
        }
        this.objectMapper = objectMapper;
        this.depositServicesUserAgent = userAgent;
    }

    /**
     * {@inheritDoc}
     * <em>Implementation notes</em>
     * <p>
     * Attempts to resolve the JMS message body for a value of the {@code http://www.w3.org/ns/prov#SoftwareAgent}
     * property.  If the value is {@code null} or does <em>not</em> equal the user agent string supplied on
     * construction, the message is <em>accepted</em>.  If the value is equal to the user agent string supplied on
     * construction, this policy drops the message.
     * </p>
     *
     * @param messageContext {@inheritDoc}
     * @return false if the JMS message user agent is equal to the user agent string supplied on construction
     */
    @Override
    public boolean test(DepositUtil.MessageContext messageContext) {
        JsonNode attribution = null;
        try {
            attribution = objectMapper.readTree(messageContext.message().getPayload().toString()).findValue
                ("wasAttributedTo");
        } catch (IOException e) {
            throw new RuntimeException("Unable to resolve JMS message body: " + e.getMessage(), e);
        }

        if (attribution != null) {
            for (Iterator<JsonNode> itr = attribution.elements(); itr.hasNext(); ) {
                JsonNode node = itr.next();
                if (node.has("type") && node.findValue("type").textValue()
                                            .equals(Constants.Prov.SOFTWARE_AGENT)) {
                    if (node.has("name") && node.findValue("name").textValue()
                                                .equals(depositServicesUserAgent)) {
                        LOG.trace("Dropping message that originated from this agent: {}",
                                  depositServicesUserAgent);
                        return false;
                    } else {
                        return true;
                    }
                }
            }
        }

        // TODO: pluggable null semantic
        return true;
    }

}
