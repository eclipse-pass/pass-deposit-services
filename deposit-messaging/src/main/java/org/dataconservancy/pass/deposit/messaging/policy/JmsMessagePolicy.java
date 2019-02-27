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

import org.dataconservancy.pass.deposit.messaging.service.DepositUtil.MessageContext;

/**
 * Determines if a JMS message is to be processed by Deposit Services.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@FunctionalInterface
public interface JmsMessagePolicy extends Policy<MessageContext> {

    /**
     * Returns {@code true} if the message in the supplied {@link MessageContext} is to be processed further.
     *
     * @param messageContext the {@code MessageContext} which carries the original JMS message
     * @return {@code true} if the message is to be processed further
     */
    boolean test(MessageContext messageContext);

}
