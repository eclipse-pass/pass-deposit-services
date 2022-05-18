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

import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_ARCHIVED;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;

import org.dataconservancy.pass.deposit.messaging.config.repository.BasicAuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryDepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.repository.StatusMapping;
import org.dataconservancy.pass.model.Deposit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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

    private RepositoryConfig repositoryConfig;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        resolver = mock(DepositStatusResolver.class);
        mapping = mock(StatusMapping.class);
        authRealm = mock(BasicAuthRealm.class);
        when(authRealm.getBaseUrl()).thenReturn(depositStatusRefBaseUrl);
        repositoryConfig = mock(RepositoryConfig.class);
        RepositoryDepositConfig depositConfig = mock(RepositoryDepositConfig.class);
        when(repositoryConfig.getRepositoryDepositConfig()).thenReturn(depositConfig);
        when(depositConfig.getStatusMapping()).thenReturn(mapping);

        underTest = new DefaultDepositStatusProcessor(resolver);
        deposit = mock(Deposit.class);
        when(deposit.getDepositStatusRef()).thenReturn(depositStatusRef);
    }

    @Test
    public void processingOk() throws Exception {
        URI refUri = URI.create(depositStatusRef);
        when(resolver.resolve(refUri, repositoryConfig)).thenReturn(SWORD_STATE_ARCHIVED.asUri());
        when(mapping.getStatusMap()).thenReturn(new HashMap<String, String>() {
            {
                put(SWORD_STATE_ARCHIVED.asUri().toString(), Deposit.DepositStatus.ACCEPTED.name().toLowerCase());
            }
        });

        assertEquals(Deposit.DepositStatus.ACCEPTED,
                     underTest.process(deposit, repositoryConfig));

        verify(resolver).resolve(refUri, repositoryConfig);
        verify(mapping).getStatusMap();
    }

    @Test
    public void mappingReturnsUnknownDepositStatus() throws Exception {
        String badDepositStatusUri = "http://foo/uri";
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(badDepositStatusUri);

        URI refUri = URI.create(depositStatusRef);
        when(resolver.resolve(refUri, repositoryConfig)).thenReturn(SWORD_STATE_ARCHIVED.asUri());
        when(mapping.getStatusMap()).thenReturn(new HashMap<String, String>() {
            {

                put(SWORD_STATE_ARCHIVED.asUri().toString(), badDepositStatusUri);
            }
        });

        underTest.process(deposit, repositoryConfig);
        verify(resolver).resolve(refUri, repositoryConfig);
        verify(mapping);
    }

    @Test
    public void parsingReturnsNullSwordStatus() throws Exception {
        when(resolver.resolve(any(), eq(repositoryConfig))).thenReturn(null);

        assertNull(underTest.process(deposit, repositoryConfig));

        verify(resolver).resolve(any(), eq(repositoryConfig));
        verifyZeroInteractions(mapping);
    }

    @Test
    public void mappingReturnsNullDepositStatus() throws Exception {
        when(resolver.resolve(any(), eq(repositoryConfig))).thenReturn(SWORD_STATE_INPROGRESS.asUri());
        when(mapping.getStatusMap()).thenReturn(Collections.emptyMap());
        when(mapping.getDefaultMapping()).thenReturn(null);

        assertNull(underTest.process(deposit, repositoryConfig));

        verify(resolver).resolve(any(), eq(repositoryConfig));
        verify(mapping).getStatusMap();
        verify(mapping).getDefaultMapping();
    }

    @Test
    public void parsingThrowsRuntimeException() throws Exception {
        when(resolver.resolve(any(), eq(repositoryConfig))).thenThrow(new RuntimeException("Expected"));
        expectedException.expectMessage("Expected");
        expectedException.expect(RuntimeException.class);

        underTest.process(deposit, repositoryConfig);

        verify(resolver).resolve(any(), repositoryConfig);
        verifyZeroInteractions(mapping);
    }

    @Test
    public void mappingThrowsRuntimeException() throws Exception {
        when(resolver.resolve(any(), eq(repositoryConfig))).thenReturn(SWORD_STATE_INPROGRESS.asUri());
        when(mapping.getStatusMap()).thenThrow(new RuntimeException("Expected"));
        expectedException.expectMessage("Expected");
        expectedException.expect(RuntimeException.class);

        assertNull(underTest.process(deposit, repositoryConfig));

        verify(resolver).resolve(any(), repositoryConfig);
    }
}