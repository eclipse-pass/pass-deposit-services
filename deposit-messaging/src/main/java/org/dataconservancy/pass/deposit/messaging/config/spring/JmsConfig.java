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
package org.dataconservancy.pass.deposit.messaging.config.spring;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.ackMessage;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.toMessageContext;

import java.net.URI;
import java.util.function.Consumer;
import javax.jms.ConnectionFactory;
import javax.jms.Session;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.constants.Constants;
import org.dataconservancy.pass.support.messaging.json.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.JmsHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@EnableJms
public class JmsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JmsConfig.class);

    @Autowired
    private PassClient passClient;

    @Autowired
    private JsonParser jsonParser;

    @Autowired
    @Qualifier("submissionMessagePolicy")
    private JmsMessagePolicy submissionPolicy;

    @Autowired
    @Qualifier("depositMessagePolicy")
    private JmsMessagePolicy depositPolicy;

    @Autowired
    private Consumer<Submission> submissionConsumer;

    @Autowired
    private Consumer<Deposit> depositConsumer;

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(DepositServiceErrorHandler errorHandler,
                                                                          @Value("${spring.jms.listener.concurrency}")
                                                                              String concurrency,
                                                                          @Value("${spring.jms.listener.auto-startup}")
                                                                              boolean autoStart,
                                                                          ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setErrorHandler(errorHandler);
        factory.setConcurrency(concurrency);
        factory.setConnectionFactory(connectionFactory);
        factory.setAutoStartup(autoStart);
        return factory;
    }

    @JmsListener(destination = "${pass.deposit.queue.submission.name}",
                 containerFactory = "jmsListenerContainerFactory")
    public void processSubmissionMessage(@Header(Constants.JmsFcrepoHeader.FCREPO_RESOURCE_TYPE) String resourceType,
                                         @Header(Constants.JmsFcrepoHeader.FCREPO_EVENT_TYPE) String eventType,
                                         @Header(JmsHeaders.TIMESTAMP) long timeStamp,
                                         @Header(JmsHeaders.MESSAGE_ID) String id,
                                         Session session,
                                         Message<String> message,
                                         javax.jms.Message jmsMessage) {

        DepositUtil.MessageContext mc =
            toMessageContext(resourceType, eventType, timeStamp, id, session, message, jmsMessage);

        if (filterMessage(mc, submissionPolicy)) {
            return;
        }

        // Parse the identity of the Submission from the message
        URI submissionUri = null;
        try {
            submissionUri = parseResourceUri(mc, jsonParser);
            submissionConsumer.accept(passClient.readResource(submissionUri, Submission.class));
        } catch (Exception e) {
            LOG.warn("Failed to process Submission ({}) from JMS message: {}\nPayload (if available): '{}'",
                     (submissionUri == null ? "<failed to parse Submission URI from JMS message>" : submissionUri),
                     e.getMessage(), mc.message().getPayload(), e);
        } finally {
            ackMessage(mc);
        }

    }

    @JmsListener(destination = "${pass.deposit.queue.deposit.name}", containerFactory = "jmsListenerContainerFactory")
    public void processDepositMessage(@Header(Constants.JmsFcrepoHeader.FCREPO_RESOURCE_TYPE) String resourceType,
                                      @Header(Constants.JmsFcrepoHeader.FCREPO_EVENT_TYPE) String eventType,
                                      @Header(JmsHeaders.TIMESTAMP) long timeStamp,
                                      @Header(JmsHeaders.MESSAGE_ID) String id,
                                      Session session,
                                      Message<String> message,
                                      javax.jms.Message jmsMessage) {

        DepositUtil.MessageContext mc =
            toMessageContext(resourceType, eventType, timeStamp, id, session, message, jmsMessage);

        if (filterMessage(mc, depositPolicy)) {
            return;
        }

        // Parse the identity of the Deposit from the message
        try {
            URI depositUri = parseResourceUri(mc, jsonParser);
            depositConsumer.accept(passClient.readResource(depositUri, Deposit.class));
        } catch (Exception e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace(
                    "Error processing a JMS message for a 'Deposit' resource {}: {}\nPayload (if available): '{}'",
                    mc.id(), e.getMessage(), mc.message().getPayload(), e);
            } else {
                LOG.error("Error processing a JMS message for a 'Deposit' resource {}: {}", mc.id(), e.getMessage(), e);
            }
        } finally {
            ackMessage(mc);
        }

    }

    /**
     * Determine if the message should be accepted for further processing according to the supplied {@code policy}.
     *
     * @param mc        the message context
     * @param jmsPolicy the policy
     * @return true if the message should be filtered (i.e., <em>not</em> accepted for further processing)
     */
    private static boolean filterMessage(DepositUtil.MessageContext mc, JmsMessagePolicy jmsPolicy) {
        LOG.trace("Processing message (ack mode: {}) {} body:\n{}",
                  mc.ackMode(), mc.id(), mc.message().getPayload());

        // verify the message is one we want, otherwise ack it right away and return
        if (!jmsPolicy.test(mc)) {
            ackMessage(mc);
            return true;
        }
        return false;
    }

    /**
     * Parse the Fedora repository URI of the PASS entity represented in the message.
     *
     * @param mc         the message context
     * @param jsonParser vanilla Jackson JSON parser used to parse the JMS message payload
     * @return the URI of the PASS resource in the Fedora repository
     */
    private static URI parseResourceUri(DepositUtil.MessageContext mc, JsonParser jsonParser) {
        byte[] payload = mc.message().getPayload().getBytes(UTF_8);
        return URI.create(jsonParser.parseId(payload));
    }

}
