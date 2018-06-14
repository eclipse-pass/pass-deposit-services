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

import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.policy.SubmissionPolicy;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil.DepositWorkerContext;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.JsonParser;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.toDepositWorkerContext;

/**
 * Encapsulates functionality common to performing the submission of a Deposit to the TaskExecutor.
 * <p>
 * This functionality is useful when creating <em>new</em> Deposits, as well as re-trying existing Deposits which have
 * failed.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DepositTaskHelper {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionProcessor.class);

    private final String FAILED_TO_PROCESS_DEPOSIT = "Failed to process Deposit for tuple [%s, %s, %s]: %s";

    protected PassClient passClient;

    protected Registry<Packager> packagerRegistry;

    protected TaskExecutor taskExecutor;

    protected DepositStatusMapper<SwordDspaceDepositStatus> depositStatusMapper;

    protected DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser;

    protected Policy<Deposit.DepositStatus> dirtyDepositPolicy;

    protected Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    protected CriticalRepositoryInteraction critical;

    @Autowired
    public DepositTaskHelper(PassClient passClient, Registry<Packager> packagerRegistry, TaskExecutor taskExecutor,
                             DepositStatusMapper<SwordDspaceDepositStatus> depositStatusMapper,
                             DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser,
                             Policy<Deposit.DepositStatus> dirtyDepositPolicy,
                             Policy<Deposit.DepositStatus> terminalDepositStatusPolicy,
                             CriticalRepositoryInteraction critical) {
        this.passClient = passClient;
        this.packagerRegistry = packagerRegistry;
        this.taskExecutor = taskExecutor;
        this.depositStatusMapper = depositStatusMapper;
        this.atomStatusParser = atomStatusParser;
        this.dirtyDepositPolicy = dirtyDepositPolicy;
        this.terminalDepositStatusPolicy = terminalDepositStatusPolicy;
        this.critical = critical;
    }

    /**
     * Composes a {@link DepositWorkerContext} from the supplied arguments, and submits the context to the {@code
     * TaskExecutor}.  If the executor throws any exceptions, a {@link DepositServiceRuntimeException} will be thrown
     * referencing the {@code Deposit} that failed.
     * <p>
     * Note that the {@link DepositServiceErrorHandler} will be invoked to handle the {@code
     * DepositServiceRuntimeException}, which will attempt to mark the {@code Deposit} as FAILED.
     * </p>
     *
     * @param submission the submission that the {@code deposit} belongs to
     * @param depositSubmission the submission in the Deposit Services' model
     * @param repo the {@code Repository} that is the target of the {@code Deposit}, for which the {@code Packager}
     *             knows how to communicate
     * @param deposit the {@code Deposit} that is being submitted
     * @param packager the Packager for the {@code repo}
     */
    void submitDeposit(Submission submission, DepositSubmission depositSubmission, Repository repo, Deposit deposit,
                       Packager packager) {
        try {
            DepositWorkerContext dc = toDepositWorkerContext(
                    deposit, submission, depositSubmission, repo, packager);
            DepositTask depositTask = new DepositTask(dc, passClient, atomStatusParser, depositStatusMapper,
                    dirtyDepositPolicy, terminalDepositStatusPolicy, critical);

            LOG.debug(">>>> Submitting task ({}@{}) for tuple [{}, {}, {}]",
                    depositTask.getClass().getSimpleName(), toHexString(identityHashCode(depositTask)),
                    submission.getId(), repo.getId(), deposit.getId());
            taskExecutor.execute(depositTask);
        } catch (Exception e) {
            // For example, if the task isn't accepted by the taskExecutor
            String msg = format(FAILED_TO_PROCESS_DEPOSIT, submission.getId(), repo.getId(),
                    (deposit == null) ? "null" : deposit.getId(), e.getMessage());
            throw new DepositServiceRuntimeException(msg, e, deposit);
        }
    }

    /**
     * Attempts to resolve the {@code Packager} to use for depositing to the supplied {@code Repository}.
     *
     * @param submission the submission that the {@code deposit} belongs to
     * @param deposit the {@code Deposit} that is being performed
     * @param repo the {@code Repository} that is the target of the {@code Deposit}, for which the {@code Packager}
     *             knows how to communicate
     * @return the Packager for the Repository, or an empty Optional
     */
    // TODO: packagers are resolved based on the 'name' of the Repository; there's a better way
    Optional<Packager> resolvePackager(Submission submission, Deposit deposit, Repository repo) {
        Packager packager = packagerRegistry.get(repo.getName());

        if (packager == null) {
            // Fail the Deposit if the Packager is null, not the Submission.
            deposit.setDepositStatus(Deposit.DepositStatus.FAILED);
            deposit = passClient.createAndReadResource(deposit, Deposit.class);
            LOG.error(">>>> No Packager found for tuple [{}, {}, {}]: " +
                            "Missing Packager for Repository named '{}', marking Deposit as FAILED.",
                    submission.getId(), repo.getId(), deposit.getId(), repo.getName());
            return Optional.empty();
        }

        return Optional.of(packager);
    }

}
