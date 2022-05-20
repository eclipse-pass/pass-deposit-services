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

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.isMessageA;

import java.util.Collection;
import java.util.Objects;

import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides common logic for accepting JMS messages from Fedora based on the resource and event type.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class FedoraMessagePolicy implements JmsMessagePolicy {

    private static final Logger LOG = LoggerFactory.getLogger(FedoraMessagePolicy.class);

    /**
     * Accepts a message if the JMS message is one of the {@link #acceptableFedoraResourceEventTypes() acceptable}
     * {@link FedoraResourceEventType}.
     *
     * @param messageContext the {@code MessageContext} which carries the original JMS message
     * @return {@code true} if the message is one of the acceptable {@code FedoraResourceEventType}s
     */
    @Override
    public boolean test(DepositUtil.MessageContext messageContext) {
        // the message must be one of these FedoraResourceEventTypes
        boolean result = acceptableFedoraResourceEventTypes().stream().anyMatch((ret) ->
                                                                                    isMessageA(ret.EVENT_TYPE,
                                                                                               ret.RESOURCE_TYPE,
                                                                                               messageContext));

        if (!result) {
            LOG.trace("Dropping message {}, it did not match any of the acceptable " +
                      "FedoraResourceEventTypes {}: was {}",
                      messageContext.id(),
                      acceptableFedoraResourceEventTypes().stream().map(Objects::toString).collect(joining(",")),
                      format(FedoraResourceEventType.FMT, messageContext.resourceType(), messageContext.eventType()));
            return false;
        }

        return true;
    }

    /**
     * Subclasses are expected to return a {@code Collection} of {@link FedoraResourceEventType}s that this message
     * policy should accept.
     *
     * @return acceptable {@code FedoraResourceEventType}s, may be empty but never {@code null}
     */
    public abstract Collection<FedoraResourceEventType> acceptableFedoraResourceEventTypes();

    /**
     * Tuple representing a Fedora resource type and event type.
     */
    public static class FedoraResourceEventType {
        private static final String FMT = "[%s, %s]";
        public String RESOURCE_TYPE;
        public String EVENT_TYPE;

        public FedoraResourceEventType(String RESOURCE_TYPE, String EVENT_TYPE) {
            this.RESOURCE_TYPE = RESOURCE_TYPE;
            this.EVENT_TYPE = EVENT_TYPE;
        }

        @Override
        public String toString() {
            return format(FMT, RESOURCE_TYPE, EVENT_TYPE);
        }
    }

}
