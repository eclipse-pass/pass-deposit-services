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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.constants.Constants;
import org.dataconservancy.pass.support.messaging.constants.Constants.JmsFcrepoEvent;
import org.dataconservancy.pass.support.messaging.constants.Constants.PassType;
import org.springframework.stereotype.Component;

/**
 * Accepts messages that represent the creation or modification of a PASS {@link Submission}.  Messages that do not meet
 * this {@code Policy} should be acknowledged immediately, with no further processing taking place.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SubmissionMessagePolicy extends FedoraMessagePolicy {

    private static final Set<FedoraResourceEventType> SUBMISSION_RESOURCE_EVENT_TYPES = Collections.unmodifiableSet(
        new HashSet<FedoraResourceEventType>() {
            {
                add(new FedoraResourceEventType(SUBMISSION_RESOURCE, RESOURCE_CREATION));
                add(new FedoraResourceEventType(SUBMISSION_RESOURCE, RESOURCE_MODIFICATION));
            }
        });

    private AgentPolicy agentPolicy;

    public SubmissionMessagePolicy(AgentPolicy agentPolicy) {
        this.agentPolicy = agentPolicy;
    }

    /**
     * Accepts a message if the following conditions are true:
     * <ul>
     *     <li>{@link Constants.JmsFcrepoHeader#FCREPO_RESOURCE_TYPE} is a {@link PassType#SUBMISSION_RESOURCE}</li>
     *     <li>{@link Constants.JmsFcrepoHeader#FCREPO_EVENT_TYPE} is a {@link JmsFcrepoEvent#RESOURCE_MODIFICATION} or
     *         {@link JmsFcrepoEvent#RESOURCE_CREATION}</li>
     *     <li>The JMS message is accepted according to the {@link AgentPolicy}</li>
     * </ul>
     *
     * @param messageContext the {@code MessageContext} which carries the original JMS message
     * @return {@code true} if the message represents the creation or modification of a {@code Submission}
     */
    @Override
    public boolean test(DepositUtil.MessageContext messageContext) {
        boolean result = super.test(messageContext);
        if (!result) {
            return false;
        }

        return agentPolicy.test(messageContext);
    }

    /**
     * Returns {@code FedoraResourceEventType}s for {@code Submission} creation and modification messages.
     *
     * @return FedoraResourceEventTypes accepting {@code Submission} creation and modification messages
     */
    @Override
    public Collection<FedoraResourceEventType> acceptableFedoraResourceEventTypes() {
        return SUBMISSION_RESOURCE_EVENT_TYPES;
    }

}
