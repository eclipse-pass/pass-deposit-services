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

import org.dataconservancy.pass.model.Submission;

/**
 * Determines if a {@link Submission} is to be processed by Deposit Services.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@FunctionalInterface
public interface SubmissionPolicy extends Policy<Submission> {

    /**
     * Returns {@code true} if the supplied {@code Submission} is suitable for processing.
     *
     * @param submission the Submission
     * @return {@code true} if the Submission is suitable for processing
     */
    boolean test(Submission submission);

}
