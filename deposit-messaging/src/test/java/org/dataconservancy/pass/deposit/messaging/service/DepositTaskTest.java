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

import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomIntermediateDepositStatus;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomUri;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.abdera.i18n.iri.IRI;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.messaging.model.Packager;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2DepositReceiptResponse;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalPath;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.DefaultConflictHandler;
import org.junit.Before;
import org.junit.Test;
import org.swordapp.client.DepositReceipt;
import org.swordapp.client.SWORDClientException;
import org.swordapp.client.SwordIdentifier;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositTaskTest {

    private DepositUtil.DepositWorkerContext dc;

    private PassClient passClient;

    private Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy;

    private CriticalRepositoryInteraction cri;

    private DepositTask underTest;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        DepositUtil.DepositWorkerContext dwc = new DepositUtil.DepositWorkerContext();
        dc = spy(dwc);
        passClient = mock(PassClient.class);
        intermediateDepositStatusPolicy = mock(Policy.class);
        cri = new CriticalPath(passClient, new DefaultConflictHandler(passClient));
        underTest = new DepositTask(dc, passClient, intermediateDepositStatusPolicy, cri);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void j10sStatementUrlHack() throws Exception {
        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        String prefix = "http://moo";
        String replacement = "http://foo";

        URI dspaceItemUri = randomUri();
        SwordIdentifier dspaceItem = mock(SwordIdentifier.class);
        when(dspaceItem.getHref()).thenReturn(dspaceItemUri.toString());
        SwordIdentifier swordStatement = identifierFor(prefix);

        DepositReceipt dr = mock(DepositReceipt.class);
        Sword2DepositReceiptResponse tr = new Sword2DepositReceiptResponse(dr);
        when(dr.getStatusCode()).thenReturn(200);
        when(dr.getSplashPageLink()).thenReturn(dspaceItem);
        when(dr.getAtomStatementLink()).thenReturn(swordStatement);

        Deposit d = depositContext(dc, tr, passClient);

        underTest.setSwordSleepTimeMs(1); // move things along...
        underTest.setReplacementPrefix(replacement);
        underTest.setPrefixToMatch(prefix);

        underTest.run();

        assertEquals(replacement, d.getDepositStatusRef());
    }

    @Test
    public void j10sStatementUrlHackWithNullValues() throws Exception {
        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        String prefix = "http://moo";
        String replacement = null;

        URI dspaceItemUri = randomUri();
        SwordIdentifier dspaceItem = mock(SwordIdentifier.class);
        when(dspaceItem.getHref()).thenReturn(dspaceItemUri.toString());
        SwordIdentifier swordStatement = identifierFor(prefix);

        DepositReceipt dr = mock(DepositReceipt.class);
        Sword2DepositReceiptResponse tr = new Sword2DepositReceiptResponse(dr);
        when(dr.getStatusCode()).thenReturn(200);
        when(dr.getSplashPageLink()).thenReturn(dspaceItem);
        when(dr.getAtomStatementLink()).thenReturn(swordStatement);

        Deposit d = depositContext(dc, tr, passClient);

        underTest.setSwordSleepTimeMs(1); // move things along...
        underTest.setReplacementPrefix(replacement);
        underTest.setPrefixToMatch(prefix);

        underTest.run();

        assertEquals(prefix, d.getDepositStatusRef());
    }

    @Test
    public void j10sStatementUrlHackWithNonMatchingValues() throws Exception {
        when(intermediateDepositStatusPolicy.test(any())).thenReturn(true);
        String href = "http://baz";
        String prefix = "http://moo";
        String replacement = "http://foo";

        URI dspaceItemUri = randomUri();
        SwordIdentifier dspaceItem = mock(SwordIdentifier.class);
        when(dspaceItem.getHref()).thenReturn(dspaceItemUri.toString());
        SwordIdentifier swordStatement = identifierFor(href);

        DepositReceipt dr = mock(DepositReceipt.class);
        Sword2DepositReceiptResponse tr = new Sword2DepositReceiptResponse(dr);
        when(dr.getStatusCode()).thenReturn(200);
        when(dr.getSplashPageLink()).thenReturn(dspaceItem);
        when(dr.getAtomStatementLink()).thenReturn(swordStatement);

        Deposit d = depositContext(dc, tr, passClient);

        underTest.setSwordSleepTimeMs(1); // move things along...
        underTest.setReplacementPrefix(replacement);
        underTest.setPrefixToMatch(prefix);

        underTest.run();

        assertEquals(href, d.getDepositStatusRef());
    }

    /**
     * Populates the supplied {@code depositContext} with a {@code Repository}, {@code Submission} and
     * {@code Deposit}.
     *
     * @param depositContext
     * @return
     */
    private static Deposit depositContext(DepositUtil.DepositWorkerContext depositContext, TransportResponse tr,
                                          PassClient passClient) {
        Repository r = new Repository();
        r.setId(randomUri());
        depositContext.repository(r);

        Submission s = new Submission();
        s.setId(randomUri());
        s.setAggregatedDepositStatus(Submission.AggregatedDepositStatus.IN_PROGRESS);
        depositContext.submission(s);

        Deposit d = new Deposit();
        d.setId(randomUri());
        d.setSubmission(s.getId());
        d.setDepositStatus(randomIntermediateDepositStatus.get());
        depositContext.deposit(d);

        when(passClient.readResource(d.getId(), Deposit.class)).thenReturn(d);
        when(passClient.updateAndReadResource(any(), any())).thenAnswer((inv) -> inv.getArgument(0));
        when(passClient.createAndReadResource(any(), any())).thenAnswer((inv) -> inv.getArgument(0));

        Assembler assembler = mock(Assembler.class);
        PackageStream stream = mock(PackageStream.class);
        Packager packager = mock(Packager.class);
        Transport transport = mock(Transport.class);
        TransportSession session = mock(TransportSession.class);

        Map<String, String> packagerConfig = new HashMap<>();
        when(packager.getAssembler()).thenReturn(assembler);
        when(packager.getConfiguration()).thenReturn(packagerConfig);
        when(assembler.assemble(any(), anyMap())).thenReturn(stream);
        when(packager.getTransport()).thenReturn(transport);
        when(transport.open(anyMap())).thenReturn(session);
        when(session.send(eq(stream), any())).thenReturn(tr);

        when(depositContext.packager()).thenReturn(packager);

        return d;
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