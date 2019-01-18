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
 * Similar to {@link java.util.function.Function}, but allows implementations to throw checked exceptions.
 *
 * @param <T> type of the input to the function
 * @param <R> type of the output of the function
 */
@FunctionalInterface
public interface ExceptionThrowingFunction<T, R> {

    /**
     * Implementations perform a function on input {@code t} and return the result of the function.  Checked exceptions
     * may be thrown by the implementation.
     *
     * @param t the input to the function
     * @return the output of the function
     * @throws Exception if there is an error performing the function
     */
    R apply(T t) throws Exception;

}
