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
 * org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser} is able to parse a deposit status from an
 * object, and {@link org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper} is able to map a deposit
 * status from domain to another.
 * </p>
 * <p>
 * The most complicated implementation is the {@link
 * org.dataconservancy.pass.deposit.messaging.status.AbstractStatusMapper}, which uses a JSON configuration file located
 * in the classpath as {@code /statusmapping.json}.  The configuration file provides mappings to PASS {@link
 * org.dataconservancy.pass.model.Deposit.DepositStatus} from two different domains: SWORDv2 Atom feeds, and a PASS
 * {@code RepositoryCopy} {@link org.dataconservancy.pass.model.RepositoryCopy#copyStatus copy status}
 * </p>
 * <p>
 * The single implementation of {@link org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser} is the
 * {@link org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomFeedStatusParser}, which is able to parse
 * an Atom feed and return a deposit status.
 * </p>
 */
package org.dataconservancy.pass.deposit.messaging.status;