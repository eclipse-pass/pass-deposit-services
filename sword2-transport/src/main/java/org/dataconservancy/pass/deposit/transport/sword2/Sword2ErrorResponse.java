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
import org.swordapp.client.SWORDError;

/**
 * Adapts a {@link SWORDError} as a {@link TransportResponse}.  The underlying {@code SWORDError} is available from
 * {@link #getSwordError()}.
 * <p>
 * This class should be used when the calling code returns a {@code SWORDError}.  If the calling code is returning a
 * non-specific error, then the more generic {@link Sword2ThrowableResponse} should be used instead.
 * </p>
 *
 * @see Sword2ThrowableResponse
 */
public class Sword2ErrorResponse implements TransportResponse {

    private Throwable swordError;

    /**
     * Constructs a {@code Sword2ErrorResponse} from the {@code SWORDError}.  Internally, the {@code swordError} is
     * wrapped by {@link SwordErrorMessageWrapper}.
     *
     * @param swordError the SWORDError
     */
    public Sword2ErrorResponse(SWORDError swordError) {
        if (swordError == null) {
            throw new IllegalArgumentException("SWORDError must not be null.");
        }
        this.swordError = new SwordErrorMessageWrapper(swordError);
    }

    /**
     * Constructs a {@code Sword2ErrorResponse} from the wrapped {@code SWORDError}.
     *
     * @param wrappedError the wrapped SWORDError
     */
    public Sword2ErrorResponse(SwordErrorMessageWrapper wrappedError) {
        if (wrappedError == null) {
            throw new IllegalArgumentException("Wrapped SWORDError must not be null.");
        }
        this.swordError = wrappedError;
    }

    @Override
    public boolean success() {
        return false;
    }

    @Override
    public Throwable error() {
        return swordError;
    }

    /**
     * Return the underlying {@code SWORDError}
     *
     * @return the underlying {@code SWORDError}
     */
    public SWORDError getSwordError() {
        return ((SwordErrorMessageWrapper) swordError).getSwordError();
    }
}
