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

/**
 * Provides interfaces for evaluating and the status of a "deposit" in arbitrary domains.
 * <p>
 * {@link org.dataconservancy.pass.deposit.messaging.status.StatusEvaluator} determines if a particular status is in its
 * terminal state; that is, the status will not change in the future by any automated process.  {@link
 * org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver} is able to resolve a deposit status from an
 * object, and {@link org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper} is able to map a deposit
 * status from domain to another.
 * </p>
 */
package org.dataconservancy.pass.deposit.messaging.status;