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
package org.dataconservancy.pass.deposit.messaging.status;

import org.dataconservancy.pass.deposit.messaging.config.repository.BasicAuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.StatusMapping;
import org.dataconservancy.pass.model.Deposit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;

import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_ARCHIVED;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DefaultDepositStatusProcessorTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private DefaultDepositStatusProcessor underTest;

    private DepositStatusResolver<URI, URI> resolver;

    private StatusMapping mapping;

    private Deposit deposit;

    private String depositStatusRefBaseUrl = "http://example.org/";
    private String depositStatusRef = depositStatusRefBaseUrl + "statement.atom";

    private BasicAuthRealm authRealm;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        resolver = mock(DepositStatusResolver.class);
        mapping = mock(StatusMapping.class);
        underTest = new DefaultDepositStatusProcessor(resolver);
        deposit = mock(Deposit.class);
        when(deposit.getDepositStatusRef()).thenReturn(depositStatusRef);
        authRealm = mock(BasicAuthRealm.class);
        when(authRealm.getBaseUrl()).thenReturn(depositStatusRefBaseUrl);
    }

    @Test
    public void processingOk() throws Exception {
        URI refUri = URI.create(depositStatusRef);
        when(resolver.resolve(refUri, authRealm)).thenReturn(SWORD_STATE_ARCHIVED.asUri());
        when(mapping.getStatusMap()).thenReturn(new HashMap<String, String>() {
            {
                put(SWORD_STATE_ARCHIVED.asUri().toString(), Deposit.DepositStatus.ACCEPTED.asUri().toString());
            }
        });

        assertEquals(Deposit.DepositStatus.ACCEPTED,
                underTest.process(deposit, Collections.singletonList(authRealm), mapping));

        verify(resolver).resolve(refUri, authRealm);
        verify(mapping).getStatusMap();
    }

    @Test
    public void mappingReturnsUnknownDepositStatus() throws Exception {
        String badDepositStatusUri = "http://foo/uri";
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(badDepositStatusUri);

        URI refUri = URI.create(depositStatusRef);
        when(resolver.resolve(refUri, authRealm)).thenReturn(SWORD_STATE_ARCHIVED.asUri());
        when(mapping.getStatusMap()).thenReturn(new HashMap<String, String>() {
            {

                put(SWORD_STATE_ARCHIVED.asUri().toString(), badDepositStatusUri);
            }
        });

        underTest.process(deposit, Collections.singletonList(authRealm), mapping);
        verify(resolver).resolve(refUri, authRealm);
        verify(mapping);
    }

    @Test
    public void parsingReturnsNullSwordStatus() throws Exception {
        when(resolver.resolve(any(), any())).thenReturn(null);

        assertNull(underTest.process(deposit, null, mapping));

        verify(resolver).resolve(any(), any());
        verifyZeroInteractions(mapping);
    }

    @Test
    public void mappingReturnsNullDepositStatus() throws Exception {
        when(resolver.resolve(any(), any())).thenReturn(SWORD_STATE_INPROGRESS.asUri());
        when(mapping.getStatusMap()).thenReturn(Collections.emptyMap());
        when(mapping.getDefaultMapping()).thenReturn(null);

        assertNull(underTest.process(deposit, null, mapping));

        verify(resolver).resolve(any(), any());
        verify(mapping).getStatusMap();
        verify(mapping).getDefaultMapping();
    }

    @Test
    public void parsingThrowsRuntimeException() throws Exception {
        when(resolver.resolve(any(), any())).thenThrow(new RuntimeException("Expected"));
        expectedException.expectMessage("Expected");
        expectedException.expect(RuntimeException.class);

        underTest.process(deposit, null, mapping);

        verify(resolver).resolve(any(), any());
        verifyZeroInteractions(mapping);
    }

    @Test
    public void mappingThrowsRuntimeException() throws Exception {
        when(resolver.resolve(any(), any())).thenReturn(SWORD_STATE_INPROGRESS.asUri());
        when(mapping.getStatusMap()).thenThrow(new RuntimeException("Expected"));
        expectedException.expectMessage("Expected");
        expectedException.expect(RuntimeException.class);

        assertNull(underTest.process(deposit, null, mapping));

        verify(resolver).resolve(any(), any());
    }
}