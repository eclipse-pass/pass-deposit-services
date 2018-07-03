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

import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil.DepositWorkerContext;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.toDepositWorkerContext;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.REJECTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.SUBMITTED;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.IN_PROGRESS;

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

    public static final String FAILED_TO_PROCESS_DEPOSIT = "Failed to process Deposit for tuple [%s, %s, %s]: %s";

    public static final String MISSING_PACKAGER = ">>>> No Packager found for tuple [{}, {}, {}]: " +
            "Missing Packager for Repository named '{}', marking Deposit as FAILED.";

    private static final String PRECONDITION_FAILED = "Refusing to update {}, the following pre-condition failed: ";

    private static final String POSTCONDITION_FAILED = "Refusing to update {}, the following post-condition failed: ";

    private PassClient passClient;

    private TaskExecutor taskExecutor;

    private Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy;

    private Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    private CriticalRepositoryInteraction cri;

    @Value("${pass.deposit.transport.swordv2.sleep-time-ms}")
    private long swordDepositSleepTimeMs;

    @Value("${jscholarship.hack.sword.statement.uri-prefix}")
    private String statementUriPrefix;

    @Value("${jscholarship.hack.sword.statement.uri-replacement}")
    private String statementUriReplacement;

    private Registry<Packager> packagerRegistry;

    @Autowired
    public DepositTaskHelper(PassClient passClient,
                             TaskExecutor taskExecutor,
                             Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy,
                             Policy<Deposit.DepositStatus> terminalDepositStatusPolicy,
                             CriticalRepositoryInteraction cri,
                             Registry<Packager> packagerRegistry) {
        this.passClient = passClient;
        this.taskExecutor = taskExecutor;
        this.intermediateDepositStatusPolicy = intermediateDepositStatusPolicy;
        this.terminalDepositStatusPolicy = terminalDepositStatusPolicy;
        this.cri = cri;
        this.packagerRegistry = packagerRegistry;
    }

    /**
     * Composes a {@link DepositWorkerContext} from the supplied arguments, and submits the context to the {@code
     * TaskExecutor}.  If the executor throws any exceptions, a {@link DepositServiceRuntimeException} will be thrown
     * referencing the {@code Deposit} that failed.
     * <p>
     * Note that the {@link DepositServiceErrorHandler} will be invoked to handle the {@code
     * DepositServiceRuntimeException}, which will attempt to mark the {@code Deposit} as FAILED.
     * </p>
     * <p>
     * The {@code DepositTask} composed by this helper method will only accept {@code Deposit} resources with
     * <em>intermediate</em> state.
     * </p>
     *
     * @param submission the submission that the {@code deposit} belongs to
     * @param depositSubmission the submission in the Deposit Services' model
     * @param repo the {@code Repository} that is the target of the {@code Deposit}, for which the {@code Packager}
     *             knows how to communicate
     * @param deposit the {@code Deposit} that is being submitted
     * @param packager the Packager for the {@code repo}
     */
    public void submitDeposit(Submission submission, DepositSubmission depositSubmission, Repository repo, Deposit deposit,
                       Packager packager) {
        try {
            DepositWorkerContext dc = toDepositWorkerContext(
                    deposit, submission, depositSubmission, repo, packager);
            DepositTask depositTask = new DepositTask(dc, passClient, intermediateDepositStatusPolicy, cri, this);
            depositTask.setSwordSleepTimeMs(swordDepositSleepTimeMs);
            depositTask.setPrefixToMatch(statementUriPrefix);
            depositTask.setReplacementPrefix(statementUriReplacement);

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

    public void processDepositStatus(Deposit deposit) {
       processDepositStatus(
                passClient.readResource(deposit.getSubmission(), Submission.class),
                passClient.readResource(deposit.getRepository(), Repository.class),
                passClient.readResource(deposit.getRepositoryCopy(), RepositoryCopy.class), deposit);
    }

    void processDepositStatus(Submission submission, Repository repo, RepositoryCopy repoCopy, Deposit deposit) {

        // Subtle issue to be aware of:
        //
        // The Deposit being passed into this method may contain state (e.g. a depositStatusRef) that is not
        // present on the Deposit resource in the repository.  Therefore, the Deposit retrieved by the CRI may
        // be out of date with respect to the Deposit provided to this method.
        //
        // At this time, the depositStatusRef is the only state that may differ.
        //
        // To insure that the depositStatusRef from the 'deposit' method parameter is stored on the 'deposit' from
        // the CRI, the field is copied in the "critical update" lambda below.  This insures if a conflict arises,
        // the ConflictHandler will retry the critical update, including the copy of the depositStatusRef.

        CriticalResult<RepositoryCopy, Deposit> cr = cri.performCritical(deposit.getId(), Deposit.class,

                /*
                 * Preconditions:
                 * - The depositStatusRef on the 'deposit' *supplied to this method* must not be null, and must be a URI
                 * - The links between Deposit, Submission, Repository, and RepositoryCopy must be intact
                 * - The Deposit must be in a SUBMITTED state.
                 * - Insure a DepositStatusRefProcessor exists for the Repository
                 */
                (criDeposit) -> {
                    if (criDeposit.getDepositStatus() != SUBMITTED) {
                        LOG.warn(PRECONDITION_FAILED + " Expected Deposit.DepositStatus = {}, but was '{}'",
                                SUBMITTED, deposit.getDepositStatus());
                        return false;
                    }

                    if (!verifyNullityAndLinks(submission, repo, repoCopy, criDeposit)) {
                        return false;
                    }

                    try {
                        new URI(deposit.getDepositStatusRef());
                    } catch (URISyntaxException|NullPointerException e) {
                        LOG.warn(PRECONDITION_FAILED + " depositStatusRef must be a valid URI: {}",
                                deposit.getId(), e.getMessage(), e);
                        return false;
                    }

                    if (packagerRegistry.get(repo.getName()) == null || packagerRegistry.get(repo.getName())
                            .getDepositStatusProcessor() == null) {
                        LOG.warn(PRECONDITION_FAILED + " mising a DepositStatusRefProcessor for Repository with name " +
                                "'{}' (id: '{}')", repo.getName(), repo.getId());
                        return false;
                    }

                    return true;
                },

                /*
                 * Postconditions:
                 * - The criRepoCopy must not be null
                 * - The criDeposit must have a depositStatusRef
                 * - The criDeposit must be linked to the criRepoCopy
                 * - The criDeposit cannot be in a FAILED state
                 * - If the criDeposit is still in SUBMITTED state, then the RepositoryCopy must remain (or be in) an IN-PROGRESS state
                 * - If the criDeposit is in a REJECTED state, then the RepositoryCopy must be in a REJECTED state
                 * - If the criDeposit is in an ACCEPTED stated, then the RepositoryCopy must be in an ACCEPTED state
                 */
                (criDeposit, criRepoCopy) -> {
                    if (criRepoCopy == null) {
                        LOG.warn(POSTCONDITION_FAILED + " RepositoryCopy was null.");
                        return false;
                    }

                    if (criDeposit.getDepositStatusRef() == null) {
                        LOG.warn(POSTCONDITION_FAILED + " Deposit must have a depositStatusRef.");
                        return false;
                    }

                    if (criDeposit.getRepositoryCopy() == null || !criDeposit.getRepositoryCopy().equals(criRepoCopy.getId())) {
                        LOG.warn(POSTCONDITION_FAILED + " Deposit RepositoryCopy URI was '{}', and does not equal the" +
                                " expected URI {}", criDeposit.getRepositoryCopy(), criRepoCopy.getId());
                        return false;
                    }

                    // A SWORD deposit may have taken longer than 10s (they are async, after all), so the Deposit
                    // may be in the SUBMITTED state still.
                    //
                    // NIHMS FTP deposits will always be in the SUBMITTED state upon success, because there is no
                    // way to determine the acceptability of the package simply by dropping it off at the FTP server
                    //
                    // So, the status of the Deposit might be REJECTED, ACCEPTED, or SUBMITTED, as long as it isn't
                    // FAILED.
                    if (!terminalDepositStatusPolicy.accept(criDeposit.getDepositStatus()) &&
                            criDeposit.getDepositStatus() != SUBMITTED) {
                        LOG.warn(POSTCONDITION_FAILED + " Expected Deposit.DepositStatus to be {}, {}, or {}, but was '{}'",
                                ACCEPTED, REJECTED, SUBMITTED, criDeposit.getDepositStatus());
                        return false;
                    }

                    if (criDeposit.getDepositStatus() == SUBMITTED && repoCopy.getCopyStatus() != IN_PROGRESS) {
                        LOG.warn(POSTCONDITION_FAILED + " Expected RepoCopy.CopyStatus = {}, but was '{}' for Deposit.DepositStatus = '{}'",
                                IN_PROGRESS, criRepoCopy.getCopyStatus(), SUBMITTED);
                        return false;
                    }

                    if (criDeposit.getDepositStatus() == REJECTED && criRepoCopy.getCopyStatus() != RepositoryCopy.CopyStatus.REJECTED) {
                        LOG.warn(POSTCONDITION_FAILED + " Expected RepoCopy.CopyStatus = {}, but was '{}' for Deposit.DepositStatus = '{}'",
                                RepositoryCopy.CopyStatus.REJECTED, criRepoCopy.getCopyStatus(), REJECTED);
                        return false;
                    }

                    if (criDeposit.getDepositStatus() == ACCEPTED && criRepoCopy.getCopyStatus() != RepositoryCopy.CopyStatus.COMPLETE) {
                        LOG.warn(POSTCONDITION_FAILED + " Expected RepoCopy.CopyStatus = {}, but was '{}' for Deposit.DepositStatus = '{}'",
                                RepositoryCopy.CopyStatus.COMPLETE, criRepoCopy.getCopyStatus(), ACCEPTED);
                        return false;
                    }

                    return true;
                },

                (criDeposit) -> {
                    Deposit.DepositStatus status;

                    criDeposit.setDepositStatusRef(deposit.getDepositStatusRef());

                    try {
                        DepositStatusRefProcessor depositStatusProcessor =
                                packagerRegistry.get(repo.getName()).getDepositStatusProcessor();
                        status = depositStatusProcessor.process(URI.create(criDeposit.getDepositStatusRef()));
                    } catch (Exception e) {
                        String msg = format("Failed to update deposit status for [%s], " +
                                        "parsing the status document referenced by %s failed: %s",
                                criDeposit.getId(), criDeposit.getDepositStatusRef(), e.getMessage());
                        LOG.warn(msg, e);
                        throw new DepositServiceRuntimeException(msg, e, criDeposit);
                    }

                    if (status == null) {
                        String msg = format("Failed to update deposit status for [%s], " +
                                        "mapping the status obtained from  %s failed",
                                criDeposit.getId(), criDeposit.getDepositStatusRef());
                        throw new DepositServiceRuntimeException(msg, criDeposit);
                    }

                    switch (status) {
                        case ACCEPTED: {
                            LOG.info("Deposit {} was accepted.", criDeposit.getId());
                            criDeposit.setDepositStatus(ACCEPTED);
                            repoCopy.setCopyStatus(RepositoryCopy.CopyStatus.COMPLETE);
                            break;
                        }

                        case REJECTED: {
                            LOG.info("Deposit {} was rejected.", criDeposit.getId());
                            criDeposit.setDepositStatus(Deposit.DepositStatus.REJECTED);
                            repoCopy.setCopyStatus(RepositoryCopy.CopyStatus.REJECTED);
                            break;
                        }
                    }

                    RepositoryCopy criRepoCopy;

                    try {
                        if (repoCopy.getId() == null) {
                            criRepoCopy = passClient.createAndReadResource(repoCopy, RepositoryCopy.class);
                        } else {
                            criRepoCopy = passClient.updateAndReadResource(repoCopy, RepositoryCopy.class);
                        }
                        criDeposit.setRepositoryCopy(criRepoCopy.getId());
                    } catch (Exception e) {
                        String msg = String.format("Failed to create or update RepositoryCopy '%s' for %s",
                                repoCopy.getId(), criDeposit.getId());
                        throw new DepositServiceRuntimeException(msg, e, criDeposit);
                    }

                    return criRepoCopy;
                });

        if (!cr.success()) {
            String msg = format("Failed to update Deposit tuple [%s, %s, %s]",
                    submission.getId(), repo.getId(), deposit.getId());

            if (cr.throwable().isPresent()) {
                throw new DepositServiceRuntimeException(msg, cr.throwable().get(), deposit);
            }

            throw new DepositServiceRuntimeException(msg, deposit);
        }
    }

    String getStatementUriPrefix() {
        return statementUriPrefix;
    }

    void setStatementUriPrefix(String statementUriPrefix) {
        this.statementUriPrefix = statementUriPrefix;
    }

    String getStatementUriReplacement() {
        return statementUriReplacement;
    }

    void setStatementUriReplacement(String statementUriReplacement) {
        this.statementUriReplacement = statementUriReplacement;
    }

    private static boolean verifyNullityAndLinks(Submission s, Repository r, RepositoryCopy rc, Deposit d) {
        if (d.getDepositStatus() != SUBMITTED) {
            LOG.warn(PRECONDITION_FAILED + " expected DepositStatus = '{}', but was '{}'",
                    d.getId(), SUBMITTED, d.getDepositStatus());
            return false;
        }

        if (rc.getId() != null && !rc.getId().equals(d.getRepositoryCopy())) {
            LOG.warn(PRECONDITION_FAILED + " RepositoryCopy URI mismatch: deposit RepositoryCopy URI: '{}', supplied RepositoryCopy URI: '{}'",
                    d.getId(), d.getRepositoryCopy(), rc.getId());
            return false;
        }

        if (d.getSubmission() == null) {
            LOG.warn(PRECONDITION_FAILED + " it has a 'null' Submission.", d.getId());
            return false;
        }

        if (!s.getId().equals(d.getSubmission())) {
            LOG.warn(PRECONDITION_FAILED + " Submission URI mismatch: deposit Submission URI: '{}', supplied Submission URI: '{}'",
                    d.getId(), d.getSubmission(), s.getId());
        }

        if (d.getRepository() == null) {
            LOG.warn(PRECONDITION_FAILED + " it has a 'null' Repository.", d.getId());
            return false;
        }

        if (!r.getId().equals(d.getRepository())) {
            LOG.warn(PRECONDITION_FAILED + " Repository URI mismatch: deposit Repository URI: '{}', supplied Repository URI: '{}'",
                    d.getId(), d.getRepository(), r.getId());
            return false;
        }

        return true;
    }

}
