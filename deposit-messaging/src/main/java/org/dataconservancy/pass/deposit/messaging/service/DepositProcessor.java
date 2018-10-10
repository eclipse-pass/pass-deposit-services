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

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;

@Component
public class DepositProcessor implements Consumer<Deposit> {

    private static final Logger LOG = LoggerFactory.getLogger(DepositProcessor.class);

    private Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    private Policy<Submission.AggregatedDepositStatus> terminalSubmissionStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private PassClient passClient;

    private DepositTaskHelper depositHelper;

    @Autowired
    public DepositProcessor(Policy<Deposit.DepositStatus> terminalDepositStatusPolicy,
                            Policy<Submission.AggregatedDepositStatus> terminalSubmissionStatusPolicy,
                            CriticalRepositoryInteraction cri,
                            PassClient passClient,
                            DepositTaskHelper depositHelper) {
        this.terminalDepositStatusPolicy = terminalDepositStatusPolicy;
        this.terminalSubmissionStatusPolicy = terminalSubmissionStatusPolicy;
        this.cri = cri;
        this.passClient = passClient;
        this.depositHelper = depositHelper;
    }

    public void accept(Deposit deposit) {

        if (terminalDepositStatusPolicy.accept(deposit.getDepositStatus())) {
            // terminal Deposit status, so update its Submission aggregate deposit status.

            // obtain a critical over the submission
            cri.performCritical(deposit.getSubmission(), Submission.class,

                    /*
                     * The Submission must not be in a terminal state in order for us to update its status
                     */
                    (criSubmission) -> !terminalSubmissionStatusPolicy.accept(criSubmission.getAggregatedDepositStatus()),

                    /*
                     * Any (or no) updates to the Submission are acceptable
                     */
                    (criSubmission) -> true,

                    /*
                     * Update the status of the Submission only if all of its Deposits are in a terminal state
                     */
                    (criSubmission) -> {
                        Collection<Deposit> deposits = passClient.getIncoming(criSubmission.getId())
                                .getOrDefault("submission", Collections.emptySet()).stream()
                                .map((uri) -> {
                                    try {
                                        return passClient.readResource(uri, Deposit.class);
                                    } catch (RuntimeException e) {
                                        // ignore exceptions whose cause is related to type coercion of JSON objects
                                        if (!(e.getCause() instanceof InvalidTypeIdException)) {
                                            throw e;
                                        }

                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        // If all the statuses are terminal, then we can update the aggregated deposit status of
                        // the submission
                        if (deposits.stream().allMatch((criDeposit) ->
                                terminalDepositStatusPolicy.accept(criDeposit.getDepositStatus()))) {
                            if (deposits.stream().allMatch((criDeposit) -> deposit.getDepositStatus() == ACCEPTED)) {
                                criSubmission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.ACCEPTED);
                                LOG.trace(">>>> Updating {} aggregated deposit status to {}", criSubmission.getId(), ACCEPTED);
                            } else {
                                criSubmission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.REJECTED);
                                LOG.trace(">>>> Updating {} aggregated deposit status to {}", criSubmission.getId(),
                                        Submission.AggregatedDepositStatus.REJECTED);
                            }
                        }

                        return criSubmission;
                    });
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
}
