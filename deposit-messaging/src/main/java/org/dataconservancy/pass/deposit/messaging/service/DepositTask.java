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

import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.transport.TransportResponse;
import org.dataconservancy.nihms.transport.TransportSession;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.fedora.UpdateConflictException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusMapper;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2DepositReceiptResponse;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.SUBMITTED;

/**
 * Assembles, packages, and transports the custodial content associated with a {@code Submission} to a remote endpoint
 * represented by a {@code Deposit}, {@code Repository} tuple.  Creates any {@code RepositoryCopy} resources, and
 * maintains/updates the status of {@code Deposit} during the process.
 * <p>
 * In particular, this class distinguishes between <em>physical</em> and <em>logical</em> success of a deposit.  The
 * physical outcome refers to whether or not the bytes of the custodial content were successfully received by the the
 * repository endpoint.  This has to do with the physical characteristics of the underlying transport, authentication,
 * etc.
 * </p>
 * <p>
 * The logical outcome refers to whether or not the custodial content of the package was accepted and accessioned by
 * the repository endpoint.  That is to say the logical outcome revolves around the transfer of custody to another
 * repository.  A successful logical outcome indicates that custody was transferred to another repository.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(DepositTask.class);

    private DepositUtil.DepositWorkerContext dc;

    private PassClient passClient;

    private DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser;

    private DepositStatusMapper<SwordDspaceDepositStatus> swordDepositStatusMapper;

    private Policy<Deposit.DepositStatus> dirtyDepositPolicy;

    private CriticalRepositoryInteraction critical;

    public DepositTask(DepositUtil.DepositWorkerContext dc, PassClient passClient,
                       DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser,
                       DepositStatusMapper<SwordDspaceDepositStatus> swordDepositStatusMapper,
                       Policy<Deposit.DepositStatus> dirtyDepositPolicy, CriticalRepositoryInteraction critical) {
        this.dc = dc;
        this.passClient = passClient;
        this.atomStatusParser = atomStatusParser;
        this.swordDepositStatusMapper = swordDepositStatusMapper;
        this.dirtyDepositPolicy = dirtyDepositPolicy;
        this.critical = critical;
    }

    @Override
    public void run() {

        LOG.debug(">>>> Running {}@{}", DepositTask.class.getSimpleName(), toHexString(identityHashCode(this)));

        CriticalResult<TransportResponse, Deposit> result = critical.performCritical(dc.deposit().getId(), Deposit.class,

                /*
                 * Only "dirty" deposits can be processed by {@code DepositTask}
                 */
                (deposit) -> {
                    boolean accept = dirtyDepositPolicy.accept(deposit.getDepositStatus());
                    if (!accept) {
                        LOG.debug(">>>> Update precondition failed for {}", deposit.getId());
                    }

                    return accept;
                },

                /*
                 * Determines *physical* success of the Deposit: were the bytes of the package successfully received?
                 */
                (deposit, tr) -> {
                    boolean success = deposit.getDepositStatus() == SUBMITTED;
                    if (!success) {
                        LOG.debug(">>>> Update postcondition failed for {} - expected status '{}' but actual status " +
                                "is '{}'", deposit.getId(), SUBMITTED, deposit.getDepositStatus());
                    }

                    success &= tr.success();

                    if (!success) {
                        LOG.debug(">>>> Update postcondition failed for {} - transport of package to endpoint " +
                                "failed: {}", deposit.getId(), tr.error().getMessage(), tr.error());
                    }

                    return success;
                },

                /*
                 * Assemble and stream a package of content to the repository endpoint, update status to SUBMITTED
                 */
                (deposit) -> {
                    Packager packager = dc.packager();
                    PackageStream packageStream = packager.getAssembler().assemble(dc.depositSubmission());
                    Map<String, String> packagerConfig = packager.getConfiguration();
                    try (TransportSession transport = packager.getTransport().open(packagerConfig)) {
                        TransportResponse tr = transport.send(packageStream, packagerConfig);
                        deposit.setDepositStatus(SUBMITTED);
                        return tr;
                    } catch (Exception e) {
                        throw new RuntimeException("Error closing transport session for deposit " +
                                dc.deposit().getId() + ": " + e.getMessage(), e);
                    }
                });

        // Check *physical* success: were the bytes of the package successfully streamed to endpoint?

        if (!result.success()) {
            // one of the pre or post conditions failed, or the critical code path (creating a package failed)
            result.throwable().ifPresent(t ->
                    LOG.warn("Failed to perform deposit for tuple [{}, {}, {}]: {}",
                        dc.submission().getId(), dc.repository().getId(), dc.deposit().getId(), t.getMessage(), t));

            // Mark deposit as dirty, then return.
            try {
                dc.deposit(result.resource()
                        .orElse(passClient.readResource(dc.deposit().getId(), Deposit.class)));
                dc.deposit().setDepositStatus(null);
                passClient.updateResource(dc.deposit());
            } catch (Exception e) {
                LOG.warn("Additionally, there was an error marking {} as dirty: {}",
                        dc.deposit().getId(), e.getMessage(), e);
            }
            return;
        }

        dc.deposit(result.resource().orElseThrow(() ->
                new RuntimeException("Missing expected Deposit resource " + dc.deposit().getId())));

        TransportResponse transportResponse = result.result().orElseThrow(() ->
                new RuntimeException("Missing TransportResponse for Deposit " + dc.deposit().getId()));

        // Determine *logical* success: was the Deposit accepted by the remote system?

        // TODO: response handlers should be decoupled, this will require an update the the TransportResponse interface; e.g. is the response terminal, or should it be polled?
        if (!(transportResponse instanceof Sword2DepositReceiptResponse)) {
            // Currently two remote repositories are supported: NIHMS FTP, and JScholarship
            // JScholarship will return a Sword2DepositReceiptResponse containing information that will allow us
            //   to determine logical success
            // NIHMS will return an anonymous implementation which has no information in it to determine logical
            //   success

            // If we don't have a Sword2DepositReceiptResponse, then there is nothing we can do to determine logical
            // success.  All we know is that the deposit has been submitted.
        } else {
            // Deposits for JScholarship are practically synchronous even though the API is asyc.
            // TODO: abstract out a configurable timer.
            // Sleep here for a bit, let DSpace do its thing, and then we ought to be able to parse a deposit status
            try {
                LOG.debug(">>>> Sleeping 10 seconds for SWORD deposit to complete ...");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                LOG.debug(">>>> DepositTask {}@{} interrupted!",
                        DepositTask.class.getSimpleName(), toHexString(identityHashCode(this)));
                Thread.interrupted();
            }

            // deposit receipt -> SWORD Statement (ORE ReM/Atom XML) -> sword:state
            Sword2DepositReceiptResponse swordResponse = (Sword2DepositReceiptResponse) transportResponse;
            String itemUri = swordResponse.getReceipt().getEntry().getAlternateLink().getHref().toString();
            URI statementUri = null;
            Deposit.DepositStatus status = null;
            try {
                statementUri = swordResponse.getReceipt().getAtomStatementLink().getIRI().toURI();
                SwordDspaceDepositStatus swordStatus = atomStatusParser.parse(statementUri);
                status = swordDepositStatusMapper.map(swordStatus);
            } catch (Exception e) {
                String msg = format("Failed to update deposit status to %s for tuple [%s, %s, %s]; " +
                            "parsing the Atom statement %s for %s failed: %s",
                        ACCEPTED, dc.submission().getId(), dc.repository().getId(), dc.deposit().getId(),
                        statementUri, dc.deposit().getId(), e.getMessage());
                throw new RuntimeException(msg, e);
            }

            switch (status) {
                case ACCEPTED: {
                    LOG.debug(">>>> Deposit {} was accepted.", dc.deposit().getId());
                    dc.deposit().setDepositStatus(ACCEPTED);
                    RepositoryCopy repoCopy = new RepositoryCopy();
                    repoCopy.setCopyStatus(RepositoryCopy.CopyStatus.COMPLETE);
                    repoCopy.setRepository(dc.repository().getId());
                    repoCopy.setPublication(dc.submission().getPublication());
                    repoCopy.setExternalIds(Collections.singletonList(itemUri));
                    dc.repoCopy(repoCopy);
                    break;
                }

                case REJECTED: {
                    LOG.debug(">>>> Deposit {} was rejected.", dc.deposit().getId());
                    dc.deposit().setDepositStatus(Deposit.DepositStatus.REJECTED);
                    break;
                }
            }
        }

        // TransportResponse was successfully parsed and logical success or failure of the Deposit was determined.
        // Create the RepositoryCopy and update the Deposit

        if (dc.repoCopy() != null) {
            try {
                dc.repoCopy(passClient.createAndReadResource(dc.repoCopy(), RepositoryCopy.class));
                LOG.debug(">>>> Created repository copy for {}: {}", dc.deposit().getId(), dc.repoCopy().getId());
                dc.deposit().setRepositoryCopy(dc.repoCopy().getId());
            } catch (Exception e) {
                String msg = format("Failed to create RepositoryCopy resource for successful deposit of " +
                        "tuple [%s, %s, %s]: %s",
                        dc.submission().getId(), dc.repository().getId(), dc.deposit().getId(), e.getMessage());
                throw new RuntimeException(msg, e);
            }
        }

        try {
            LOG.debug(">>>> Updating deposit {}", dc.deposit().getId());
            passClient.updateResource(dc.deposit());
        } catch (Exception e) {
            String msg = format("Failed to update Deposit resource for successful deposit of tuple [%s, %s, %s]: %s",
                    dc.submission().getId(), dc.repository().getId(), dc.deposit().getId(), e.getMessage());
            throw new RuntimeException(msg, e);
        }
    }

    @Override
    public String toString() {
        return "DepositTask{" + "dc=" + dc + ", passClient=" + passClient + '}';
    }
}
