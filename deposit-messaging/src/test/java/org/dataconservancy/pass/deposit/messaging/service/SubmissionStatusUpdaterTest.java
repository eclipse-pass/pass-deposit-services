/*
 * Copyright 2019 Johns Hopkins University
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

import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomSubmissionStatus;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomSubmissionStatusExcept;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomUri;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.SubmissionStatusService;
import org.dataconservancy.pass.deposit.messaging.service.SubmissionStatusUpdater.CriFunc;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Submission.SubmissionStatus;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionStatusUpdaterTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusUpdater.class);

    private SubmissionStatusUpdater underTest;

    private SubmissionStatusService statusService;

    private PassClient passClient;

    private CriticalRepositoryInteraction cri;

    @Before
    public void setUp() throws Exception {
        statusService = mock(SubmissionStatusService.class);
        passClient = mock(PassClient.class);
        cri = mock(CriticalRepositoryInteraction.class);

        underTest = new SubmissionStatusUpdater(statusService, passClient, cri);
    }

    /**
     * A submitted flag of TRUE and a status of anything other than CANCELLED or COMPLETE should meet the precondition
     */
    @Test
    public void criPreconditionSuccess() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(
            randomSubmissionStatusExcept(SubmissionStatus.COMPLETE, SubmissionStatus.CANCELLED));
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertTrue(CriFunc.preCondition.test(s));
    }

    /**
     * A null status should fail the precondition
     */
    @Test
    public void criPreconditionFailsNullStatus() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(null);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A null submitted flag should fail the precondition
     */
    @Test
    public void criPreconditionFailsNullSubmit() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        when(s.getSubmitted()).thenReturn(null);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A submitted flag of FALSE will fail the precondition
     */
    @Test
    public void criPreconditionFailsSubmitted() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.SUBMITTED);
        when(s.getSubmitted()).thenReturn(Boolean.FALSE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A status of COMPLETE will fail the precondition
     */
    @Test
    public void criPreconditionFailsStatusComplete() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.COMPLETE);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * A status of CANCELLED will fail the precondition
     */
    @Test
    public void criPreconditionFailsStatusCancelled() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(SubmissionStatus.CANCELLED);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.preCondition.test(s));
    }

    /**
     * Every status except CANCELLED and COMPLETE should meet the precondition
     */
    @Test
    public void criPreconditionSuccessAllStatus() {
        Submission s = mock(Submission.class);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        Stream.of(SubmissionStatus.values())
              .filter(status -> status != SubmissionStatus.CANCELLED && status != SubmissionStatus.COMPLETE)
              .peek(status -> when(s.getSubmissionStatus()).thenReturn(status))
              .peek(status -> LOG.trace("Testing status {}", status))
              .forEach(status -> assertTrue(CriFunc.preCondition.test(s)));
    }

    /**
     * Any non-null status and a submission flag set to true should result in a successful post-condition
     */
    @Test
    public void criPostconditionSuccess() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(randomSubmissionStatus());
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertTrue(CriFunc.postCondition.test(s));
    }

    /**
     * A null or false submission flag should fail the post condition
     */
    @Test
    public void criPostconditionFailSubmissionFlag() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(randomSubmissionStatus());
        when(s.getSubmitted()).thenReturn(Boolean.FALSE);

        assertFalse(CriFunc.postCondition.test(s));
    }

    /**
     * A null or false submission flag should fail the post condition
     */
    @Test
    public void criPostconditionFailNullSubmissionFlag() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(randomSubmissionStatus());
        when(s.getSubmitted()).thenReturn(null);

        assertFalse(CriFunc.postCondition.test(s));
    }

    /**
     * A null status should fail the post condition
     */
    @Test
    public void criPostconditionFailNullStatus() {
        Submission s = mock(Submission.class);
        when(s.getSubmissionStatus()).thenReturn(null);
        when(s.getSubmitted()).thenReturn(Boolean.TRUE);

        assertFalse(CriFunc.postCondition.test(s));
    }

    /**
     * The critical function ought to invoke the status service and modify the status
     */
    @Test
    public void criCriticalInvokesSubmissionStatus() {
        Submission s = mock(Submission.class);
        when(statusService.calculateSubmissionStatus(s)).thenReturn(randomSubmissionStatus());
        assertNotNull(CriFunc.critical(statusService).apply(s));
        verify(statusService).calculateSubmissionStatus(s);
    }

    /**
     * the toUpdate method should not try to find Submissions with a status of COMPLETE or CANCELLED.
     * the toUpdate method should try to find Submissions with all other statuses.
     */
    @Test
    public void toUpdateCollectsAllButCompleteAndCancelled() {
        when(passClient.findAllByAttribute(eq(Submission.class), eq("submissionStatus"), any()))
            .then(inv -> {
                String status = inv.getArgument(2);
                assertFalse(status.equalsIgnoreCase(SubmissionStatus.COMPLETE.name()));
                assertFalse(status.equalsIgnoreCase(SubmissionStatus.CANCELLED.name()));
                return Collections.emptySet(); // don't care about the result
            });
        SubmissionStatusUpdater.toUpdate(passClient);

        verify(passClient, times(SubmissionStatus.values().length - 2))
            .findAllByAttribute(eq(Submission.class), eq("submissionStatus"), any());
    }

    /**
     * invoking doUpdate(Collection) with a non-empty collection should invoke the CRI for every URI.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void doUpdateInvokesCri() {
        URI submissionUri = randomUri();
        underTest.doUpdate(Collections.singleton(submissionUri));

        verify(cri, times(1)).performCritical(eq(submissionUri), eq(Submission.class), any(), (Predicate) any(), any());
    }

    /**
     * invoking doUpdate() should invoke the pass client to find all submisssions that are not CANCELLED or COMPLETE,
     * and then invoke the CRI for every discovered URI
     */
    @Test
    @SuppressWarnings("unchecked")
    public void doUpdateInvokesPassClientAndCri() {
        URI submissionUri = randomUri();
        when(passClient.findAllByAttribute(eq(Submission.class), eq("submissionStatus"), any())).thenReturn(
            Collections.singleton(submissionUri));

        underTest.doUpdate();

        verify(passClient, times(SubmissionStatus.values().length - 2)).findAllByAttribute(eq(Submission.class),
                                                                                           eq("submissionStatus"),
                                                                                           any());
        verify(cri, times(1)).performCritical(eq(submissionUri), eq(Submission.class), any(), (Predicate) any(), any());

    }

}