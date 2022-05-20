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
package org.dataconservancy.pass.deposit.messaging.runner;

import static java.lang.String.format;
import static org.dataconservancy.pass.deposit.messaging.service.DepositTaskHelper.MISSING_PACKAGER;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.FAILED;
import static org.dataconservancy.pass.support.messaging.constants.Constants.Indexer.DEPOSIT_STATUS;

import java.net.URI;
import java.util.Collection;
import java.util.stream.Collectors;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.TerminalDepositStatusPolicy;
import org.dataconservancy.pass.deposit.messaging.service.DepositTaskHelper;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Accepts uris for, or searches for,
 * <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Deposit.md">
 * Deposit</a> repository resources that have a {@code null} deposit status (so-called "dirty" deposits).
 * <p>
 * Dirty deposits have not had the contents of their {@link Submission} successfully transferred to a {@link
 * Repository}.  The deposits may not have been processed, or a transient failure was encountered when transferring the
 * content of their {@code Submission} to a {@code Repository}.
 * </p>
 * <p>
 * Dirty deposits are simply re-queued for processing by a submission processor that implements {@code
 * BiConsumer<Submission, Deposit>}.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see org.dataconservancy.pass.deposit.messaging.service.SubmissionProcessor
 */
public class FailedDepositRunner {

    private static final Logger LOG = LoggerFactory.getLogger(FailedDepositRunner.class);

    private static final String FAILED_TO_PROCESS = "Failed to process {}: {}";

    private static final String URIS_PARAM = "uri";

    private enum MODE {
        SYNC,
        ASYNC
    }

    @Autowired
    private SubmissionBuilder fcrepoModelBuilder;

    @Autowired
    private DepositTaskHelper depositTaskHelper;

    @Autowired
    private Registry<Packager> packagerRegistry;

    @Autowired
    private TerminalDepositStatusPolicy terminalDepositStatusPolicy;

    @Autowired
    private CriticalRepositoryInteraction cri;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    /**
     * Answers a Spring {@link ApplicationRunner} that will process a {@code Collection} of URIs representing dirty
     * deposits.  If no URIs are supplied on the command line, a search is performed for all dirty deposits.  The
     * dirty deposits are then queued for processing by the provided {@code submissionProcessor}.
     *
     * @param passClient the client implementation used to resolve PASS entity uris and perform searches
     * @return the Spring {@code ApplicationRunner} which receives the command line arguments supplied to this
     * application
     */
    @Bean
    public ApplicationRunner retryDeposit(PassClient passClient) {
        return (args) -> {
            Collection<URI> deposits = depositsToUpdate(args, passClient);
            deposits.forEach(depositUri -> {
                try {
                    Deposit deposit = passClient.readResource(depositUri, Deposit.class);
                    Submission submission = passClient.readResource(deposit.getSubmission(), Submission.class);
                    Repository repo = passClient.readResource(deposit.getRepository(), Repository.class);

                    final Packager[] packager = {null};
                    final DepositSubmission[] depositSubmission = {null};

                    CriticalRepositoryInteraction.CriticalResult<?, Deposit> cr =
                        cri.performCritical(deposit.getId(), Deposit.class,

                            /*
                             * This pre-condition insists that:
                             *   1. The Deposit must have a FAILED or null DepositStatus
                             *   2. A Packager must exist for the Repository associated with the Deposit
                             *   3. The DepositSubmission must be built successfully from the original Submission
                             *   4. That the DepositSubmission must carry files, and each file must have a location URI
                             *
                             * The failure to satisfy a pre-condition is *not* considered exceptional, and no action is
                             * taken on the Deposit or any other repository resource if one fails.
                             */
                            (d) -> {
                                if (deposit.getDepositStatus() != FAILED && deposit.getDepositStatus() != null) {
                                    LOG.warn(FAILED_TO_PROCESS, deposit.getId(),
                                             "Deposit status must equal 'null' " +
                                             "or '" + FAILED + "', but was '" + deposit.getDepositStatus() + "'");
                                    return false;
                                }

                                packager[0] = packagerRegistry.get(repo.getName());
                                if (packager[0] == null) {
                                    LOG.warn(MISSING_PACKAGER,
                                             submission.getId(), repo.getId(), deposit.getId(),
                                             repo.getName());
                                    return false;
                                }

                                try {
                                    depositSubmission[0] =
                                        fcrepoModelBuilder.build(submission.getId().toString());
                                } catch (InvalidModel invalidModel) {
                                    LOG.warn(FAILED_TO_PROCESS, deposit.getId(),
                                             "Failed to build the DepositSubmission model",
                                             invalidModel);
                                    return false;
                                }

                                if (depositSubmission[0].getFiles() == null ||
                                    depositSubmission[0].getFiles().size() < 1) {
                                    LOG.warn(FAILED_TO_PROCESS, deposit.getId(),
                                             "There are no files attached to " +
                                             "the submission " + submission.getId());
                                    return false;
                                }

                                // Each DepositFile must have a URI that links to its content
                                String filesMissingLocations = depositSubmission[0].getFiles().stream()
                                    .filter(df -> df.getLocation() == null || df.getLocation().trim().length() == 0)
                                    .map(DepositFile::getName)
                                    .collect(Collectors.joining(", "));

                                if (filesMissingLocations != null && filesMissingLocations.length() > 0) {
                                    String msg = "Update precondition failed for %s: the following " +
                                                 "DepositFiles are " +
                                                 "missing URIs referencing their binary content: %s";
                                    LOG.warn(FAILED_TO_PROCESS, deposit.getId(),
                                             format(msg, submission.getId(), filesMissingLocations));
                                }

                                return true;
                            },

                            /*
                             * The post condition does nothing, at least, it doesn't do anything right now.  In the
                             * future, if the user wants to see the console in the foreground as things progress, this
                             * post condition can track the progress of the retried Deposit.
                             */
                            (d) -> true,

                            (d) -> {
                                depositTaskHelper.submitDeposit(submission, depositSubmission[0], repo,
                                                                deposit,
                                                                packager[0]);

                                return null;
                            }
                        );

                    if (!cr.success()) {
                        if (cr.throwable().isPresent()) {
                            LOG.warn(FAILED_TO_PROCESS, deposit.getId(), cr.throwable().get().getMessage(),
                                     cr.throwable().get());
                        } else {
                            LOG.warn(FAILED_TO_PROCESS, deposit.getId(), "(no throwable present to log)");
                        }
                    }
                } catch (Exception e) {
                    LOG.warn(FAILED_TO_PROCESS, depositUri, e.getMessage(), e);
                }
            });

            taskExecutor.shutdown();
            taskExecutor.setAwaitTerminationSeconds(10);
        };
    }

