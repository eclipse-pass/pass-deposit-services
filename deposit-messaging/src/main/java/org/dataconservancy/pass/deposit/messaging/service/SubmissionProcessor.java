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

import org.dataconservancy.nihms.builder.InvalidModel;
import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.policy.SubmissionPolicy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.deposit.messaging.support.JsonParser;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.function.Consumer;

import static java.lang.String.format;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.IN_PROGRESS;

/**
 * Processes an incoming {@code Submission} by composing and submitting a {@link DepositTask} for execution.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Service
public class SubmissionProcessor implements Consumer<Submission> {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionProcessor.class);

    private final String FAILED_TO_PROCESS_DEPOSIT = "Failed to process Deposit for tuple [%s, %s, %s]: %s";

    protected PassClient passClient;

    protected JsonParser jsonParser;

    protected SubmissionBuilder submissionBuilder;

    protected Registry<Packager> packagerRegistry;

    protected SubmissionPolicy submissionPolicy;

    protected JmsMessagePolicy messagePolicy;

    protected TaskExecutor taskExecutor;

    protected DepositStatusMapper<SwordDspaceDepositStatus> depositStatusMapper;

    protected DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser;

    protected Policy<Deposit.DepositStatus> dirtyDepositPolicy;

    protected Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    protected CriticalRepositoryInteraction critical;

    protected DepositTaskHelper depositTaskHelper;

    @Autowired
    public SubmissionProcessor(PassClient passClient, JsonParser jsonParser, SubmissionBuilder submissionBuilder,
                               Registry<Packager> packagerRegistry,
                               SubmissionPolicy passUserSubmittedPolicy,
                               Policy<Deposit.DepositStatus> dirtyDepositPolicy,
                               JmsMessagePolicy submissionMessagePolicy,
                               Policy<Deposit.DepositStatus> terminalDepositStatusPolicy,
                               TaskExecutor taskExecutor, DepositTaskHelper depositTaskHelper,
                               DepositStatusMapper<SwordDspaceDepositStatus> depositStatusMapper,
                               DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser,
                               CriticalRepositoryInteraction critical) {

        this.passClient = passClient;
        this.jsonParser = jsonParser;
        this.submissionBuilder = submissionBuilder;
        this.packagerRegistry = packagerRegistry;
        this.submissionPolicy = passUserSubmittedPolicy;
        this.messagePolicy = submissionMessagePolicy;
        this.taskExecutor = taskExecutor;
        this.depositStatusMapper = depositStatusMapper;
        this.atomStatusParser = atomStatusParser;
        this.dirtyDepositPolicy = dirtyDepositPolicy;
        this.terminalDepositStatusPolicy = terminalDepositStatusPolicy;
        this.critical = critical;
        this.depositTaskHelper = depositTaskHelper;
    }

    public void accept(Submission submission) {

        // Mark the Submission as being IN_PROGRESS immediately.  If this fails, we've essentially lost a JMS message

        CriticalResult<DepositSubmission, Submission> result = critical.performCritical(submission.getId(), Submission.class,
                (s) -> {
                    boolean accepted = submissionPolicy.accept(s);
                    if (!accepted) {
                        LOG.debug(">>>> Update precondition(s) failed for {}", s.getId());
                    }
                    return accepted;
                },
                (s, ds) -> {
                    if (s.getAggregatedDepositStatus() != IN_PROGRESS) {
                        String msg = "Update postcondition failed for %s: expected status '%s' but actual status is " +
                                "'%s'";
                        throw new IllegalStateException(String.format(msg, s.getId(), IN_PROGRESS, s
                                .getAggregatedDepositStatus()));
                    }

                    if (ds.getFiles().size() < 1) {
                        String msg = "Update postcondition failed for %s: the DepositSubmission has no files " +
                                "attached! (Hint: check the incoming links to the Submission)";
                        throw new IllegalStateException(String.format(msg, s.getId()));
                    }

                    return true;
                },
                (s) -> {
                    DepositSubmission ds = null;
                    try {
                        ds = submissionBuilder.build(s.getId().toString());
                    } catch (InvalidModel invalidModel) {
                        throw new RuntimeException(invalidModel.getMessage(), invalidModel);
                    }
                    s.setAggregatedDepositStatus(IN_PROGRESS);
                    return ds;
                });

        if (!result.success()) {
            result.throwable().ifPresent(t -> LOG.debug("Unable to update status of {} to '{}': {}",
                    submission.getId(), IN_PROGRESS, t.getMessage(), t));

            if (result.throwable().isPresent()) {
                Throwable cause = result.throwable().get();
                LOG.warn("Unable to update status of {} to '{}': {}",
                        submission.getId(), IN_PROGRESS, cause.getMessage());
                String msg = format("Unable to update status of %s to '%s': %s",
                        submission.getId(), IN_PROGRESS, cause.getMessage());
                throw new DepositServiceRuntimeException(msg, cause, submission);
            }

            LOG.warn("Unable to update status of {} to '{}': {}",
                    submission.getId(), IN_PROGRESS, "(no throwable provided)");
            String msg = format("Unable to update status of %s to '%s'", submission.getId(), IN_PROGRESS);
            throw new DepositServiceRuntimeException(msg, submission);
        }

        Submission updatedS = result.resource().orElseThrow(() ->
                new DepositServiceRuntimeException("Missing expected Submission " + submission.getId(), submission));

        DepositSubmission depositSubmission = result.result().orElseThrow(() ->
            new DepositServiceRuntimeException("Missing expected DepositSubmission", submission));

        LOG.debug(">>>> Processing Submission {}", submission.getId());

        updatedS.getRepositories().stream().map(repoUri -> passClient.readResource(repoUri, Repository.class))
                .forEach(repo -> {
                    Deposit deposit = null;
                    Packager packager = null;
                    try {
                        deposit = createDeposit(updatedS, repo);
                        packager = packagerRegistry.get(repo.getName());
                        if (packager == null) {
                            throw new NullPointerException(format("No Packager found for tuple [%s, %s, %s]: " +
                                            "Missing Packager for Repository named '%s'",
                                    updatedS.getId(), deposit.getId(), repo.getId(), repo.getName()));
                        }
                        deposit = passClient.createAndReadResource(deposit, Deposit.class);
                    } catch (Exception e) {
                        String msg = format(FAILED_TO_PROCESS_DEPOSIT, updatedS.getId(), repo.getId(),
                                (deposit == null) ? "null" : deposit.getId(), e.getMessage());
                        throw new DepositServiceRuntimeException(msg, e, deposit);
                    }

                    depositTaskHelper.submitDeposit(updatedS, depositSubmission, repo, deposit, packager);
                });
    }

    private Deposit createDeposit(Submission submission, Repository repo) {
        Deposit deposit;
        deposit = new Deposit();
        deposit.setRepository(repo.getId());
        deposit.setSubmission(submission.getId());
        return deposit;
    }

}
