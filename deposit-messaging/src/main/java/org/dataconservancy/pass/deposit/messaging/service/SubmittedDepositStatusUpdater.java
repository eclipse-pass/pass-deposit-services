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

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.StatusEvaluator;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.function.Consumer;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SubmittedDepositStatusUpdater implements Consumer<Deposit> {

    private static final Logger LOG = LoggerFactory.getLogger(PassEntityProcessor.class);

    /**
     * Communicates with Fedora and Elastic search
     */
    private PassClient passClient;

    /**
     * Determines whether or not a {@code DepositStatus} is <em>intermediate</em>
     */
    private Policy<DepositStatus> intermediateDepositStatusPolicy;

    /**
     * Determines whether or not a {@code DepositStatus} is <em>terminal</em>
     */
    private Policy<DepositStatus> terminalDepositStatusPolicy;

    /**
     * Resolves the URI, retrieves the content, and parses a {@link SwordDspaceDepositStatus} from it
     */
    private DepositStatusParser<URI, SwordDspaceDepositStatus> statusParser;

    /**
     * Determines a Deposit Status based on the Repository Copy Status
     */
    private DepositStatusMapper<CopyStatus> repoCopyStatusMapper;

    /**
     * Determines a Deposit Status based on a {@link SwordDspaceDepositStatus}
     */
    private DepositStatusMapper<SwordDspaceDepositStatus> swordStatusMapper;

    @Autowired
    public SubmittedDepositStatusUpdater(PassClient passClient, Policy<DepositStatus> intermediateDepositStatusPolicy, Policy<DepositStatus> terminalDepositStatusPolicy, DepositStatusParser<URI, SwordDspaceDepositStatus> statusParser, DepositStatusMapper<CopyStatus> repoCopyStatusMapper, DepositStatusMapper<SwordDspaceDepositStatus> swordStatusMapper) {
        this.passClient = passClient;
        this.intermediateDepositStatusPolicy = intermediateDepositStatusPolicy;
        this.terminalDepositStatusPolicy = terminalDepositStatusPolicy;
        this.statusParser = statusParser;
        this.repoCopyStatusMapper = repoCopyStatusMapper;
        this.swordStatusMapper = swordStatusMapper;
    }

    /**
     * Evaluates the deposit status reference (preferred) or the {@link CopyStatus RepositoryCopy status} (if a {@code
     * RepositoryCopy} for the deposit status {@link Deposit#getRepositoryCopy() exists}), and updates the {@link
     * DepositStatus} of the supplied {@code deposit} by storing the updated {@code deposit} in the repository.
     * <p>
     * If the supplied {@code deposit} has a deposit status reference, the reference is resolved, contents parsed, and
     * mapped to a {@code DepositStatus}.  If the {@code deposit} has no deposit status reference, its {@code
     * RepositoryCopy} is resolved and the {@code CopyStatus} is mapped to a {@code DepositStatus}.  If neither exist,
     * then the {@code DepositStatus} of {@code deposit} remains unchanged, and no communication with the repository
     * takes place.
     * </p>
     * <p>
     * If a deposit status reference or a {@code RepositoryCopy CopyStatus} is mapped to a {@code DepositStatus}, and
     * the {@code DepositStatus} is not a {@link StatusEvaluator#isTerminal(Object) terminal} status, then the {@code
     * DepositStatus} of {@code deposit} remains unchanged.  The {@code deposit} is only updated if the {@code
     * DepositStatus} is determined to be <em>terminal</em>.
     * </p>
     *
     * @param deposit the PASS Deposit resource whose {@code DepositStatus} is being evaluated; the supplied {@code
     *                deposit} is expected to have an intermediate (i.e. <em>non-terminal</em>) {@link DepositStatus}
     * @throws org.apache.http.ParseException if there is an error parsing an Atom Statement
     * @throws RuntimeException if there is an error retrieving the Atom Statement or communicating with PASS repository
     */
    @Override
    public void accept(Deposit deposit) {
        LOG.trace(">>>> Processing Deposit {} with status {}",
                deposit.getId(), (deposit.getDepositStatus() != null ? deposit.getDepositStatus().name() : "'null'"));

        // If the incoming deposit does not have an _intermediate_ deposit status, then there's nothing for
        // this updater to update.
        if (!intermediateDepositStatusPolicy.accept(deposit.getDepositStatus())) {
            LOG.trace(">>>>    Refusing to update non-intermediate status {} for deposit {}",
                    deposit.getDepositStatus(), deposit.getId());
            return;
        }

        DepositStatus status = null;

        if (deposit.getDepositStatusRef() != null) {
            // If Deposit has a depositStatusRef, poll the endpoint to determine status and map it to a
            // DepositStatus
            LOG.trace(">>>>    Parsing and mapping deposit status document from {}", deposit.getDepositStatusRef());
            status = swordStatusMapper.map(statusParser.parse(URI.create(deposit.getDepositStatusRef())));
        } else if (deposit.getRepositoryCopy() != null) {
            // If Deposit has a linked RepositoryCopy, map the RepositoryCopy status to a DepositStatus
            LOG.trace(">>>>    Mapping repository copy status from {}", deposit.getRepositoryCopy());
            status = repoCopyStatusMapper.map(passClient.readResource(deposit.getRepositoryCopy(),
                    RepositoryCopy.class).getCopyStatus());
        }

        // if the resulting deposit status is terminal, update the Deposit
        if (terminalDepositStatusPolicy.accept(status)) {
            LOG.trace(">>>>    Updating deposit status of {} to {}", deposit.getId(), status);
            deposit.setDepositStatus(status);
            passClient.updateResource(deposit);
            return;
        }

        LOG.trace(">>>>    Deposit status of {} could not be updated to '{}'; the status was either mapped to null or" +
                " status is not terminal", deposit.getId(), status == null ? "null" : status);
    }
}
