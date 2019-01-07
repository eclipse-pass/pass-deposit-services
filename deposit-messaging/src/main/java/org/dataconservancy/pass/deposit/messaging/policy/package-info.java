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
 * Defines policy abstractions and implementations.
 * <p>
 * Policies are used to determine if JMS messages and {@link org.dataconservancy.pass.model.Submission}s are of interest
 * to Deposit Services.
 * </p>
 * <p> Messages representing the creation or modification of a
 * <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Submission.md">Submission
 * repository resource</a> are considered for further processing by the {@link
 * org.dataconservancy.pass.deposit.messaging.service.SubmissionProcessor}. After retrieving the {@link
 * org.dataconservancy.pass.model.Submission} from the repository, the {@link
 * org.dataconservancy.pass.deposit.messaging.policy.PassUserSubmittedPolicy} determines whether or not the {@code
 * Submission} should be processed by Deposit Services.
 */
package org.dataconservancy.pass.deposit.messaging.policy;