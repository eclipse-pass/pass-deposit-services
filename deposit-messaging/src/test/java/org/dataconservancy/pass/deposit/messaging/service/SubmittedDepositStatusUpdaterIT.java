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
import org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomFeedStatusParser;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"spring.jms.listener.auto-startup=false"})
public class SubmittedDepositStatusUpdaterIT {

    /**
     * Represents the status of a <em>dirty</em> deposit (i.e. a Deposit that has not had its bytes transferred
     * successfully to a {@code Repository})
     */
    private final Deposit.DepositStatus DEPOSIT_STATUS_DIRTY = null;

    /**
     * Represents the status of a Deposit with a <em>terminal</em> state (i.e. a Deposit that has been successfully
     * transferred to a {@code Repository} and has been accepted (as in this case) or rejected by the remote {@code
     * Repository})
     */
    private final Deposit.DepositStatus DEPOSIT_STATUS_TERMINAL = Deposit.DepositStatus.ACCEPTED;

    /**
     * Represents the status of a Deposit with an <em>intermediate</em> state (i.e. a Deposit that has been successfully
     * transferred to a {@code Repository} but its <em>terminal</em> status cannot be determined (it has not been
     * accepted or rejected by the remote {@code Repository})
     */
    private final Deposit.DepositStatus DEPOSIT_STATUS_INTERMEDIATE = Deposit.DepositStatus.SUBMITTED;

    /**
     * A {@code Submission} that has been submitted by an interactive user of the PASS UI (e.g. {@code submitted} =
     * {@code true})
     */
    private Submission submission;

    /**
     * A dirty deposit linked to the {@code submission}
     */
    private Deposit dirtyDeposit;

    /**
     * A terminal deposit linked to the {@code submission}
     */
    private Deposit terminalDeposit;

    /**
     * An intermediate deposit linked to the {@code submission}
     */
    private Deposit intermediateDeposit;

    /**
     * Represents the status of a RepositoryCopy with a <em>terminal</em> state.
     */
    private final RepositoryCopy.CopyStatus COPY_STATUS_TERMINAL = RepositoryCopy.CopyStatus.COMPLETE;

    /**
     * Represents the status of a RepositoryCopy with a <em>intermediate</em> state.
     */
    private final RepositoryCopy.CopyStatus COPY_STATUS_INTERMEDIATE = RepositoryCopy.CopyStatus.IN_PROGRESS;

    /**
     * A terminal repository copy
     */
    private RepositoryCopy terminalRepoCopy;

    /**
     * An intermediate repository copy
     */
    private RepositoryCopy intermediateRepoCopy;


    /**
     * A repository copy with a null copy status
     */
    private RepositoryCopy nullStatusRepoCopy;

    /**
     * Client to communicate with the Fedora repository
     */
    @Autowired
    private PassClient passClient;

    /**
     * The status updater under test
     */
    @Autowired
    private SubmittedDepositStatusUpdater underTest;

    /**
     * Populates the Fedora repository with
     * <ol>
     *     <li>{@code Submission} with {@code submitted} = {@code true}, and {@code aggregatedDepositStatus} = {@code IN_PROGRESS}</li>
     *     <li>{@code Deposit} with {@code depositStatus} = {@code null} (i.e. <em>dirty</em>), linked to the {@code Submission}</li>
     *     <li>{@code Deposit} with {@code depositStatus} = {@code ACCEPTED} (i.e. <em>terminal</em>), linked to the {@code Submission}</li>
     *     <li>{@code Deposit} with {@code depositStatus} = {@code SUBMITTED} (i.e. <em>intermediate</em>), linked to the {@code Submission}</li>
     * </ol>
     * Each resource is created, and then re-read from the repository to obtain any state that may be set by the repository (e.g. repository identifiers).
     * <p>
     * (TODO: {@code Submission} {@code aggregatedDepositStatus} <em>is not</em> used by Deposit Services.)
     * </p>
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        submission = new Submission();
        submission.setSource(Submission.Source.PASS);
        submission.setSubmitted(true);
        submission.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.IN_PROGRESS);
        submission = passClient.readResource(passClient.createResource(submission), Submission.class);

        dirtyDeposit = new Deposit();
        dirtyDeposit.setDepositStatus(DEPOSIT_STATUS_DIRTY);
        dirtyDeposit.setSubmission(submission.getId());
        dirtyDeposit = passClient.readResource(passClient.createResource(dirtyDeposit), Deposit.class);

        terminalDeposit = new Deposit();
        terminalDeposit.setDepositStatus(DEPOSIT_STATUS_TERMINAL);
        terminalDeposit.setSubmission(submission.getId());
        terminalDeposit = passClient.readResource(passClient.createResource(terminalDeposit), Deposit.class);

        intermediateDeposit = new Deposit();
        intermediateDeposit.setDepositStatus(DEPOSIT_STATUS_INTERMEDIATE);
        intermediateDeposit.setSubmission(submission.getId());
        intermediateDeposit = passClient.readResource(passClient.createResource(intermediateDeposit), Deposit.class);

        intermediateRepoCopy = new RepositoryCopy();
        intermediateRepoCopy.setCopyStatus(COPY_STATUS_INTERMEDIATE);
        intermediateRepoCopy = passClient.readResource(passClient.createResource(intermediateRepoCopy),
                RepositoryCopy.class);

        terminalRepoCopy = new RepositoryCopy();
        terminalRepoCopy.setCopyStatus(COPY_STATUS_TERMINAL);
        terminalRepoCopy = passClient.readResource(passClient.createResource(terminalRepoCopy), RepositoryCopy.class);

        nullStatusRepoCopy = new RepositoryCopy();
        nullStatusRepoCopy.setCopyStatus(null);
        nullStatusRepoCopy = passClient.readResource(passClient.createResource(nullStatusRepoCopy),
                RepositoryCopy.class);
    }

    /**
     * Submit a <em>dirty</em> Deposit to the updater under test.
     *
     * Status of the submission and deposit in the repository should not change, because the
     * SubmittedDepositStatusUpdater should not accept or process dirty deposits.
     *
     * @throws Exception
     */
    @Test
    public void dirtyDepositRejected() throws Exception {
        underTest.accept(dirtyDeposit);

        assertEquals(submission, passClient.readResource(submission.getId(), Submission.class));
        assertEquals(dirtyDeposit, passClient.readResource(dirtyDeposit.getId(), Deposit.class));
    }