    /**
     * Parses command line arguments for the URIs to update, or searches the index for URIs of dirty deposits.
     * <dl>
     *     <dt>--uris</dt>
     *     <dd>space-separated list of Deposit URIs to be processed.  If the URI does not specify a Deposit, it is
     *         skipped (implies {@code --sync}, but can be overridden by supplying {@code --async})</dd>
     *     <dt>--sync</dt>
     *     <dd>the console remains attached as each URI is processed, allowing the end-user to examine the results of
     *         updated Deposits as they happen</dd>
     *     <dt>--async</dt>
     *     <dd>the console detaches immediately, with the Deposit URIs processed in the background</dd>
     * </dl>
     *
     * @param args       the command line arguments
     * @param passClient used to search the index for dirty deposits
     * @return a {@code Collection} of URIs representing dirty deposits
     */
    private Collection<URI> depositsToUpdate(ApplicationArguments args, PassClient passClient) {
        if (args.containsOption(URIS_PARAM) && args.getOptionValues(URIS_PARAM).size() > 0) {
            // maintain the order of the uris as they were supplied on the CLI
            return args.getOptionValues(URIS_PARAM).stream().map(URI::create).collect(Collectors.toList());
        } else {
            Collection<URI> uris = passClient.findAllByAttribute(Deposit.class, DEPOSIT_STATUS, FAILED);
            uris.addAll(passClient.findAllByAttribute(Deposit.class, DEPOSIT_STATUS, null));
            if (uris.size() < 1) {
                throw new IllegalArgumentException("No URIs found to process.");
            }
            return uris;
        }
    }

    /**
     * If {@code --sync} or {@code --async} are supplied, those win, no matter the values of other arguments.
     * <p>
     * If {@code --uris} are supplied, then {@code --sync} is implied.
     * </p>
     * <p>
     * If no {@code --uris} are supplied, then {@cocde --async} is implied.
     * </p>
     *
     * @param args
     * @return
     * @throws IllegalArgumentException if both {@code --sync} <em>and</em> {@code --async} are supplied
     */
    private MODE getMode(ApplicationArguments args) {
        if (args.containsOption("sync") && args.containsOption("async")) {
            throw new IllegalArgumentException("Arguments \"sync\" and \"async\" are mutually exclusive!");
        }

        if (args.containsOption("sync")) {
            return MODE.SYNC;
        }

        if (args.containsOption("async")) {
            return MODE.ASYNC;
        }

        if (args.containsOption("uris")) {
            return MODE.SYNC;
        }

        return MODE.ASYNC;
    }

}
