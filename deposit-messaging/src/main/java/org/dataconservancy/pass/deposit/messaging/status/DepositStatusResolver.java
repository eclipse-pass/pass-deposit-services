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

import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;

/**
 * Resolve a deposit status from an object.
 *
 * @param <T> the type of the object that references or contains a deposit status
 * @param <R> the type of the object that represents a deposit status
 */
@FunctionalInterface
public interface DepositStatusResolver<T, R> {


    /**
     * Resolve the deposit status from {@code o}.  If authentication is required to resolve {@code o} (e.g. if {@code o}
     * is a URL that references a deposit status document), the supplied {@code authRealm} will be expected to provide
     * authentication parameters.
     *
     * @param o                the object that contains or references a deposit status
     * @param repositoryConfig the configuration for the downstream repository that the deposit targeted
     * @return the deposit status
     */
    R resolve(T o, RepositoryConfig repositoryConfig);

}
