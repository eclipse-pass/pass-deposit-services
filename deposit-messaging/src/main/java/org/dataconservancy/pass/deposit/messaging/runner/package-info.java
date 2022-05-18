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
 * Provides classes used by Spring Boot to run components of Deposit Services.
 * <p>
 * Runners are used to identify and process {@code Deposit} resources that are in an unknown (i.e. <em>dirty</em>) or
 * <em>intermediate</em> state, in an effort to determine their <em>terminal</em> state.
 * </p>
 * <p>
 * The {@link org.dataconservancy.pass.deposit.messaging.runner.FailedDepositRunner} identifies and processes
 * <em>dirty</em> {@link org.dataconservancy.pass.model.Deposit}s by re-submitting the {@code Deposit} and its
 * associated {@code Submission} as a {@link org.dataconservancy.pass.deposit.messaging.service.DepositTask}.
 * </p>
 * <p>
 * The {@link org.dataconservancy.pass.deposit.messaging.runner.SubmittedUpdateRunner} identifies and processes {@link
 * org.dataconservancy.pass.model.Deposit}s in an <em>intermediate</em> (e.g.
 * {@link org.dataconservancy.pass.model.Deposit.DepositStatus#SUBMITTED}) state, and attempts to resolve their
 * <em>terminal</em> by inspecting deposit status references or any {@link
 * org.dataconservancy.pass.model.RepositoryCopy} {@link org.dataconservancy.pass.model.RepositoryCopy.CopyStatus}
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see
 * <a href="https://docs.spring.io/spring-boot/docs/2.0.1.RELEASE/reference/htmlsingle/#boot-features-command-line-runner">Spring Boot Application Runner</a>
 */
package org.dataconservancy.pass.deposit.messaging.runner;