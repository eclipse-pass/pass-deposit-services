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

package org.dataconservancy.deposit.util.function;

/**
 * Performs an operation over some internal state and returns the result.  Implementations may throw checked exceptions.
 *
 * @param <T> the type of the output of the function
 */
@FunctionalInterface
public interface ExceptionThrowingCommand<T> {

    /**
     * Implementations perform some operation over internal state and return a result.
     *
     * @return the result of the operation
     * @throws Exception if there is an error performing the command
     */
    T perform() throws Exception;

}
