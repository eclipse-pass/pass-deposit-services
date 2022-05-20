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
package org.dataconservancy.pass.deposit.messaging;

import org.dataconservancy.pass.model.PassEntity;

/**
 * Base {@code RuntimeException} thrown by deposit services.  Most, if not all, exceptions thrown by deposit services
 * happen in the context of updating the state of a repository resource such as a {@code Submission} or {@code Deposit}.
 * This class provides access to the resource that was being updated, and allows any exception handlers to perform
 * remediation related to the resource.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositServiceRuntimeException extends RuntimeException {

    /**
     * The resource that was the subject of the interaction with the repository.
     */
    private PassEntity resource;

    /**
     * Constructs a new exception that relates to the supplied {@code resource}
     *
     * @param resource the repository resource that was the subject of a repository interaction
     */
    public DepositServiceRuntimeException(PassEntity resource) {
        this.resource = resource;
    }

    /**
     * Constructs a new exception that relates to the supplied {@code resource}
     *
     * @param message  exception message
     * @param resource the repository resource that was the subject of a repository interaction
     */
    public DepositServiceRuntimeException(String message, PassEntity resource) {
        super(message);
        this.resource = resource;
    }

    /**
     * Constructs a new exception that relates to the supplied {@code resource}
     *
     * @param message  exception message
     * @param cause    the cause of this exception
     * @param resource the repository resource that was the subject of a repository interaction
     */
    public DepositServiceRuntimeException(String message, Throwable cause, PassEntity resource) {
        super(message, cause);
        this.resource = resource;
    }

    /**
     * Constructs a new exception that relates to the supplied {@code resource}
     *
     * @param cause    the cause of this exception
     * @param resource the repository resource that was the subject of a repository interaction
     */
    public DepositServiceRuntimeException(Throwable cause, PassEntity resource) {
        super(cause);
        this.resource = resource;
    }

    /**
     * Obtains the resource that was the subject of the repository interaction resulting in this exception.
     *
     * @return the repository resource
     */
    public PassEntity getResource() {
        return resource;
    }

}
