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

package org.dataconservancy.pass.deposit.transport;

import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;

public interface TransportResponse {

    /**
     * Whether or not transferring the stream of bytes to the target destination succeeded without error.  If an error
     * was encountered transferring the bytes, then {@link #error()} can be invoked to retrieve the stack trace.
     *
     * @return {@code false} if there was an error transferring the byte stream to the target system
     */
    boolean success();

    /**
     * Will contain the {@code Throwable} in the case an error is encountered transferring the bytes to the target
     * system.
     *
     * @return the cause of the error, may be {@code null}
     */
    Throwable error();

    /**
     * Invoked as a callback by Deposit Services after creating or updating PASS repository resources related to the
     * successful transfer of bytes by a Transport.  At a minimum {@link #success()} must return {@code true} for this
     * callback to be invoked.
     * <p>
     * Implementations should not assume that the invocation of {@code onSuccess(...)} implies <em>logical</em> success;
     * nothing should be inferred with respect to the custody of materials.  {@code onSuccess(...)} is invoked on
     * <em>physical</em> success, but that does not imply that a transfer of custody has taken place.
     * </p>
     * <p>
     * Implementations may interrogate and update the PASS repository resources that are supplied by method parameters.
     * For example, a transport implementation may be able to provide identifiers or other metadata that may be useful
     * to record in PASS.
     * </p>
     *
     * @param submission
     * @param deposit
     * @param repositoryCopy
     */
    default void onSuccess(Submission submission, Deposit deposit, RepositoryCopy repositoryCopy) {
        // no-op
    }

}
