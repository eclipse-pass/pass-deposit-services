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
import org.dataconservancy.pass.model.RepositoryCopy;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.dataconservancy.pass.model.Deposit.DepositStatus.ACCEPTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.REJECTED;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.SUBMITTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmittedDepositStatusUpdaterTest {

    /**
     * Represents a <em>terminal</em> status for a {@code Deposit}
     */
    private final static Deposit.DepositStatus TERMINAL_STATUS = Deposit.DepositStatus.ACCEPTED;

    /**
     * Represents a <em>terminal</em> status for a SWORD deposit
     */
    private final static SwordDspaceDepositStatus SWORD_TERMINAL_STATUS = SwordDspaceDepositStatus.SWORD_STATE_ARCHIVED;

    /**
     * Represents a <em>terminal</em> status for a {@code RepositoryCopy}
     */
    private final static RepositoryCopy.CopyStatus RC_TERMINAL_STATUS = RepositoryCopy.CopyStatus.COMPLETE;

    /**
     * Represents an <em>intermediate</em> status for a {@code Deposit}
     */
    private final static Deposit.DepositStatus INTERMEDIATE_STATUS = Deposit.DepositStatus.SUBMITTED;

    /**
     * Represents an <em>intermediate</em> status for a SWORD deposit
     */
    private final static SwordDspaceDepositStatus SWORD_INTERMEDIATE_STATUS = SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS;

    /**
     * Represents an <em>intermediate</em> status for a {@code RepositoryCopy}
     */
    private final static RepositoryCopy.CopyStatus RC_INTERMEDIATE_STATUS = RepositoryCopy.CopyStatus.IN_PROGRESS;

    /**
     * Parses a {@code SwordDspaceDepositStatus} from the Atom feed referenced by the URI
     */
    private DepositStatusParser<URI, SwordDspaceDepositStatus> depositStatusParser;

    /**
     * Maps {@code RepositoryCopy} deposit status to {@code Deposit} deposit status
     */
    private DepositStatusMapper<RepositoryCopy.CopyStatus> repoCopyStatusMapper;

    /**
     * Maps SWORD deposit status to {@code Deposit} deposit status
     */
    private DepositStatusMapper<SwordDspaceDepositStatus> swordStatusMapper;

    /**
     * Policy which answers whether or not to accept <em>intermediate</em> {@code DepositStatus}s
     */
    private Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy;

    /**
     * Policy which answers whether or not to accept <em>terminal</em> {@code DepositStatus}s
     */
    private Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    /**
     * A {@code Deposit} which is initialized to an <em>intermediate</em> deposit state,
     * {@link Deposit.DepositStatus#SUBMITTED}
     */
    private Deposit submittedDeposit;

    /**
     * The PassClient interface used to communicate with the index and repository
     */
    private PassClient passClient;

    /**
     * Status updater under test
     */
    private SubmittedDepositStatusUpdater underTest;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        this.passClient = mock(PassClient.class);
        this.depositStatusParser = mock(DepositStatusParser.class);
        this.repoCopyStatusMapper = mock(DepositStatusMapper.class);
        this.swordStatusMapper = mock(DepositStatusMapper.class);
        this.intermediateDepositStatusPolicy = mock(Policy.class);
        this.terminalDepositStatusPolicy = mock(Policy.class);


        this.underTest = new SubmittedDepositStatusUpdater(passClient, intermediateDepositStatusPolicy,
                terminalDepositStatusPolicy, depositStatusParser, repoCopyStatusMapper, swordStatusMapper);

        submittedDeposit = new Deposit();
        assertNull(submittedDeposit.getDepositStatusRef());
        assertNull(submittedDeposit.getRepositoryCopy());
        submittedDeposit.setDepositStatus(INTERMEDIATE_STATUS);

        when(intermediateDepositStatusPolicy.accept(INTERMEDIATE_STATUS)).thenReturn(true);
        when(terminalDepositStatusPolicy.accept(TERMINAL_STATUS)).thenReturn(true);
    }

    // Deposit[status=null] should not be picked up
    // Deposit[status=rejected] || Deposit[status=accepted] should not be picked up
    // Only non-terminal statuses should be polled

    // Test cases:
    //   - Deposit with no status ref and no repo copy (left alone)
    //   - Deposit with no status ref with repo copy (repo copy used)
    //   - Deposit with status ref and repo copy (repo copy ignored in this case)
    //   - Deposit with malformed status ref
    //   - Deposit with status ref that doesn't exist
    //   - Deposit with status ref that times out
    //   - Deposit status is mapped to a non-terminal status (should be left alone)
    //   - Pass Client that fails to update the resource

    /**
     * When there is status ref and no repo copy, no updates should happen to the deposit.
     *
     * @throws Exception
     */
    @Test
    public void scanNoStatusNoRepocopy() throws Exception {
        Deposit.DepositStatus originalStatus = submittedDeposit.getDepositStatus();
        underTest.accept(submittedDeposit);
        verifyZeroInteractions(repoCopyStatusMapper, swordStatusMapper, depositStatusParser, passClient);

        assertEquals(originalStatus, submittedDeposit.getDepositStatus());
    }

    /**
     * When a status ref is present, and the document it points provides a terminal status, the deposit should be updated.
     *
     * @throws Exception
     */
    @Test
    public void scanWithStatusRefTerminalStatusArchived() throws Exception {
        Deposit.DepositStatus originalStatus = submittedDeposit.getDepositStatus();
        String statusUri = "http://url.to.a.status/feed";
        submittedDeposit.setDepositStatusRef(statusUri);

        when(depositStatusParser.parse(URI.create(statusUri))).thenReturn(SWORD_TERMINAL_STATUS);
        when(swordStatusMapper.map(SWORD_TERMINAL_STATUS)).thenReturn(TERMINAL_STATUS);

        underTest.accept(submittedDeposit);

        verifyZeroInteractions(repoCopyStatusMapper);
        verify(depositStatusParser).parse(URI.create(statusUri));
        verify(swordStatusMapper).map(SWORD_TERMINAL_STATUS);
        verify(passClient).updateResource(submittedDeposit);
        verify(terminalDepositStatusPolicy).accept(TERMINAL_STATUS);

        assertEquals(TERMINAL_STATUS, submittedDeposit.getDepositStatus());
        assertNotEquals(TERMINAL_STATUS, originalStatus);
    }

    /**
     * When a status ref is present, and the document it points provides an intermediate status, the deposit should not
     * be updated.
     *
     * @throws Exception
     */
    @Test
    public void scanWithStatusRefIntermediateStatus() throws Exception {
        Deposit.DepositStatus originalStatus = submittedDeposit.getDepositStatus();
        String statusUri = "http://url.to.a.status/feed";
        submittedDeposit.setDepositStatusRef(statusUri);

        when(depositStatusParser.parse(URI.create(statusUri))).thenReturn(SWORD_INTERMEDIATE_STATUS);
        when(swordStatusMapper.map(SWORD_INTERMEDIATE_STATUS)).thenReturn(INTERMEDIATE_STATUS);

        underTest.accept(submittedDeposit);

        verifyZeroInteractions(repoCopyStatusMapper, passClient);
        verify(depositStatusParser).parse(URI.create(statusUri));
        verify(swordStatusMapper).map(SWORD_INTERMEDIATE_STATUS);
        verify(terminalDepositStatusPolicy).accept(INTERMEDIATE_STATUS);

        assertEquals(originalStatus, submittedDeposit.getDepositStatus());
    }


    /**
     * Repo copy URI is ignored when a deposit status ref is present; i.e. a deposit status ref is preferred over a
     * repo copy.
     *
     * @throws Exception
     */
    @Test
    public void scanWithStatusRefAndRepoCopy() throws Exception {
        String statusUri = "http://url.to.a.status/feed";
        submittedDeposit.setDepositStatusRef(statusUri);
        submittedDeposit.setRepositoryCopy(URI.create("http://repo/copy/uri/is/ignored"));

        when(depositStatusParser.parse(URI.create(statusUri))).thenReturn(SWORD_INTERMEDIATE_STATUS);
        when(swordStatusMapper.map(SWORD_INTERMEDIATE_STATUS)).thenReturn(INTERMEDIATE_STATUS);

        underTest.accept(submittedDeposit);

        verifyZeroInteractions(repoCopyStatusMapper, passClient);
        verify(swordStatusMapper).map(any());
        verify(terminalDepositStatusPolicy).accept(INTERMEDIATE_STATUS);
    }

    /**
     * Repo copy is consulted when the deposit status ref is absent.
     *
     * @throws Exception
     */
    @Test
    public void scanWithNullStatusRefAndRepoCopy() throws Exception {
        Deposit.DepositStatus originalStatus = submittedDeposit.getDepositStatus();
        submittedDeposit.setRepositoryCopy(URI.create("http://repo/copy/uri"));
        RepositoryCopy repoCopy = new RepositoryCopy();
        repoCopy.setCopyStatus(RC_TERMINAL_STATUS);

        when(passClient.readResource(submittedDeposit.getRepositoryCopy(), RepositoryCopy.class)).thenReturn(repoCopy);
        when(repoCopyStatusMapper.map(repoCopy.getCopyStatus())).thenReturn(TERMINAL_STATUS);

        underTest.accept(submittedDeposit);

        verifyZeroInteractions(swordStatusMapper);
        verify(passClient).readResource(submittedDeposit.getRepositoryCopy(), RepositoryCopy.class);
        verify(repoCopyStatusMapper).map(repoCopy.getCopyStatus());
        verify(passClient).updateResource(submittedDeposit);
        verify(terminalDepositStatusPolicy).accept(TERMINAL_STATUS);

        assertEquals(TERMINAL_STATUS, submittedDeposit.getDepositStatus());
        assertNotEquals(originalStatus, submittedDeposit.getDepositStatus());
    }

    /**
     * When a DepositStatus is considered non-terminal, the Deposit should <em>not</em> be updated, and it should
     * maintain its existing deposit state
     * @throws Exception
     */
    @Test
    public void scanResultsInNonTerminalStatus() throws Exception {
        submittedDeposit.setRepositoryCopy(URI.create("http://repo/copy/uri"));
        RepositoryCopy repoCopy = new RepositoryCopy();
        repoCopy.setCopyStatus(RC_INTERMEDIATE_STATUS);
        Deposit.DepositStatus originalStatus = submittedDeposit.getDepositStatus();

        when(passClient.readResource(submittedDeposit.getRepositoryCopy(), RepositoryCopy.class)).thenReturn(repoCopy);
        when(repoCopyStatusMapper.map(repoCopy.getCopyStatus())).thenReturn(INTERMEDIATE_STATUS);

        underTest.accept(submittedDeposit);

        verifyZeroInteractions(swordStatusMapper);
        verify(terminalDepositStatusPolicy).accept(INTERMEDIATE_STATUS);
        verify(passClient, times(0)).updateResource(any());

        assertEquals(originalStatus, submittedDeposit.getDepositStatus());
    }

    /**
     * Dirty deposits - those with a null deposit status - should not be updated.
     *
     * @throws Exception
     */
    @Test
    public void denyDirtyDeposit() throws Exception {
        Deposit dirty = new Deposit();
        dirty.setDepositStatus(null);

        underTest.accept(dirty);

        verifyZeroInteractions(terminalDepositStatusPolicy, passClient, swordStatusMapper, repoCopyStatusMapper);
        verify(intermediateDepositStatusPolicy).accept(null);
    }

    /**
     * Deposits with a terminal status should not be updated.
     *
     * @throws Exception
     */
    @Test
    public void denyTerminalDeposit() throws Exception {
        Deposit terminal = new Deposit();
        terminal.setDepositStatus(TERMINAL_STATUS);

        underTest.accept(terminal);

        verifyZeroInteractions(terminalDepositStatusPolicy, passClient, swordStatusMapper, repoCopyStatusMapper);
        verify(intermediateDepositStatusPolicy).accept(TERMINAL_STATUS);
    }

    /**
     * TODO: Test what happens when parsing or retrieving a status ref goes awry
     * @throws Exception
     */
    @Test
    public void statusRefParsingThrowsException() throws Exception {

    }

    /**
     * TODO: Test what happens when both the Deposit status ref is null and the RepoCopy copy status is null
     * @throws Exception
     */
    @Test
    public void nullStatusRefAndNullRepoCopy() throws Exception {

    }
}