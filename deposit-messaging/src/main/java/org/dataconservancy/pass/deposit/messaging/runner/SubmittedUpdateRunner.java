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

import static org.dataconservancy.pass.model.Deposit.DepositStatus.SUBMITTED;
import static org.dataconservancy.pass.support.messaging.constants.Constants.Indexer.DEPOSIT_STATUS;

import java.net.URI;
import java.util.Collection;
import java.util.stream.Collectors;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.service.DepositTaskHelper;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
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
 * Deposit</a> repository resources that have a deposit status of {@code submitted}. <p> Submitted deposits have had the
 * contents of their {@link Submission} successfully transferred to a {@link Repository}, but their <em>terminal</em>
 * status is not known.  That is, Deposit Services does not know if the {@code Deposit} has been accepted or rejected.
 * </p> <p> Submitted deposits are examined for a deposit status reference and repository copies. </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmittedUpdateRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SubmittedUpdateRunner.class);

    private static final String URIS_PARAM = "uri";

    @Autowired
    private DepositTaskHelper depositTaskHelper;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private DepositServiceErrorHandler errorHandler;

    /**
     * Answers a Spring {@link ApplicationRunner} that will process a {@code Collection} of URIs representing submitted
     * deposits.  If no URIs are supplied on the command line, a search is performed for all submitted deposits.  The
     * submitted deposits are then queued for processing by the provided {@code processor}.
     *
     * @param passClient the client implementation used to resolve PASS entity uris and perform searches
     * @return the Spring {@code ApplicationRunner} which receives the command line arguments supplied to this
     * application
     */
    @Bean
    public ApplicationRunner depositUpdate(PassClient passClient) {
        return (args) -> {
            Collection<URI> deposits = depositsToUpdate(args, passClient);
            deposits.forEach(depositUri -> {
                try {
                    depositTaskHelper.processDepositStatus(depositUri);
                } catch (Exception e) {
                    errorHandler.handleError(e);
                }
            });

            taskExecutor.shutdown();
            taskExecutor.setAwaitTerminationSeconds(10);
        };
    }

    /**
     * Parses command line arguments for the URIs to update, or searches the index for URIs of submitted deposits.
     * <dl>
     * <dt>--uris</dt><dd>space-separated list of Deposit URIs to be processed.  If the URI does not specify a Deposit,
     * it is skipped (implies {@code --sync}, but can be overridden by supplying {@code --async})</dd> <dt>--sync</dt>
     * <dd>the console remains attached as each URI is processed, allowing the end-user to examine the results of
     * updated Deposits as they happen</dd> <dt>--async</dt> <dd>the console detaches immediately, with the Deposit URIs
     * processed in the background</dd> </dl>
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
            Collection<URI> uris = passClient.findAllByAttribute(Deposit.class, DEPOSIT_STATUS, SUBMITTED);
            if (uris.size() < 1) {
                throw new IllegalArgumentException("No URIs found to process.");
            }
            return uris;
        }
    }

}
