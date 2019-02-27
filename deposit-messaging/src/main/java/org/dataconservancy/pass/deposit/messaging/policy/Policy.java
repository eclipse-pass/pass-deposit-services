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
package org.dataconservancy.pass.deposit.messaging.policy;

import java.util.function.Predicate;

/**
 * Generic interface representing a simple policy.
 *
 * Policies are simply predicates, but the name of the interface represents a semantic that is useful.
 *
 * @param <T> type of object being accepted or rejected by this {@code Policy}
 */
@FunctionalInterface
public interface Policy<T> extends Predicate<T> {

    /**
     * Return {@code true} if this {@code Policy} accepts the supplied object
     *
     * @param o the object being evaluated by this {@code Policy}
     * @return {@code true} if the object is acceptable according to this {@code Policy}
     */
    boolean test(T o);
}
