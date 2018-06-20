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

import org.dataconservancy.pass.model.Submission;
import org.springframework.stereotype.Component;

/**
 * Determines if a PASS {@link org.dataconservancy.pass.model.Submission.AggregatedDepositStatus} is <em>terminal</em>
 * or not.
 * <p>
 * <strong>N.B.</strong> {@code null} is <em>not</em> considered a status to be evaluated
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SubmissionStatusEvaluator implements StatusEvaluator<Submission.AggregatedDepositStatus> {

    /**
     * Determine if {@code status} is in a <em>terminal</em> state, {@link Submission.AggregatedDepositStatus#ACCEPTED}
     * or {@link Submission.AggregatedDepositStatus#REJECTED}
     *
     * @param status the status the PASS {@code DepositStatus}
     * @return {@code true} if the status is terminal
     * @throws IllegalArgumentException if {@code status} is {@code null}
     */
    @Override
    public boolean isTerminal(Submission.AggregatedDepositStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Submission status must not be null.");
        }

        switch (status) {
            case ACCEPTED:
            case REJECTED:
                return true;
            default:
                return false;
        }
    }
}
