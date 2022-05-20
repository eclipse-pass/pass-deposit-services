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

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.dataconservancy.pass.model.Submission.AggregatedDepositStatus.IN_PROGRESS;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.policy.SubmissionPolicy;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.support.messaging.json.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    protected SubmissionBuilder fcrepoModelBuilder;

    protected Registry<Packager> packagerRegistry;

    protected SubmissionPolicy submissionPolicy;

    protected CriticalRepositoryInteraction critical;

    protected DepositTaskHelper depositTaskHelper;

    @Autowired
    public SubmissionProcessor(PassClient passClient, JsonParser jsonParser, SubmissionBuilder fcrepoModelBuilder,
                               Registry<Packager> packagerRegistry, SubmissionPolicy passUserSubmittedPolicy,
                               DepositTaskHelper depositTaskHelper, CriticalRepositoryInteraction critical) {

        this.passClient = passClient;
        this.jsonParser = jsonParser;
        this.fcrepoModelBuilder = fcrepoModelBuilder;
        this.packagerRegistry = packagerRegistry;
        this.submissionPolicy = passUserSubmittedPolicy;
        this.critical = critical;
        this.depositTaskHelper = depositTaskHelper;
    }

    public void accept(Submission submission) {

        // Validates the incoming Submission, marks it as being IN_PROGRESS immediately.
        // If this fails, we've essentially lost a JMS message

        CriticalResult<DepositSubmission, Submission> result =
            critical.performCritical(submission.getId(), Submission.class,
                                     CriFunc.preCondition(submissionPolicy),
                                     CriFunc.postCondition(),
                                     CriFunc.critical(fcrepoModelBuilder));

        if (!result.success()) {
            // Throw DepositServiceRuntimeException, which will be processed by the DepositServiceErrorHandler
            final String msg_tmpl = "Unable to update status of %s to '%s': %s";

            if (result.throwable().isPresent()) {
                Throwable cause = result.throwable().get();
                String msg = format(msg_tmpl, submission.getId(), IN_PROGRESS, cause.getMessage());
                throw new DepositServiceRuntimeException(msg, cause, submission);
            } else {
                String msg = format(msg_tmpl, submission.getId(), IN_PROGRESS,
                                    "no cause was present, probably a pre- or post-condition was not satisfied.");
                LOG.debug(msg);
                return;
            }
        }

        Submission updatedS = result.resource().orElseThrow(() ->
                                                                new DepositServiceRuntimeException(
                                                                    "Missing expected Submission " + submission.getId(),
                                                                    submission));

        DepositSubmission depositSubmission = result.result().orElseThrow(() ->
                                                                              new DepositServiceRuntimeException(
                                                                                  "Missing expected DepositSubmission",
                                                                                  submission));

        LOG.info("Processing Submission {}", submission.getId());

        updatedS.getRepositories()
                .stream()
                .map(repoUri -> passClient.readResource(repoUri, Repository.class))
                .filter(repo -> Repository.IntegrationType.WEB_LINK != repo.getIntegrationType())
                .forEach(repo -> {
                    submitDeposit(updatedS, depositSubmission, repo);
                });
    }

    void submitDeposit(Submission submission, DepositSubmission depositSubmission, Repository repo) {
        Deposit deposit = null;
        Packager packager = null;
        try {
            deposit = createDeposit(submission, repo);

            for (final String key : getLookupKeys(repo)) {
                if ((packager = packagerRegistry.get(key)) != null) {
                    break;
                }
            }

            if (packager == null) {
                throw new NullPointerException(format("No Packager found for tuple [%s, %s, %s]: " +
                                                      "Missing Packager for Repository named '%s' (key: %s)",
                                                      submission.getId(), deposit.getId(), repo.getId(), repo.getName(),
                                                      repo.getRepositoryKey()));
            }
            deposit = passClient.createAndReadResource(deposit, Deposit.class);
        } catch (Exception e) {
            String msg = format(FAILED_TO_PROCESS_DEPOSIT, submission.getId(), repo.getId(),
                                (deposit == null) ? "null" : deposit.getId(), e.getMessage());
            throw new DepositServiceRuntimeException(msg, e, deposit);
        }

        depositTaskHelper.submitDeposit(submission, depositSubmission, repo, deposit, packager);
    }

    static class CriFunc {

        /**
         * Answers the critical function which builds a {@link DepositSubmission} from the Submission, then sets the
         * {@link Submission.AggregatedDepositStatus} to {@code IN_PROGRESS}.
         *
         * @param modelBuilder the model builder used to build the {@code DepositSubmission}
         * @return the Function that builds the DepositSubmission and sets the aggregated deposit status on the
         * Submission
         */
        static Function<Submission, DepositSubmission> critical(SubmissionBuilder modelBuilder) {
            return (s) -> {
                DepositSubmission ds = null;
                try {
                    ds = modelBuilder.build(s.getId().toString());
                } catch (InvalidModel invalidModel) {
                    throw new RuntimeException(invalidModel.getMessage(), invalidModel);
                }
                s.setAggregatedDepositStatus(IN_PROGRESS);
                return ds;
            };
        }

        /**
         * Answers a BiPredicate that verifies the state of the Submission and the DepositSubmission.
         * <ul>
         *     <li>The Submission.AggregatedDepositStatus must be IN_PROGRESS</li>
         *     <li>The DepositSubmission must have at least one {@link DepositSubmission#getFiles() file} attached</li>
         *     <li>Each DepositFile must have a non-empty {@link DepositFile#getLocation() location}</li>
         * </ul>
         *
         * @return a BiPredicate that verifies the state created or set by the critical function on repository resources
         * @throws IllegalStateException if the Submission, DepositSubmission, or DepositFiles have invalid state
         */
        static BiPredicate<Submission, DepositSubmission> postCondition() {
            return (s, ds) -> {
                if (IN_PROGRESS != s.getAggregatedDepositStatus()) {
                    String msg = "Update postcondition failed for %s: expected status '%s' but actual status is " +
                                 "'%s'";
                    throw new IllegalStateException(String.format(msg, s.getId(), IN_PROGRESS, s
                        .getAggregatedDepositStatus()));
                }

                // Treat the lack of files on the Submission as a FAILURE, as that is not a transient issue
                // (that is, files will not magically appear on the Submission in the future).

                if (ds.getFiles().size() < 1) {
                    String msg = "Update postcondition failed for %s: the DepositSubmission has no files " +
                                 "attached! (Hint: check the incoming links to the Submission)";
                    throw new IllegalStateException(String.format(msg, s.getId()));
                }

                // Each DepositFile must have a URI that links to its content
                String filesMissingLocations = ds.getFiles().stream()
                                                 .filter(df -> df.getLocation() == null || df.getLocation().trim()
                                                                                             .length() == 0)
                                                 .map(DepositFile::getName)
                                                 .collect(Collectors.joining(", "));

                if (filesMissingLocations != null && filesMissingLocations.length() > 0) {
                    String msg = "Update postcondition failed for %s: the following DepositFiles are missing " +
                                 "URIs referencing their binary content: %s";
                    throw new IllegalStateException(String.format(msg, s.getId(), filesMissingLocations));
                }

                return true;
            };
        }

        /**
         * Answers a Predicate that will accept the Submission for processing if it is accepted by the supplied
         * {@link Policy}.
         *
         * @param submissionPolicy the submission policy
         * @return a Predicate that invokes the submission policy
         */
        static Predicate<Submission> preCondition(SubmissionPolicy submissionPolicy) {
            return submissionPolicy::test;
        }
    }

    static Collection<String> getLookupKeys(Repository repo) {
        final List<String> keys = new ArrayList<>();

        ofNullable(repo.getName()).ifPresent(keys::add);
        ofNullable(repo.getRepositoryKey()).ifPresent(keys::add);
        ofNullable(repo.getId()).map(Object::toString).ifPresent(keys::add);

        String path = ofNullable(repo.getId()).map(URI::getPath).orElse("");

        while (path.contains("/")) {
            path = path.substring(path.indexOf("/") + 1);
            keys.add(path);
        }

        return keys;
    }

    private static Deposit createDeposit(Submission submission, Repository repo) {
        Deposit deposit;
        deposit = new Deposit();
        deposit.setRepository(repo.getId());
        deposit.setSubmission(submission.getId());
        return deposit;
    }

}
