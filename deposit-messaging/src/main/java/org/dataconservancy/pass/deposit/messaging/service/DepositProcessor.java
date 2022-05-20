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

package org.dataconservancy.pass.deposit.messaging.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.AggregatedDepositStatus;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DepositProcessor implements Consumer<Deposit> {

    public static final String SUBMISSION_REL = "submission";

    private static final Logger LOG = LoggerFactory.getLogger(DepositProcessor.class);

    private Policy<DepositStatus> terminalDepositStatusPolicy;

    private Policy<AggregatedDepositStatus> intermediateSubmissionStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private PassClient passClient;

    private DepositTaskHelper depositHelper;

    @Autowired
    public DepositProcessor(Policy<DepositStatus> terminalDepositStatusPolicy,
                            Policy<AggregatedDepositStatus> intermediateSubmissionStatusPolicy,
                            CriticalRepositoryInteraction cri,
                            PassClient passClient,
                            DepositTaskHelper depositHelper) {
        this.terminalDepositStatusPolicy = terminalDepositStatusPolicy;
        this.intermediateSubmissionStatusPolicy = intermediateSubmissionStatusPolicy;
        this.cri = cri;
        this.passClient = passClient;
        this.depositHelper = depositHelper;
    }

    public void accept(Deposit deposit) {

        if (terminalDepositStatusPolicy.test(deposit.getDepositStatus())) {
            // terminal Deposit status, so update its Submission aggregate deposit status.
            cri.performCritical(deposit.getSubmission(), Submission.class,
                                DepositProcessorCriFunc.precondition(intermediateSubmissionStatusPolicy),
                                DepositProcessorCriFunc.postcondition(),
                                DepositProcessorCriFunc.critical(passClient, terminalDepositStatusPolicy));
        } else {
            // intermediate status, process the Deposit depositStatusRef

            // determine the RepositoryConfig for the Deposit
            // retrieve and invoke the DepositStatusProcessor from the RepositoryConfig
            //   - requires Collection<AuthRealm> and StatusMapping

            // if result is still intermediate, add Deposit to queue for processing?  Or just process from an ES query?
            //   - ES query prioritized?  What if ES query/queue is processed at the same time? Need to do w/in CRI

            // Determine the logical success or failure of the Deposit, and persist the Deposit and RepositoryCopy in
            // the Fedora repository
            depositHelper.processDepositStatus(deposit.getId());
        }
    }

    static class DepositProcessorCriFunc {

        /**
         * Answers a Predicate that accepts the Submission for processing if it satisfies the supplied Policy.  In
         * practice, the Policy must accept Submissions with an intermediate deposit status, and reject those with a
         * terminal status.
         *
         * @param intermediateStatusPolicy a Policy that accepts Submissions with an intermediate status, and rejects
         *                                 those with a terminal status
         * @return the Predicate that applies the supplied policy to the Submission
         */
        static Predicate<Submission> precondition(Policy<AggregatedDepositStatus> intermediateStatusPolicy) {
            return (criSubmission) -> intermediateStatusPolicy.test(criSubmission.getAggregatedDepositStatus());
        }

        /**
         * The critical function may or may not modify the state of the Submission, so this answers a Predicate that
         * always returns {@code true}.
         *
         * @return a Predicate that always returns {@code true}
         */
        static Predicate<Submission> postcondition() {
            return (criSubmission) -> true;
        }

        /**
         * Answers a Function that updates the {@link Submission.AggregatedDepositStatus} of the {@code Submission} if
         * all of the Deposits attached to the Submission are in a terminal state.
         * <p>
         * If any Deposit attached to the Submission is in an intermediate state, no modifications are made to the
         * Submission.AggregatedDepositStatus.
         * </p>
         * <p>
         * If all Deposits attached to the Submission have a terminal DepositStatus.ACCEPTED state, then the Submission
         * AggregatedDepositStatus is updated to ACCEPTED.
         * </p>
         * <p>
         * If all Deposits attached to the Submission have a terminal DepositStatus.REJECTED state, then the Submission
         * AggregatedDepositStatus is updated to REJECTED.
         * </p>
         *
         * @param passClient           used to query the PASS repository for resources
         * @param terminalStatusPolicy a Policy that accepts DepositStatuses in a terminal state.
         * @return the critical function that may modify the Submission.AggregatedDepositStatus based on its Deposits
         */
        static Function<Submission, Submission> critical(PassClient passClient,
                                                         Policy<DepositStatus> terminalStatusPolicy) {
            return (criSubmission) -> {

                // Collect Deposits that are attached to the Submission using incoming links
                // This avoids issues related to querying the index for Deposits
                Collection<Deposit> deposits = passClient
                        .getIncoming(criSubmission.getId())
                        .getOrDefault(SUBMISSION_REL, Collections.emptySet())
                        .stream()
                        .map((uri) -> {
                            try {
                                return passClient.readResource(uri, Deposit.class);
                            } catch (RuntimeException e) {
                                // ignore exceptions whose cause is related to type
                                // coercion of JSON objects
                                if (!(e.getCause() instanceof InvalidTypeIdException)) {
                                    throw e;
                                }

                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (deposits.isEmpty()) {
                    return criSubmission;
                }

                // If all the statuses are terminal, then we can update the aggregated deposit status of
                // the submission
                if (deposits.stream()
                            .allMatch((criDeposit) -> terminalStatusPolicy.test(criDeposit.getDepositStatus()))) {

                    if (deposits.stream()
                                .allMatch((criDeposit) -> DepositStatus.ACCEPTED == criDeposit.getDepositStatus())) {
                        criSubmission.setAggregatedDepositStatus(AggregatedDepositStatus.ACCEPTED);
                        LOG.debug("Updating {} aggregated deposit status to {}", criSubmission.getId(),
                                  DepositStatus.ACCEPTED);
                    } else {
                        criSubmission.setAggregatedDepositStatus(AggregatedDepositStatus.REJECTED);
                        LOG.debug("Updating {} aggregated deposit status to {}", criSubmission.getId(),
                                  AggregatedDepositStatus.REJECTED);
                    }
                }

                return criSubmission;
            };
        }
    }
}
