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

import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.DepositServiceRuntimeException;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.service.DepositUtil.DepositWorkerContext;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2DepositReceiptResponse;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

import static java.lang.Integer.toHexString;
import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static java.util.Collections.emptyMap;
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

    private DepositWorkerContext dc;

    private PassClient passClient;

    private Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private DepositTaskHelper depositHelper;

    private long swordSleepTimeMs = 10000;

    // e.g. http://dspace-prod.mse.jhu.edu:8080/swordv2
    private String prefixToMatch;

    // e.g. https://jscholarship.library.jhu.edu/swordv2
    private String replacementPrefix;

    public DepositTask(DepositWorkerContext dc,
                       PassClient passClient,
                       Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy,
                       CriticalRepositoryInteraction cri,
                       DepositTaskHelper depositHelper) {
        this.dc = dc;
        this.passClient = passClient;
        this.intermediateDepositStatusPolicy = intermediateDepositStatusPolicy;
        this.cri = cri;
        this.depositHelper = depositHelper;
    }

    @Override
    public void run() {

        LOG.debug(">>>> Running {}@{}", DepositTask.class.getSimpleName(), toHexString(identityHashCode(this)));

        CriticalResult<TransportResponse, Deposit> result = cri.performCritical(dc.deposit().getId(), Deposit.class,

                /*
                 * Only "intermediate" deposits can be processed by {@code DepositTask}
                 */
                (deposit) -> {
                    boolean accept = intermediateDepositStatusPolicy.accept(deposit.getDepositStatus());
                    if (!accept) {
                        LOG.debug(">>>> Update precondition failed for {}", deposit.getId());
                    }

                    return accept;
                },

                /*
                 * Determines *physical* success of the Deposit: were the bytes of the package successfully received?
                 */
                (deposit, tr) -> {
                    if (deposit.getDepositStatus() != SUBMITTED) {
                        LOG.debug("Postcondition failed for {}.  Expected status '{}' but actual status " +
                                "is '{}'", deposit.getId(), SUBMITTED, deposit.getDepositStatus());
                        return false;
                    }

                    if (!tr.success()) {
                        LOG.debug("Postcondition failed for {}.  Transport of package to endpoint " +
                                "failed: {}", deposit.getId(), tr.error().getMessage(), tr.error());
                        return false;
                    }

                    return true;
                },

                /*
                 * Assemble and stream a package of content to the repository endpoint, update status to SUBMITTED
                 */
                (deposit) -> {
                    Packager packager = dc.packager();
                    PackageStream packageStream = packager.getAssembler().assemble(dc.depositSubmission(), emptyMap());
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
            if (result.throwable().isPresent()) {
                Throwable t = result.throwable().get();
                String msg = String.format("Failed to perform deposit for tuple [%s, %s, %s]: %s",
                        dc.submission().getId(), dc.repository().getId(), dc.deposit().getId(), t.getMessage());
                throw new DepositServiceRuntimeException(msg, t, dc.deposit());
            }

            String msg = String.format("Failed to perform deposit for tuple [%s, %s, %s]",
                    dc.submission().getId(), dc.repository().getId(), dc.deposit().getId());
            throw new DepositServiceRuntimeException(msg, dc.deposit());
        }

        dc.deposit(result.resource().orElseThrow(() ->
                new DepositServiceRuntimeException("Missing expected Deposit resource " +
                        dc.deposit().getId(), dc.deposit())));

        TransportResponse transportResponse = result.result().orElseThrow(() ->
                new DepositServiceRuntimeException("Missing TransportResponse for " +
                        dc.deposit().getId(), dc.deposit()));

        // Determine *logical* success: was the Deposit accepted by the remote system?

        // TODO: response handlers should be decoupled, this will require an update the the TransportResponse interface; e.g. is the response terminal, or should it be polled?
        if (transportResponse instanceof Sword2DepositReceiptResponse) {
            String statementUri = null;
            String itemUri = null;
            try {
                Sword2DepositReceiptResponse swordResponse = (Sword2DepositReceiptResponse) transportResponse;

                // deposit receipt -> SWORD Statement (ORE ReM/Atom XML) -> sword:state
                if (swordResponse.getReceipt().getSplashPageLink() != null) {
                    itemUri = swordResponse.getReceipt().getSplashPageLink().getHref();
                }

                // deposit receipt -> DSpace Item URL
                statementUri = swordResponse.getReceipt().getAtomStatementLink().getIRI().toURI().toString();

                if (prefixToMatch != null && statementUri.startsWith(prefixToMatch)) {
                    String newUri = statementUri.replace(prefixToMatch, replacementPrefix);
                    LOG.trace("Replacing Atom Statement URI '{}' with '{}'", statementUri, newUri);
                    statementUri  = newUri;
                } else {
                    LOG.trace("Prefix '{}' did not match Atom Statement URI '{}', no replacement will take place.",
                            prefixToMatch == null ? "<null>" : prefixToMatch, statementUri);
                }
            } catch (Exception e) {
                String msg = format("Failed to update deposit status to %s for tuple [%s, %s, %s]; " +
                            "parsing the Atom statement %s for %s failed: %s",
                        ACCEPTED, dc.submission().getId(), dc.repository().getId(), dc.deposit().getId(),
                        statementUri, dc.deposit().getId(), e.getMessage());
                throw new DepositServiceRuntimeException(msg, e, dc.deposit());
            }

            // Update and persist the Deposit and RepositoryCopy in the repository

            String finalStatementUri = statementUri;
            String finalItemUri = itemUri;
            CriticalResult<RepositoryCopy, Deposit> cr = cri.performCritical(dc.deposit().getId(), Deposit.class,

                    (criDeposit) -> true,
                    (criDeposit) -> true,
                    (criDeposit) -> {
                        if (finalItemUri != null) {
                            // TransportResponse was successfully parsed, set the status ref
                            criDeposit.setDepositStatusRef(finalStatementUri);
                        }

                        // Create a RepositoryCopy, which will record the URL of the Item in DSpace
                        RepositoryCopy repoCopy = null;
                        if (finalItemUri != null) {
                            repoCopy = passClient.createAndReadResource(newRepositoryCopy(dc,
                                    finalItemUri, CopyStatus.IN_PROGRESS), RepositoryCopy.class);
                            criDeposit.setRepositoryCopy(repoCopy.getId());
                        }
                        return repoCopy;
                    });

            if (cr.success()) {
                dc.deposit(cr.resource().orElseThrow(() -> new RuntimeException("Missing expected Deposit resource.")));
                dc.repoCopy(cr.result().orElseThrow(() -> new RuntimeException("Missing expected RepositoryCopy")));
            } else {
                if (cr.throwable().isPresent()) {
                    Throwable t = cr.throwable().get();
                    if (t instanceof DepositServiceRuntimeException) {
                        throw (DepositServiceRuntimeException) t;
                    }

                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }

                    throw new RuntimeException(t.getMessage(), t);
                } else {
                    throw new RuntimeException(String.format("Failed updating Deposit and RepositoryCopy for tuple " +
                            "[%s, %s, %s]", dc.submission().getId(), dc.repository().getId(), dc.deposit().getId()));
                }
            }
        }

    }

    /**
     * Creates a new instance of a {@code RepositoryCopy}.  The initial state of the repository copy returned from this
     * method is:
     * <ul>
     *     <li>a copy status set to {@code copyStatus}</li>
     *     <li>a repository URI of the {@code Repository} maintained in the deposit context</li>
     *     <li>a publication URI of the {@code Submission} maintained in the deposit context</li>
     *     <li>a external id set to the {@code itemUri}</li>
     *     <li>an access URL set to the URL form of {@code itemUri}</li>
     * </ul>
     *
     * TODO: review the setting of {@code itemUrl}, as that is Repository-specific.
     *
     * @param dc
     * @param itemUri
     * @param copyStatus
     * @return
     */
    private static RepositoryCopy newRepositoryCopy(DepositWorkerContext dc, String itemUri, CopyStatus copyStatus) {
        RepositoryCopy repoCopy = new RepositoryCopy();
        repoCopy.setCopyStatus(copyStatus);
        repoCopy.setRepository(dc.repository().getId());
        repoCopy.setPublication(dc.submission().getPublication());
        if (itemUri != null) {
            repoCopy.setExternalIds(Collections.singletonList(itemUri));
            try {
                repoCopy.setAccessUrl(new URI(itemUri));
            } catch (URISyntaxException e) {
                LOG.warn("Error creating an accessUrl from '{}' for a RepositoryCopy associated with {}",
                        itemUri, dc.deposit().getId());
            }
        }
        return repoCopy;
    }

    public String getPrefixToMatch() {
        return prefixToMatch;
    }

    public void setPrefixToMatch(String prefixToMatch) {
        this.prefixToMatch = prefixToMatch;
    }

    public void setReplacementPrefix(String replacementPrefix) {
        this.replacementPrefix = replacementPrefix;
    }

    public String getReplacementPrefix() {
        return this.replacementPrefix;
    }

    public DepositWorkerContext getDepositWorkerContext() {
        return dc;
    }

    public long getSwordSleepTimeMs() {
        return swordSleepTimeMs;
    }

    public void setSwordSleepTimeMs(long swordSleepTimeMs) {
        this.swordSleepTimeMs = swordSleepTimeMs;
    }

    @Override
    public String toString() {
        return "DepositTask{" + "dc=" + dc + ", passClient=" + passClient + '}';
    }
}
