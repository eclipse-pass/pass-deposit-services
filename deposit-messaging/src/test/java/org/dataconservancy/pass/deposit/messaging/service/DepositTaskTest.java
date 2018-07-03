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

import org.apache.abdera.i18n.iri.IRI;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.messaging.support.CriticalRepositoryInteraction;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2DepositReceiptResponse;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.swordapp.client.DepositReceipt;
import org.swordapp.client.SWORDClientException;
import org.swordapp.client.SwordIdentifier;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositTaskTest {

    private DepositUtil.DepositWorkerContext dc;

    private PassClient passClient;

    private Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private DepositTaskHelper depositHelper;

    private DepositTask underTest;


    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        dc = mock(DepositUtil.DepositWorkerContext.class);
        passClient = mock(PassClient.class);
        intermediateDepositStatusPolicy = mock(Policy.class);
        cri = mock(CriticalRepositoryInteraction.class);
        depositHelper = mock(DepositTaskHelper.class);
        underTest = new DepositTask(dc, passClient, intermediateDepositStatusPolicy, cri, depositHelper);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void j10sStatementUrlHack() throws Exception {
        ArgumentCaptor<String> depositStatusRef = ArgumentCaptor.forClass(String.class);
        String prefix = "http://moo";
        String replacement = "http://foo";

        Deposit d = depositContext(dc);
        Sword2DepositReceiptResponse tr = transportResponse();
        criSuccess(d, tr, cri);
        DepositReceipt depositReceipt = depositReceipt(tr);
        SwordIdentifier statementLink = identifierFor(prefix);
        when(depositReceipt.getAtomStatementLink()).thenReturn(statementLink);

        underTest.setSwordSleepTimeMs(1); // move things along...
        underTest.setReplacementPrefix(replacement);
        underTest.setPrefixToMatch(prefix);

        underTest.run();

        verify(d).setDepositStatusRef(depositStatusRef.capture());

        assertEquals(replacement, depositStatusRef.getValue());
    }

    @Test
    public void j10sStatementUrlHackWithNullValues() throws Exception {
        ArgumentCaptor<String> depositStatusRef = ArgumentCaptor.forClass(String.class);
        String prefix = "http://moo";

        Deposit d = depositContext(dc);
        Sword2DepositReceiptResponse tr = transportResponse();
        criSuccess(d, tr, cri);
        DepositReceipt depositReceipt = depositReceipt(tr);
        SwordIdentifier statementLink = identifierFor(prefix);
        when(depositReceipt.getAtomStatementLink()).thenReturn(statementLink);

        underTest.setSwordSleepTimeMs(1); // move things along...
        underTest.setReplacementPrefix(null);
        underTest.setPrefixToMatch(null);

        underTest.run();

        verify(d).setDepositStatusRef(depositStatusRef.capture());

        assertEquals(prefix, depositStatusRef.getValue());
    }

    @Test
    public void j10sStatementUrlHackWithNonMatchingValues() throws Exception {
        ArgumentCaptor<String> depositStatusRef = ArgumentCaptor.forClass(String.class);
        String href = "http://baz";
        String prefix = "http://moo";
        String replacement = "http://foo";

        Deposit d = depositContext(dc);
        Sword2DepositReceiptResponse tr = transportResponse();
        criSuccess(d, tr, cri);
        DepositReceipt depositReceipt = depositReceipt(tr);
        SwordIdentifier statementLink = identifierFor(href);
        when(depositReceipt.getAtomStatementLink()).thenReturn(statementLink);

        underTest.setSwordSleepTimeMs(1); // move things along...
        underTest.setReplacementPrefix(replacement);
        underTest.setPrefixToMatch(prefix);
        assertNotEquals(href, prefix);

        underTest.run();

        verify(d).setDepositStatusRef(depositStatusRef.capture());

        assertEquals(href, depositStatusRef.getValue());
    }

    /**
     * Populates the supplied {@code depositContext} with a mock {@code Repository}, {@code Submission} and
     * {@code Deposit}.
     *
     * @param depositContext
     * @return
     */
    private static Deposit depositContext(DepositUtil.DepositWorkerContext depositContext) {
        Repository r = mock(Repository.class);
        when(r.getId()).thenReturn(URI.create("uuid:" + UUID.randomUUID().toString()));
        Submission s = mock(Submission.class);
        when(depositContext.submission()).thenReturn(s);
        when(depositContext.repository()).thenReturn(r);
        Deposit d = mock(Deposit.class);
        when(depositContext.deposit()).thenReturn(d);
        return d;
    }

    /**
     * Mocks a successful CRI that answers the supplied {@code deposit} as the CRI {@code resource()} and the supplied
     * {@code transportResponse} as the CRI {@code response()}.
     *
     * @param deposit
     * @param transportResponse
     * @param cri
     */
    @SuppressWarnings("unchecked")
    private static void criSuccess(Deposit deposit, Sword2DepositReceiptResponse transportResponse, CriticalRepositoryInteraction cri) {
        CriticalRepositoryInteraction.CriticalResult cr = mock(CriticalRepositoryInteraction.CriticalResult.class);
        when(cr.success()).thenReturn(true);
        when(cr.resource()).thenReturn(Optional.of(deposit));
        when(cr.result()).thenReturn(Optional.of(transportResponse));
        when(cri.performCritical(any(), eq(Deposit.class), any(Predicate.class), any(BiPredicate.class),
                any(Function.class))).thenReturn(cr);
    }

    /**
     * Answers a mock {@link DepositReceipt} that is attached to the supplied {@link Sword2DepositReceiptResponse}
     *
     * @param tr
     * @return
     */
    private static DepositReceipt depositReceipt(Sword2DepositReceiptResponse tr) {
        DepositReceipt depositReceipt = mock(DepositReceipt.class);
        when(tr.getReceipt()).thenReturn(depositReceipt);
        return depositReceipt;
    }

    /**
     * Answers a mock {@link Sword2DepositReceiptResponse}
     *
     * @return
     */
    private static Sword2DepositReceiptResponse transportResponse() {
        return mock(Sword2DepositReceiptResponse.class);
    }

    /**
     * Answers a mock {@link SwordIdentifier} that returns an {@code IRI} from the supplied {@code href}
     *
     * @param href
     * @return
     * @throws SWORDClientException
     */
    private static SwordIdentifier identifierFor(String href) throws SWORDClientException {
        SwordIdentifier identifier = mock(SwordIdentifier.class);
        when(identifier.getIRI()).thenReturn(new IRI(href));
        return identifier;
    }
}