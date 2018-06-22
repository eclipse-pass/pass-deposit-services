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

import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AbderaDepositStatusRefProcessorTest {

    private AbderaDepositStatusRefProcessor underTest;

    private URI atomStatementUri = URI.create("http://example.org/statement.atom");

    private DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser;

    private DepositStatusMapper<SwordDspaceDepositStatus> swordDepositStatusMapper;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        atomStatusParser = mock(DepositStatusParser.class);
        swordDepositStatusMapper = mock(DepositStatusMapper.class);
        underTest = new AbderaDepositStatusRefProcessor(atomStatusParser, swordDepositStatusMapper);
    }

    @Test
    public void parsingReturnsNullSwordStatus() throws Exception {
        when(atomStatusParser.parse(any())).thenReturn(null);

        assertNull(underTest.process(atomStatementUri));

        verify(atomStatusParser).parse(any());
        verifyZeroInteractions(swordDepositStatusMapper);
    }

    @Test
    public void mappingReturnsNullDepositStatus() throws Exception {
        when(atomStatusParser.parse(any())).thenReturn(SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS);
        when(swordDepositStatusMapper.map(SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS)).thenReturn(null);

        assertNull(underTest.process(atomStatementUri));

        verify(atomStatusParser).parse(any());
        verify(swordDepositStatusMapper).map(SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS);
    }

    @Test(expected = RuntimeException.class)
    public void parsingThrowsRuntimeException() throws Exception {
        when(atomStatusParser.parse(any())).thenThrow(new RuntimeException("Expected"));

        underTest.process(atomStatementUri);

        verify(atomStatusParser).parse(any());
        verifyZeroInteractions(swordDepositStatusMapper);
    }

    @Test(expected = RuntimeException.class)
    public void mappingThrowsRuntimeException() throws Exception {
        when(atomStatusParser.parse(any())).thenReturn(SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS);
        when(swordDepositStatusMapper.map(SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS))
                .thenThrow(new RuntimeException("Expected"));

        assertNull(underTest.process(atomStatementUri));

        verify(atomStatusParser).parse(any());
        verify(swordDepositStatusMapper).map(SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS);
    }
}