    /**
     * Submit a <em>dirty</em> Deposit to the updater under test.
     *
     * Status of the submission and deposit in the repository should not change, because the
     * {@link SubmittedDepositStatusUpdater} should not accept or process terminal deposits.
     * 
     * @throws Exception
     */
    @Test
    public void terminalDepositRejected() throws Exception {
        underTest.accept(terminalDeposit);

        assertEquals(submission, passClient.readResource(submission.getId(), Submission.class));
        assertEquals(terminalDeposit, passClient.readResource(terminalDeposit.getId(), Deposit.class));
    }

    /**
     * Submit an <em>intermediate</em> Deposit to the updater under test that links to an Atom statement mapping to a
     * <em>terminal</em> Deposit status.
     *
     * The Deposit should be accepted for processing because it is in an <em>intermediate</em> state.
     *
     * The Atom statement should be used for updating the status because it is present, and the updater prefers Atom
     * statements to RepositoryCopy copy status.
     *
     * The status of the deposit in the repository should be updated to a <em>terminal</em> status, because the Deposit
     * links to an Atom statement that should parse as terminal.
     *
     * @throws Exception
     */
    @Test
    public void statusRefTerminalStatus() throws Exception {
        // Update the intermediate Deposit resource to include a deposit status ref to an Atom statement that will
        // map to a terminal status
        intermediateDeposit.setDepositStatusRef(terminalDepositStatusRef());

        // Put the updated resource in the repository.  This technically isn't required, since it is supplied to
        // the class under test, but this is what _would_ be in the repository before the class under test is invoked
        passClient.updateResource(intermediateDeposit);

        // Retrieve the Deposit from the repository, in case putting the Deposit _into_ the repository modified its
        // state.  We want to insure that the class under test is getting the resource as it exists in the repository.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure the round-trips to the repository so far haven't violated the pre-conditions for this test
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(terminalDepositStatusRef(), intermediateDeposit.getDepositStatusRef());

        // Invoke the test
        underTest.accept(intermediateDeposit);

        // The class under test should have updated the Deposit and put it in the repository.  Get the latest.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure that the Deposit status was updated as expected; the deposit status ref should still be present
        assertEquals(DEPOSIT_STATUS_TERMINAL, intermediateDeposit.getDepositStatus());
        assertEquals(terminalDepositStatusRef(), intermediateDeposit.getDepositStatusRef());
    }

