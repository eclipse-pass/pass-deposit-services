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

import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.NOT_STARTED;
import static org.dataconservancy.pass.model.Submission.Source.PASS;

import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Accepts {@link Submission}s that are submitted by a user of the PASS UI.
 * <p>
 * Uses the {@link Submission#getSubmitted() getSubmitted} flag and {@link Submission#getSource() source} to determine
 * if a {@link Submission} is acceptable for processing.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see
 * <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Submission.md">Submission model documentation</a>
 */
@Component
public class PassUserSubmittedPolicy implements SubmissionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(PassUserSubmittedPolicy.class);

    /**
     * Returns {@code true} if the {@code Submission} was submitted using the PASS UI, and if the user of the UI has
     * interactively pressed the "Submit" button.
     *
     * @param submission the Submission
     * @return {@code true} if the {@code Submission} was submitted by a user of the PASS UI
     * @see
     * <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Submission.md">Submission model documentation</a>
     */
    @Override
    public boolean test(Submission submission) {

        if (submission == null) {
            LOG.debug("Null submissions not accepted for processing.");
            return false;
        }

        if (submission.getSubmitted() != null && submission.getSubmitted() == Boolean.FALSE) {
            LOG.debug("Submission {} will not be accepted for processing: submitted = {}, expected submitted = true",
                      submission.getId(), submission.getSubmitted());
            return false;
        }

        if (submission.getSource() != PASS) {
            LOG.debug("Submission {} will not be accepted for processing: source = {}, expected source = {}",
                      submission.getId(), submission.getSource(), PASS);
            return false;
        }

        // Currently we dis-allow FAILED Submissions; the SubmissionProcessor is not capable of "re-processing"
        // failures.
        if (submission.getAggregatedDepositStatus() != NOT_STARTED) {
            LOG.debug("Submission {} will not be accepted for processing: status = {}, expected status = {}",
                      submission.getId(), submission.getAggregatedDepositStatus(), NOT_STARTED);
            return false;
        }

        return true;
    }
}
