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

import org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.StatusEvaluator;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Accepts {@link Submission}s that are submitted by a user of the PASS UI.
 * <p>
 * Uses the {@link Submission#getSubmitted() getSubmitted} flag and {@link Submission#getSource() source} to determine
 * if a {@link Submission} is acceptable for processing.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Submission.md">Submission model documentation</a>
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
     * @see <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Submission.md">Submission model documentation</a>
     */
    @Override
    public boolean accept(Submission submission) {

        if (submission == null) {
            LOG.debug(">>>> Not accepting a null submission for processing.");
            return false;
        }

        if (submission.getSubmitted() != null && submission.getSubmitted() == Boolean.FALSE) {
            LOG.debug(">>>> Submission {} will not be accepted for processing: submitted = {}",
                    submission.getId(), submission.getSubmitted());
            return false;
        }

        if (submission.getSource() != Submission.Source.PASS) {
            LOG.debug(">>>> Submission {} will not be accepted for processing: source = {}",
                    submission.getId(), submission.getSource());
            return false;
        }

        // Allow FAILED and NOT_STARTED Submissions to be processed.
        if (submission.getAggregatedDepositStatus() != Submission.AggregatedDepositStatus.NOT_STARTED &&
                submission.getAggregatedDepositStatus() != Submission.AggregatedDepositStatus.FAILED) {
            LOG.debug(">>>> Submission {} will not be accepted for processing: status = {}",
                    submission.getId(), submission.getAggregatedDepositStatus());
            return false;
        }

        return true;
    }
}
