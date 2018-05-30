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

/**
 * Encapsuates the notion of a <em>terminal</em> status.
 * <p>
 * Implementations that answer {@code true} are indicating that the supplied status represents an endpoint in the state
 * of the status: it is not expected to change in the future.
 * </p>
 * <p>
 * For example, given the possible statuses in an imaginary domain: { initialized, in-progress, complete }, this
 * interface would return {@code false} for {@code initialized} and {@code in-progress}, and {@code true} for {@code
 * complete}
 * </p>
 *
 * @param <T> the type of the object that represents a status
 */
@FunctionalInterface
public interface StatusEvaluator<T> {

    /**
     * Determine if the supplied status represents a <em>terminal</em> state.  Terminal state means that the status is
     * not expected to change in the future.
     *
     * @param status the status
     * @return {@code true} if the status is terminal, {@code false} otherwise
     */
    boolean isTerminal(T status);

}
