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

import org.dataconservancy.pass.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accepts {@code DepositStatus} that represents an <em>dirty</em> deposit status.
 * <p>
 * A {@code Deposit} is considered dirty if it has a {@code null} {@code DepositStatus}, therefore, any {@code null}
 * object supplied to this implementation will be accepted by this policy.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DirtyDepositPolicy implements Policy<Deposit.DepositStatus> {

    private static final Logger LOG = LoggerFactory.getLogger(DirtyDepositPolicy.class);

    /**
     * Returns {@code true} if {@code o} is {@code null}.
     *
     * @param o the object being evaluated by this {@code Policy}
     * @return {@code true} if the {@code DepositStatus} of {@code o} is {@code null}
     */
    @Override
    public boolean test(Deposit.DepositStatus o) {
        if (o != null) {
            LOG.debug("Deposit will not be accepted for processing: status = '{}'", o);
            return false;
        }

        return true;
    }
}
