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

import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.model.Registry;
import org.dataconservancy.pass.deposit.messaging.policy.JmsMessagePolicy;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.policy.SubmissionPolicy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.deposit.messaging.support.JsonParser;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    protected SubmissionBuilder fcrepoModelBuilder;

    protected Registry<Packager> packagerRegistry;

    protected SubmissionPolicy submissionPolicy;

    protected JmsMessagePolicy messagePolicy;

    protected DepositStatusResolver<URI, SwordDspaceDepositStatus> atomStatusParser;

    protected Policy<Deposit.DepositStatus> dirtyDepositPolicy;

    protected Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    protected CriticalRepositoryInteraction critical;

    protected DepositTaskHelper depositTaskHelper;

    @Autowired
    public SubmissionProcessor(PassClient passClient, JsonParser jsonParser, SubmissionBuilder fcrepoModelBuilder,
                               Registry<Packager> packagerRegistry,
                               SubmissionPolicy passUserSubmittedPolicy,
                               JmsMessagePolicy submissionMessagePolicy,
                               DepositTaskHelper depositTaskHelper,
                               CriticalRepositoryInteraction critical) {

        this.passClient = passClient;
        this.jsonParser = jsonParser;
        this.fcrepoModelBuilder = fcrepoModelBuilder;
        this.packagerRegistry = packagerRegistry;
        this.submissionPolicy = passUserSubmittedPolicy;
        this.messagePolicy = submissionMessagePolicy;
        this.critical = critical;
        this.depositTaskHelper = depositTaskHelper;
    }

    public void accept(Submission submission) {

        // Mark the Submission as being IN_PROGRESS immediately.  If this fails, we've essentially lost a JMS message

        CriticalResult<DepositSubmission, Submission> result = critical.performCritical(submission.getId(), Submission.class,

                // PassUserSubmittedPolicy will log rejects
                (s) -> submissionPolicy.accept(s),

                (s, ds) -> {
                    if (s.getAggregatedDepositStatus() != IN_PROGRESS) {
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
                            .filter(df -> df.getLocation() == null || df.getLocation().trim().length() == 0)
                            .map(DepositFile::getName)
                            .collect(Collectors.joining(", "));

                    if (filesMissingLocations != null && filesMissingLocations.length() > 0) {
                        String msg = "Update postcondition failed for %s: the following DepositFiles are missing " +
                                "URIs referencing their binary content: %s";
                        throw new IllegalStateException(String.format(msg, s.getId(), filesMissingLocations));
                    }

                    return true;
                },
                (s) -> {
                    DepositSubmission ds = null;
                    try {
                        ds = fcrepoModelBuilder.build(s.getId().toString());
                    } catch (InvalidModel invalidModel) {
                        throw new RuntimeException(invalidModel.getMessage(), invalidModel);
                    }
                    s.setAggregatedDepositStatus(IN_PROGRESS);
                    return ds;
                });

        if (!result.success()) {

            // If a throwable is present on the CriticalResult, re-throw it as a DepositServiceRuntimeException.
            // The DepositServiceErrorHandler will pick up the exception and mark the Submission as FAILED.

            if (result.throwable().isPresent()) {
                Throwable cause = result.throwable().get();
                LOG.warn("Unable to update status of {} to '{}': {}",
                        submission.getId(), IN_PROGRESS, cause.getMessage());
                String msg = format("Unable to update status of %s to '%s': %s",
                        submission.getId(), IN_PROGRESS, cause.getMessage());
                throw new DepositServiceRuntimeException(msg, cause, submission);
            }

            return;
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
