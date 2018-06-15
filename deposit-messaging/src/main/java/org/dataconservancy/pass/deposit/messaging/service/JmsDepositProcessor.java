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

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.support.Constants;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.JsonParser;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.support.JmsHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.jms.Session;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.ackMessage;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.toMessageContext;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;

@Component
public class JmsDepositProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JmsDepositProcessor.class);

    private JmsMessagePolicy messagePolicy;

    private Policy<Deposit.DepositStatus> terminalStatusPolicy;

    private JsonParser jsonParser;

    private CriticalRepositoryInteraction critical;

    private PassClient passClient;

    @Autowired
    public JmsDepositProcessor(@Qualifier("depositMessagePolicy") JmsMessagePolicy messagePolicy,
                               @Qualifier("terminalDepositStatusPolicy") Policy<Deposit.DepositStatus> statusPolicy,
                               JsonParser jsonParser, CriticalRepositoryInteraction critical, PassClient passClient) {
        this.messagePolicy = messagePolicy;
        this.terminalStatusPolicy = statusPolicy;
        this.jsonParser = jsonParser;
        this.critical = critical;
        this.passClient = passClient;
    }

    @JmsListener(destination = "${pass.deposit.queue.deposit.name}")
    public void processMessage(@Header(Constants.JmsFcrepoHeader.FCREPO_RESOURCE_TYPE) String resourceType,
                               @Header(Constants.JmsFcrepoHeader.FCREPO_EVENT_TYPE) String eventType,
                               @Header(JmsHeaders.TIMESTAMP) long timeStamp,
                               @Header(JmsHeaders.MESSAGE_ID) String id,
                               Session session,
                               Message<String> message,
                               javax.jms.Message jmsMessage) {

        DepositUtil.MessageContext mc = toMessageContext(
                resourceType, eventType, timeStamp, id, session, message, jmsMessage);
        LOG.trace(">>>> Processing message (ack mode: {}) {} body:\n{}",
                mc.ackMode(), mc.id(), mc.message().getPayload());

        // verify the message is one we want, otherwise ack it right away and return
        if (!messagePolicy.accept(mc)) {
            ackMessage(mc);
            return;
        }

        // Parse the identity of the Deposit and Submission from the message
        URI submissionUri;
        try {
            byte[] payload = mc.message().getPayload().getBytes(Charset.forName("UTF-8"));
            URI depositUri = URI.create(jsonParser.parseId(payload));

            // If the status of the incoming Deposit is not terminal, then there's no point in continuing.
            // *All* Deposit resources for a Submission must be terminal before proceeding
            if (! terminalStatusPolicy.accept(passClient.readResource(depositUri, Deposit.class).getDepositStatus())) {
                return;
            }
            submissionUri = passClient.readResource(depositUri, Deposit.class).getSubmission();
        } catch (Exception e) {
            LOG.error("Error parsing deposit URI from message: {}", e.getMessage(), e);
            return;
        } finally {
            ackMessage(mc);
        }

        // obtain a critical over the submission
        critical.performCritical(submissionUri, Submission.class,
                (submission) -> true,
                (submission) -> true,
                (submission) -> {
                    Collection<Deposit> deposits = passClient.getIncoming(submission.getId())
                        .getOrDefault("submission", Collections.emptySet()).stream()
                        .map((uri) -> {
                            try {
                                return passClient.readResource(uri, Deposit.class);
                            } catch (RuntimeException e) {
                                // ignore exceptions whose cause is related to type coercion of JSON objects
                                if (!(e.getCause() instanceof InvalidTypeIdException)) {
                                    throw e;
                                }

                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                    // If all the statuses are terminal, then we can update the aggregated deposit status of
                    // the submission
                    if (deposits.stream().allMatch((deposit) ->
                            terminalStatusPolicy.accept(deposit.getDepositStatus()))) {
                        if (deposits.stream().allMatch((deposit) -> deposit.getDepositStatus() == ACCEPTED)) {
                            submission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.ACCEPTED);
                            LOG.trace(">>>> Updating {} aggregated deposit status to {}", submission.getId(), ACCEPTED);
                        } else {
                            submission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.REJECTED);
                            LOG.trace(">>>> Updating {} aggregated deposit status to {}", submission.getId(),
                                    Submission.AggregatedDepositStatus.REJECTED);
                        }
                    }

                return submission;
        });

    }
}
