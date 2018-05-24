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

import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.fedora.UpdateConflictException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.policy.SubmissionPolicy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.deposit.messaging.support.Constants;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.deposit.messaging.support.JsonParser;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.AggregatedDepositStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.support.JmsHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.jms.Session;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.ackMessage;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.toMessageContext;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.IN_PROGRESS;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.NOT_STARTED;

/**
 * Filters and processes incoming JMS messages, and then forwards to the {@link SubmissionProcessor}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class JmsSubmissionProcessor extends SubmissionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JmsSubmissionProcessor.class);


    /**
     *
     *
     * @param passClient
     * @param jsonParser
     * @param submissionBuilder
     * @param packagerRegistry
     * @param repositoryRegistry
     * @param submissionPolicy
     * @param dirtyDepositPolicy
     * @param messagePolicy
     * @param taskExecutor
     * @param depositStatusMapper
     * @param atomStatusParser
     */
    public JmsSubmissionProcessor(PassClient passClient, JsonParser jsonParser, SubmissionBuilder submissionBuilder,
                                  Registry<Packager> packagerRegistry, Registry<Repository> repositoryRegistry,
                                  @Qualifier("passUserSubmittedPolicy") SubmissionPolicy submissionPolicy,
                                  Policy<Deposit.DepositStatus> dirtyDepositPolicy, JmsMessagePolicy messagePolicy,
                                  TaskExecutor taskExecutor,
                                  DepositStatusMapper<SwordDspaceDepositStatus> depositStatusMapper,
                                  DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser,
                                  CriticalRepositoryInteraction critical) {

        super(passClient, jsonParser, submissionBuilder, packagerRegistry, repositoryRegistry, submissionPolicy,
                dirtyDepositPolicy, messagePolicy, taskExecutor, depositStatusMapper, atomStatusParser, critical);

    }

    @JmsListener(destination = "deposit")
    public void processMessage(@Header(Constants.JmsFcrepoHeader.FCREPO_RESOURCE_TYPE) String resourceType,
                               @Header(Constants.JmsFcrepoHeader.FCREPO_EVENT_TYPE) String eventType,
                               @Header(JmsHeaders.TIMESTAMP) long timeStamp,
                               @Header(JmsHeaders.MESSAGE_ID) String id,
                               Session session,
                               Message<String> message,
                               javax.jms.Message jmsMessage) {


        DepositUtil.MessageContext mc = toMessageContext(resourceType, eventType, timeStamp, id, session, message, jmsMessage);
        LOG.trace(">>>> Processing message (ack mode: {}): {} {}", mc.ackMode(), mc.dateTime(), mc.id());
        LOG.trace(">>>> Message {} body:{}", mc.id(), mc.message().getPayload());
        processInternal(mc);
    }

    void processInternal(DepositUtil.MessageContext mc) {
        // verify the message is one we want, otherwise ack it right away and return
        if (!messagePolicy.accept(mc)) {
            ackMessage(mc);
            return;
        }

        // Parse the identity of the Submission from the message

        URI submissionUri;
        try {
            byte[] payload = mc.message().getPayload().getBytes(Charset.forName("UTF-8"));
            submissionUri = URI.create(jsonParser.parseId(payload));
        } catch (Exception e) {
            LOG.error("Error parsing submission URI from message: {}", e.getMessage(), e);
            return;
        } finally {
            ackMessage(mc);
        }

        accept(passClient.readResource(submissionUri, Submission.class));
    }

}
