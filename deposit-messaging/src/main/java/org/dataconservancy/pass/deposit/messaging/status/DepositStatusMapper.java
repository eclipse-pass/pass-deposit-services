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
package org.dataconservancy.pass.deposit.messaging.status;

import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.RepositoryCopy;

/**
 * Accepts an object representing the status of some entity, and attempts to map the status to a
 * {@link DepositStatus}.  The status of the entity being mapped will have some kind of relationship with
 * a PASS {@code Deposit}.  That is, the value of the returned {@code DepositStatus} is a function of the status of
 * the entity
 * being mapped.
 * <p>
 * The entity being mapped from may be another PASS entity, for example, using a {@link RepositoryCopy#getCopyStatus()
 * RepositoryCopy copyStatus} to determine the status of its {@code Deposit}.  The entity being mapped from may not
 * be a PASS entity.  For example, DSpace provides an Atom feed in response to SWORD deposits, which contain a status
 * string.  This interface can be used to map from the status represented in the Atom feed to a PASS {@code
 * DepositStatus}
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@FunctionalInterface
public interface DepositStatusMapper<T> {

    /**
     * Maps an object from an arbitrary domain to a PASS {@link DepositStatus}.
     *
     * @param statusToMap the domain object containing, or used to determine, deposit status
     * @return the status of {@code Deposit}, or {@code null} if the status cannot be mapped
     */
    DepositStatus map(T statusToMap);

}
