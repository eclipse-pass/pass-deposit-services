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

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.deposit.util.loggers.Loggers.WORKERS_LOGGER;
import static org.dataconservancy.pass.deposit.messaging.service.DepositUtil.toDepositWorkerContext;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.REJECTED;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.RemedialDepositException;
import org.dataconservancy.pass.deposit.messaging.config.repository.AuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.BasicAuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.Repositories;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil.DepositWorkerContext;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusProcessor;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

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

    public static final String MISSING_PACKAGER = "No Packager found for tuple [{}, {}, {}]: " +
                                                  "Missing Packager for Repository named '{}', marking Deposit as " +
                                                  "FAILED.";

    private static final String PRECONDITION_FAILED = "Refusing to update {}, the following pre-condition failed: ";

    private static final String POSTCONDITION_FAILED = "Refusing to update {}, the following post-condition failed: ";

    private static final String ERR_RESOLVE_REPOSITORY = "Unable to resolve Repository Configuration for Repository " +
                                                         "%s (%s).  Verify the Deposit Services runtime configuration" +
                                                         " location and " + "content.";

    private static final String ERR_PARSING_STATUS_DOC = "Failed to update deposit status for [%s], parsing the " +
                                                         "status document referenced by %s failed: %s";

    private static final String ERR_MAPPING_STATUS = "Failed to update deposit status for [%s], mapping the status " +
                                                     "obtained from  %s failed";

    private static final String ERR_UPDATE_REPOCOPY = "Failed to create or update RepositoryCopy '%s' for %s";

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

    private Repositories repositories;

    @Autowired
    public DepositTaskHelper(PassClient passClient,
                             TaskExecutor depositWorkers,
                             Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy,
                             Policy<Deposit.DepositStatus> terminalDepositStatusPolicy,
                             CriticalRepositoryInteraction cri,
                             Repositories repositories) {
        this.passClient = passClient;
        this.taskExecutor = depositWorkers;
        this.intermediateDepositStatusPolicy = intermediateDepositStatusPolicy;
        this.terminalDepositStatusPolicy = terminalDepositStatusPolicy;
        this.cri = cri;
        this.repositories = repositories;
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
     * @param submission        the submission that the {@code deposit} belongs to
     * @param depositSubmission the submission in the Deposit Services' model
     * @param repo              the {@code Repository} that is the target of the {@code Deposit}, for which the
     * {@code Packager}
     *                          knows how to communicate
     * @param deposit           the {@code Deposit} that is being submitted
     * @param packager          the Packager for the {@code repo}
     */
    public void submitDeposit(Submission submission, DepositSubmission depositSubmission, Repository repo,
                              Deposit deposit,
                              Packager packager) {
        try {
            DepositWorkerContext dc = toDepositWorkerContext(
                deposit, submission, depositSubmission, repo, packager);
            DepositTask depositTask = new DepositTask(dc, passClient, intermediateDepositStatusPolicy, cri);
            depositTask.setSwordSleepTimeMs(swordDepositSleepTimeMs);
            depositTask.setPrefixToMatch(statementUriPrefix);
            depositTask.setReplacementPrefix(statementUriReplacement);

            WORKERS_LOGGER.debug("Submitting task ({}@{}) for tuple [{}, {}, {}]",
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

    public void processDepositStatus(URI depositUri) {

        CriticalResult<RepositoryCopy, Deposit> cr = cri.performCritical(depositUri, Deposit.class,
                                                                         DepositStatusCriFunc.precondition(
                                                                             intermediateDepositStatusPolicy,
                                                                             passClient),
                                                                         DepositStatusCriFunc.postcondition(),
                                                                         DepositStatusCriFunc.critical(repositories,
                                                                                                       passClient));

        if (!cr.success()) {
            if (cr.throwable().isPresent()) {
                Throwable t = cr.throwable().get();
                if (t instanceof RemedialDepositException) {
                    LOG.error(format("Failed to update Deposit %s", depositUri), t);
                    return;
                }

                if (t instanceof DepositServiceRuntimeException) {
                    throw (DepositServiceRuntimeException) t;
                }

                if (cr.resource().isPresent()) {
                    throw new DepositServiceRuntimeException(
                        format("Failed to update Deposit %s: %s", depositUri, t.getMessage()),
                        t, cr.resource().get());
                }
            }

            LOG.debug(format("Failed to update Deposit %s: no cause was present, probably a pre- or post-condition " +
                             "was not satisfied.", depositUri));
            return;
        }

        LOG.info("Successfully processed Deposit {}", depositUri);
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

    static Optional<RepositoryConfig> lookupConfig(Repository repository, Repositories repositories) {
        Set<String> repositoryKeys = repositories.keys();

        // Look up the RepositoryConfig by the Repository URI
        if (repository.getId() != null && repositoryKeys.contains(repository.getId().toString())) {
            return Optional.of(repositories.getConfig(repository.getId().toString()));
        }

        // Look up the RepositoryConfig by the Repository Key
        if (repository.getRepositoryKey() != null && repositoryKeys.contains(repository.getRepositoryKey())) {
            return Optional.of(repositories.getConfig(repository.getRepositoryKey()));
        }

        // Look up the RepositoryConfig by a path component in the Repository URI
        if (repository.getId() != null && repositoryKeys.contains(repository.getId().getPath())) {
            return Optional.of(repositories.getConfig(repository.getId().getPath()));
        }

        if (repository.getId() != null) {
            String path = repository.getId().getPath();
            int idx;
            while ((idx = path.indexOf("/")) > -1) {
                path = path.substring(idx + 1);

                if (repositoryKeys.contains("/" + path)) {
                    return Optional.of(repositories.getConfig("/" + path));
                }

                if (repositoryKeys.contains(path)) {
                    return Optional.of(repositories.getConfig(path));
                }
            }
        }

        return Optional.empty();
    }

    static Optional<BasicAuthRealm> matchRealm(String url, Collection<AuthRealm> authRealms) {
        return authRealms.stream()
                         .filter(realm -> realm instanceof BasicAuthRealm)
                         .map(realm -> (BasicAuthRealm) realm)
                         .filter(realm -> url.startsWith(realm.getBaseUrl().toString()))
                         .findAny();
    }

    static class DepositStatusCriFunc {

        /**
         * Preconditions:
         * <ul>
         *     <li>Deposit must be in an intermediate state</li>
         *     <li>Deposit must have a depositStatusRef</li>
         *     <li>Deposit must have a Repository</li>
         *     <li>Deposit must have a RepositoryCopy, even if it is just a placeholder</li>
         * </ul>
         */
        static Predicate<Deposit> precondition(Policy<Deposit.DepositStatus> statusPolicy, PassClient passClient) {
            return (deposit) -> {
                if (!statusPolicy.test(deposit.getDepositStatus())) {
                    LOG.debug(PRECONDITION_FAILED + " Deposit.DepositStatus = {}, a terminal state.",
                              deposit.getId(), deposit.getDepositStatus());
                    return false;
                }

                if (deposit.getDepositStatusRef() == null || deposit.getDepositStatusRef().trim().length() == 0) {
                    LOG.debug(PRECONDITION_FAILED + " missing Deposit status reference.", deposit.getId());
                    return false;
                }

                if (deposit.getRepository() == null) {
                    LOG.debug(PRECONDITION_FAILED + " missing Repository URI on the Deposit", deposit.getId());
                    return false;
                }

                URI repoCopy = deposit.getRepositoryCopy();

                if (repoCopy == null || passClient.readResource(repoCopy, RepositoryCopy.class) == null) {
                    LOG.debug(PRECONDITION_FAILED + " missing RepositoryCopy on the Deposit", deposit.getId());
                    return false;
                }

                return true;
            };
        }

        /**
         * Postcondition: if the critical section sets a Deposit.DepositStatus, it must be congruent with
         * RepositoryCopy.copyStatus, otherwise the Deposit must have a non-null RepositoryCopy:
         * <dl>
         *     <dt>Deposit.DepositStatus = ACCEPTED</dt>
         *     <dd>RepositoryCopy.CopyStatus = COMPLETE</dd>
         *     <dt>Deposit.DepositStatus = REJECTED</dt>
         *     <dd>RepositoryCopy.CopyStatus = REJECTED</dd>
         * </dl>
         *
         * @return
         */
        static BiPredicate<Deposit, RepositoryCopy> postcondition() {
            return (deposit, repoCopy) -> {
                if (repoCopy == null) {
                    return false;
                }

                if (deposit.getDepositStatus() == ACCEPTED) {
                    return RepositoryCopy.CopyStatus.COMPLETE == repoCopy.getCopyStatus();
                }

                if (deposit.getDepositStatus() == REJECTED) {
                    return RepositoryCopy.CopyStatus.REJECTED == repoCopy.getCopyStatus();
                }

                return true;
            };
        }

        static Function<Deposit, RepositoryCopy> critical(Repositories repositories, PassClient passClient) {
            return (deposit) -> {
                AtomicReference<Deposit.DepositStatus> status = new AtomicReference<>();
                try {
                    Repository repo = passClient.readResource(deposit.getRepository(), Repository.class);
                    RepositoryConfig repoConfig = lookupConfig(repo, repositories)
                        .orElseThrow(() ->
                                         new RemedialDepositException(
                                             format(ERR_RESOLVE_REPOSITORY, repo.getName(), repo.getId()), repo));
                    DepositStatusProcessor statusProcessor = repoConfig.getRepositoryDepositConfig()
                                                                       .getDepositProcessing()
                                                                       .getProcessor();
                    status.set(statusProcessor.process(deposit, repoConfig));
                } catch (RemedialDepositException e) {
                    throw e;
                } catch (Exception e) {
                    String msg = format(ERR_PARSING_STATUS_DOC,
                                        deposit.getId(), deposit.getDepositStatusRef(), e.getMessage());
                    throw new DepositServiceRuntimeException(msg, e, deposit);
                }

                if (status.get() == null) {
                    String msg = format(ERR_MAPPING_STATUS, deposit.getId(), deposit.getDepositStatusRef());
                    throw new DepositServiceRuntimeException(msg, deposit);
                }

                try {
                    RepositoryCopy repoCopy = passClient.readResource(deposit.getRepositoryCopy(),
                                                                      RepositoryCopy.class);

                    switch (status.get()) {
                        case ACCEPTED: {
                            LOG.debug("Deposit {} was accepted.", deposit.getId());
                            deposit.setDepositStatus(ACCEPTED);
                            repoCopy.setCopyStatus(RepositoryCopy.CopyStatus.COMPLETE);
                            repoCopy = passClient.updateAndReadResource(repoCopy, RepositoryCopy.class);
                            break;
                        }

                        case REJECTED: {
                            LOG.debug("Deposit {} was rejected.", deposit.getId());
                            deposit.setDepositStatus(Deposit.DepositStatus.REJECTED);
                            repoCopy.setCopyStatus(RepositoryCopy.CopyStatus.REJECTED);
                            repoCopy = passClient.updateAndReadResource(repoCopy, RepositoryCopy.class);
                            break;
                        }
                        default:
                    }
                    return repoCopy;
                } catch (Exception e) {
                    String msg = String.format(ERR_UPDATE_REPOCOPY, deposit.getRepositoryCopy(), deposit.getId());
                    throw new DepositServiceRuntimeException(msg, e, deposit);
                }
            };
        }
    }

}