    /**
     * Submit an <em>intermediate</em> Deposit to the updater under test that links to an Atom statement mapping to a
     * <em>intermediate</em> Deposit status.
     *
     * The Deposit should be accepted for processing because it is in an <em>intermediate</em> state.
     *
     * The Atom statement should be used for updating the status because it is present, and the updater prefers Atom
     * statements to RepositoryCopy copy status.
     *
     * The status of the deposit in the repository should <em>remain</em> in the <em>intermediate</em> state, because
     * the Deposit links to an Atom statement that should parse as intermediate (i.e. a Repository that has not yet
     * provided a status update indicating deposit success or failure).
     *
     * @throws Exception
     */
    @Test
    public void statusRefIntermediateStatus() throws Exception {
        // Update the intermediate Deposit resource to include a deposit status ref to an Atom statement that will
        // map to an intermediate
        intermediateDeposit.setDepositStatusRef(intermediateDepositStatusRef());

        // Put the updated resource in the repository.  This technically isn't required, since it is supplied to
        // the class under test, but this is what _would_ be in the repository before the class under test is invoked
        passClient.updateResource(intermediateDeposit);

        // Retrieve the Deposit from the repository, in case putting the Deposit _into_ the repository modified its
        // state.  We want to insure that the class under test is getting the resource as it exists in the repository.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure the round-trips to the repository so far haven't violated the pre-conditions for this test
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(intermediateDepositStatusRef(), intermediateDeposit.getDepositStatusRef());

        // Invoke the test
        underTest.accept(intermediateDeposit);

        // The class under test should *not* have updated the Deposit.  But attempt to get the latest just in case.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure that the Deposit status was *not updated*; the deposit status ref should still be present
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(intermediateDepositStatusRef(), intermediateDeposit.getDepositStatusRef());
    }

    @Test
    @Ignore("TODO")
    public void nullStatusRef() throws Exception {

    }

    /**
     * Submit an <em>intermediate</em> Deposit to the updater under test that links to no Atom statement, and that has
     * no {@code RepositoryCopy}s.
     *
     * The Deposit should be accepted for processing because it is in an <em>intermediate</em> state.
     *
     * There is no Atom resource and no {@code RepositoryCopy} resource to be used to determine the deposit status.
     *
     * The status of the deposit in the repository should <em>remain</em> in the <em>intermediate</em> state, because
     * there is no Atom statement and no {@code RepositoryCopy}.
     *
     * @throws Exception
     */
    @Test
    public void nullStatusRefAndNullRepoCopy() throws Exception {
        // assert initial state
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(null, intermediateDeposit.getDepositStatusRef());
        assertEquals(null, intermediateDeposit.getRepositoryCopy());

        // Invoke the test
        underTest.accept(intermediateDeposit);

        // The class under test should *not* have updated the Deposit.  But attempt to get the latest just in case.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure that the Deposit status was *not updated*; the deposit status ref should not be present; no
        // repository copy should be present
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(null, intermediateDeposit.getDepositStatusRef());
        assertEquals(null, intermediateDeposit.getRepositoryCopy());
    }

    /**
     * Submit an <em>intermediate</em> Deposit to the updater under test that links to no Atom statement, and that has
     * a {@code RepositoryCopy} with a <em>terminal</em> copy status.
     *
     * The Deposit should be accepted for processing because it is in an <em>intermediate</em> state.
     *
     * There is no Atom resource to be used to determine the deposit status.  The non-{@code null} {@code
     * RepositoryCopy} {@code copyStatus} ought to be used.
     *
     * The status of the deposit in the repository should <em>be updated</em> to a <em>terminal</em> state, because
     * there is a {@code RepositoryCopy} present with a copy status that maps to a <em>terminal</em> deposit status.
     *
     * @throws Exception
     */
    @Test
    public void repoCopyTerminalStatus() throws Exception {
        // Update the intermediate Deposit resource to include a RepositoryCopy with a terminal copy status
        intermediateDeposit.setRepositoryCopy(terminalRepoCopy.getId());

        // Put the updated resource in the repository.  This technically isn't required, since it is supplied to
        // the class under test, but this is what _would_ be in the repository before the class under test is invoked
        passClient.updateResource(intermediateDeposit);

        // Retrieve the Deposit from the repository, in case putting the Deposit _into_ the repository modified its
        // state.  We want to insure that the class under test is getting the resource as it exists in the repository.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure the round-trips to the repository so far haven't violated the pre-conditions for this test
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(terminalRepoCopy.getId(), intermediateDeposit.getRepositoryCopy());
        assertNull(intermediateDeposit.getDepositStatusRef());

        // Invoke the test
        underTest.accept(intermediateDeposit);

        // The class under test should have updated the Deposit; retrieve the updated Deposit
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure that the Deposit status was updated; the RepositoryCopy should still be present
        assertEquals(DEPOSIT_STATUS_TERMINAL, intermediateDeposit.getDepositStatus());
        assertEquals(terminalRepoCopy.getId(), intermediateDeposit.getRepositoryCopy());
    }

