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

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.messaging.support.Constants.Indexer.DEPOSIT_STATUS;

/**
 * Accepts uris for, or searches for, <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Deposit.md">
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
public class DirtyDepositRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DirtyDepositRunner.class);

    /**
     * Answers a Spring {@link ApplicationRunner} that will process a {@code Collection} of URIs representing dirty
     * deposits.  If no URIs are supplied on the command line, a search is performed for all dirty deposits.  The
     * dirty deposits are then queued for processing by the provided {@code submissionProcessor}.
     *
     * @param passClient the client implementation used to resolve PASS entity uris and perform searches
     * @param submissionProcessor processes {@code Deposit} resources that it deems dirty
     * @return the Spring {@code ApplicationRunner} which receives the command line arguments supplied to this application
     */
    @Bean
    public ApplicationRunner depositUpdate(PassClient passClient, BiConsumer<Submission, Deposit> submissionProcessor) {
        return (args) -> {
            Collection<URI> deposits = depositsToUpdate(args, passClient);
            deposits.forEach(depositUri -> {
                try {
                    Deposit deposit = passClient.readResource(depositUri, Deposit.class);
                    Submission submission = passClient.readResource(deposit.getSubmission(), Submission.class);
                    submissionProcessor.accept(submission, deposit);
                } catch (Exception e) {
                    LOG.info("Failed to process {}: {}", depositUri, e.getMessage(), e);
                }
            });
        };
    }

    /**
     * Parses command line arguments for the URIs to update, or searches the index for URIs of dirty deposits.
     *
     * @param args the command line arguments
     * @param passClient used to search the index for dirty deposits
     * @return a {@code Collection} of URIs representing dirty deposits
     */
    private Collection<URI> depositsToUpdate(ApplicationArguments args, PassClient passClient) {
        if (args.getNonOptionArgs() != null && args.getNonOptionArgs().size() > 1) {
            return args.getNonOptionArgs().stream().skip(1).map(URI::create).collect(Collectors.toSet());
        } else {
            return passClient.findAllByAttribute(Deposit.class, DEPOSIT_STATUS, null);
        }
    }

}
