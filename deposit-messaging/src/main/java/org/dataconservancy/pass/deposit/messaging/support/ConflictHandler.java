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
package org.dataconservancy.pass.deposit.messaging.support;

import org.dataconservancy.pass.model.PassEntity;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@code ConflictHandler} is invoked when a {@code 409 Conflict} or {@code 412 Precondition Failed} is returned in
 * response to a {@code PATCH}.
 * <p>
 * The {@link org.dataconservancy.pass.client.PassClient} includes an {@code If-Match} header on {@code PATCH} requests
 * to the Fedora repository.  When {@code 412 Precondition Failed} is returned, this handler is invoked to resolve the
 * conflict.  A naive implementation may invoke a back-off and re-try the request.
 * </p>
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface ConflictHandler {

    /**
     * Invoked when a {@code 412 Precondition Failed} is returned from a {@code PATCH} request to the repository.
     * <p>
     * Implementations are provided the the resource that includes the state to be updated, the function that performs
     * the update, and the precondition that ought to be satisfied prior to invoking the update function.
     * </p>
     * <p>
     * Because this handler is being invoked in response to a {@code 412}, simply re-submitting the {PATCH} request with
     * the supplied {@code resource} will <em>always</em> fail.  Implementations will need to re-retrieve the the latest
     * state of the resource, optionally apply the {@code preCondition} (insuring that the new state of the resource is
     * still valid with respect to the {@code criticalUpdate} to be applied), and invoke the {@code criticalUpdate}.
     * </p>
     *
     * @param conflictedResource the resource with the state to be updated
     * @param resourceClass the runtime class of the resource
     * @param preCondition the precondition that must be satisfied in order for the {@code criticalUpdate} to be applied
     * @param criticalUpdate the update to be applied to the resource
     * @param <T> the type of resource
     * @param <R> the type of the response returned by the {@code criticalUpdate}
     * @return the return from the {@code criticalUpdate}
     */
    <T extends PassEntity, R> R handleConflict(T conflictedResource, Class<T> resourceClass,
                                               Predicate<T> preCondition, Function<T, R> criticalUpdate);

}
