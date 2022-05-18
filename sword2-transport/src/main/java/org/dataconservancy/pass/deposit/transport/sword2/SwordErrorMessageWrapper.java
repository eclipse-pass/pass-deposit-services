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

import org.swordapp.client.SWORDError;

/**
 * Wraps a {@link SWORDError} for the purpose of providing a non-null response to {@link Throwable#getMessage()}.
 */
class SwordErrorMessageWrapper extends Exception {

    private SWORDError wrapped;

    /**
     * Wraps the supplied {@code SWORDError} by setting this {@link Throwable#getMessage() Throwable's message} with the
     * {@code SWORDError}'s {@link SWORDError#getErrorBody()}.  The wrapped {@code SWORDError} is available via
     * {@link #getCause()} or {@link #getSwordError()}.
     *
     * @param wrapped the {@code SWORDError} to be wrapped
     */
    SwordErrorMessageWrapper(SWORDError wrapped) {
        this(wrapped.getErrorBody(), wrapped);
    }

    /**
     * Use the supplied {@code message} as this {@link Throwable#getMessage() Throwable's message}.  The wrapped
     * {@code SWORDError} is available via {@link #getCause()} or {@link #getSwordError()}.
     *
     * @param message the message to be returned by {@link Throwable#getMessage()}
     * @param wrapped the {@code SWORDError} to be wrapped
     */
    SwordErrorMessageWrapper(String message, SWORDError wrapped) {
        super(message);

        if (message == null) {
            throw new IllegalArgumentException("Exception message must not be null.");
        }

        if (wrapped == null) {
            throw new IllegalArgumentException("Wrapped SWORDError must not be null.");
        }

        this.wrapped = wrapped;
    }

    /**
     * Use the supplied {@code message} as this {@link Throwable#getMessage() Throwable's message}.  The wrapped
     * {@code SWORDError} is available via {@link #getCause()} or {@link #getSwordError()}.
     *
     * @param message the message to be returned by {@link Throwable#getMessage()}
     * @param cause   the {@code SWORDError} to be wrapped
     */
    SwordErrorMessageWrapper(String message, Throwable cause) {
        super(message, cause);

        if (message == null) {
            throw new IllegalArgumentException("Exception message must not be null.");
        }

        if (cause == null) {
            throw new IllegalArgumentException("SWORDError cause must not be null.");
        }

        if (!(cause instanceof SWORDError)) {
            throw new IllegalArgumentException("Cause must be an instance of SWORDError");
        }

        this.wrapped = (SWORDError) cause;
    }

    /**
     * Use the supplied {@code message} as this {@link Throwable#getMessage() Throwable's message}.  The wrapped
     * {@code SWORDError} is available via {@link #getCause()} or {@link #getSwordError()}.
     *
     * @param message            the message to be returned by {@link Throwable#getMessage()}
     * @param cause              the {@code SWORDError} to be wrapped
     * @param enableSuppression
     * @param writableStackTrace
     */
    SwordErrorMessageWrapper(String message, Throwable cause, boolean enableSuppression,
                             boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        if (message == null) {
            throw new IllegalArgumentException("Exception message must not be null.");
        }

        if (cause == null) {
            throw new IllegalArgumentException("SWORDError cause must not be null.");
        }

        if (!(cause instanceof SWORDError)) {
            throw new IllegalArgumentException("Cause must be an instance of SWORDError");
        }

        this.wrapped = (SWORDError) cause;
    }

    SWORDError getSwordError() {
        return wrapped;
    }
}
