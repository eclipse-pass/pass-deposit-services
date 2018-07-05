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

package org.dataconservancy.pass.deposit.transport.sword2;

import org.dataconservancy.pass.deposit.transport.TransportResponse;

/**
 * Adapts a {@code Throwable} as a {@code TransportResponse}.
 * <p>
 * If the calling code is returning an instance of {@link org.swordapp.client.SWORDError}, then the more specific
 * {@link Sword2ErrorResponse} should be used instead.
 * </p>
 *
 * @see Sword2ErrorResponse
 */
public class Sword2ThrowableResponse implements TransportResponse {

    private Throwable cause;

    public Sword2ThrowableResponse(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("Cause must not be null.");
        }
        this.cause = cause;
    }

    @Override
    public boolean success() {
        return false;
    }

    @Override
    public Throwable error() {
        return cause;
    }
}
