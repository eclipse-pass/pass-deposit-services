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
import static org.dataconservancy.pass.support.messaging.constants.Constants.PassType.DEPOSIT_RESOURCE;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.dataconservancy.pass.model.Deposit;
import org.springframework.stereotype.Component;

/**
 * Accepts messages that represent the creation or modification of a PASS {@link Deposit}.  Messages that do not meet
 * this {@code Policy} should be acknowledged immediately, with no further processing taking place.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DepositMessagePolicy extends FedoraMessagePolicy {

    private static final Set<FedoraResourceEventType> DEPOSIT_RESOURCE_EVENT_TYPES = Collections.unmodifiableSet(
        new HashSet<FedoraResourceEventType>() {
            {
                add(new FedoraResourceEventType(DEPOSIT_RESOURCE, RESOURCE_CREATION));
                add(new FedoraResourceEventType(DEPOSIT_RESOURCE, RESOURCE_MODIFICATION));
            }
        });

    /**
     * Returns {@code FedoraResourceEventType}s for {@code Deposit} creation and modification messages.
     *
     * @return FedoraResourceEventTypes accepting {@code Deposit} creation and modification messages
     */
    @Override
    public Collection<FedoraResourceEventType> acceptableFedoraResourceEventTypes() {
        return DEPOSIT_RESOURCE_EVENT_TYPES;
    }
}
