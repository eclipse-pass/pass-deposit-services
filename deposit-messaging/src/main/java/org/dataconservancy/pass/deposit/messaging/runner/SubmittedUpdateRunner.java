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
import org.dataconservancy.pass.deposit.messaging.service.PassEntityProcessor;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.messaging.support.Constants.Indexer.DEPOSIT_STATUS;

/**
 * Accepts uris for, or searches for, <a href="https://github.com/OA-PASS/pass-data-model/blob/master/documentation/Deposit.md">
 * Deposit</a> repository resources that have a deposit status of {@code submitted}.
 * <p>
 * Submitted deposits have had the contents of their {@link Submission} successfully transferred to a {@link
 * Repository}, but their <em>terminal</em> status is not known.  That is, Deposit Services does not know if the {@code
 * Deposit} has been accepted or rejected.
 * </p>
 * <p>
 * Submitted deposits are examined for a deposit status reference and repository copies.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmittedUpdateRunner {

    /**
     * Answers a Spring {@link ApplicationRunner} that will process a {@code Collection} of URIs representing submitted
     * deposits.  If no URIs are supplied on the command line, a search is performed for all submitted deposits.  The
     * submitted deposits are then queued for processing by the provided {@code processor}.
     *
     * @param passClient the client implementation used to resolve PASS entity uris and perform searches
     * @param processor processes {@code Deposit} resources that have a state of submitted
     * @return the Spring {@code ApplicationRunner} which receives the command line arguments supplied to this application
     */
    @Bean
    public ApplicationRunner depositUpdate(PassClient passClient, PassEntityProcessor processor, @Qualifier("submittedDepositStatusUpdater")
            Consumer<Deposit> updater) {
        return (args) -> {
            Collection<URI> toUpdate = toUpdate(args, passClient);
            processor.update(toUpdate, updater, Deposit.class);
        };
    }

    private Collection<URI> toUpdate(ApplicationArguments args, PassClient passClient) {
        if (args.getNonOptionArgs() != null && args.getNonOptionArgs().size() > 1) {
            return args.getNonOptionArgs().stream().skip(1).map(URI::create).collect(Collectors.toSet());
        } else {
            return passClient.findAllByAttribute(Deposit.class, DEPOSIT_STATUS, "submitted");
        }
    }

}