    /**
     * Submit an <em>intermediate</em> Deposit to the updater under test that links to no Atom statement, and that has
     * a {@code RepositoryCopy} with a <em>intermediate</em> copy status.
     *
     * The Deposit should be accepted for processing because it is in an <em>intermediate</em> state.
     *
     * There is no Atom resource to be used to determine the deposit status.  The non-{@code null} {@code
     * RepositoryCopy} {@code copyStatus} ought to be used.
     *
     * The status of the deposit in the repository should <em>remain</em> in the <em>intermediate</em> state, because
     * there is a {@code RepositoryCopy} present with a copy status that maps to a <em>intermediate</em> deposit status.
     *
     * @throws Exception
     */
    @Test
    public void repoCopyIntermediateStatus() throws Exception {
        // Update the intermediate Deposit resource to include a RepositoryCopy with an intermediate copy status
        intermediateDeposit.setRepositoryCopy(intermediateRepoCopy.getId());

        // Put the updated resource in the repository.  This technically isn't required, since it is supplied to
        // the class under test, but this is what _would_ be in the repository before the class under test is invoked
        passClient.updateResource(intermediateDeposit);

        // Retrieve the Deposit from the repository, in case putting the Deposit _into_ the repository modified its
        // state.  We want to insure that the class under test is getting the resource as it exists in the repository.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure the round-trips to the repository so far haven't violated the pre-conditions for this test
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(intermediateRepoCopy.getId(), intermediateDeposit.getRepositoryCopy());
        assertNull(intermediateDeposit.getDepositStatusRef());

        // Invoke the test
        underTest.accept(intermediateDeposit);

        // The class under test should *not* have updated the Deposit; retrieve the Deposit
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure that the Deposit status was *not updated*; the RepositoryCopy should still be present
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(intermediateRepoCopy.getId(), intermediateDeposit.getRepositoryCopy());
    }


    /**
     * Submit an <em>intermediate</em> Deposit to the updater under test that links to no Atom statement, and that has
     * a {@code RepositoryCopy} with a <em>null</em> copy status.
     *
     * The Deposit should be accepted for processing because it is in an <em>intermediate</em> state.
     *
     * There is no Atom resource to be used to determine the deposit status.  The non-{@code null} {@code
     * RepositoryCopy} {@code copyStatus} ought to be used.
     *
     * The status of the deposit in the repository should <em>remain</em> in the <em>intermediate</em> state, because
     * there is a {@code RepositoryCopy} present with a copy status that maps to a <em>intermediate</em> deposit status.
     *
     * @throws Exception
     */
    @Test
    public void repoCopyNullStatus() throws Exception {
        // Update the intermediate Deposit resource to include a RepositoryCopy with an intermediate copy status
        intermediateDeposit.setRepositoryCopy(nullStatusRepoCopy.getId());

        // Put the updated resource in the repository.  This technically isn't required, since it is supplied to
        // the class under test, but this is what _would_ be in the repository before the class under test is invoked
        passClient.updateResource(intermediateDeposit);

        // Retrieve the Deposit from the repository, in case putting the Deposit _into_ the repository modified its
        // state.  We want to insure that the class under test is getting the resource as it exists in the repository.
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure the round-trips to the repository so far haven't violated the pre-conditions for this test
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(nullStatusRepoCopy.getId(), intermediateDeposit.getRepositoryCopy());
        assertNull(intermediateDeposit.getDepositStatusRef());

        // Invoke the test
        underTest.accept(intermediateDeposit);

        // The class under test should *not* have updated the Deposit; retrieve the Deposit
        intermediateDeposit = passClient.readResource(intermediateDeposit.getId(), Deposit.class);

        // Insure that the Deposit status was *not updated*; the RepositoryCopy should still be present
        assertEquals(DEPOSIT_STATUS_INTERMEDIATE, intermediateDeposit.getDepositStatus());
        assertEquals(nullStatusRepoCopy.getId(), intermediateDeposit.getRepositoryCopy());
    }

    /**
     * Returns the classpath resource for an Atom statement providing a terminal deposit status.
     *
     * @return
     */
    private static String terminalDepositStatusRef() {
        return resolveResource(AtomFeedStatusParser.class, "AtomStatusParser-archived.xml");
    }

    /**
     * Returns the classpath resource for an Atom statement providing an intermediate deposit status.
     *
     * @return
     */
    private static String intermediateDepositStatusRef() {
        return resolveResource(AtomFeedStatusParser.class, "AtomStatusParser-inprogress.xml");
    }

    /**
     * Resolves the supplied classpath resource relative to the supplied class.
     *
     * @param parserClass
     * @param atomResource
     * @return
     */
    private static String resolveResource(Class<?> parserClass, String atomResource) {
        URL atomStatement = parserClass.getResource(atomResource);
        assertTrue("Expected classpath resource to exist as a file: " + atomStatement, new File(atomStatement.getPath
                ()).exists());
        return atomStatement.toString();
    }

}