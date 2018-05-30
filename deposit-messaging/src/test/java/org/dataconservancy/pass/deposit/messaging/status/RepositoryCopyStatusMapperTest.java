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

import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class RepositoryCopyStatusMapperTest extends AbstractStatusMapperTest {

    private RepositoryCopyStatusMapper underTest;

    private RepositoryCopy repoCopy;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        repoCopy = mock(RepositoryCopy.class);
        underTest = new RepositoryCopyStatusMapper(mapping);
    }

    @Test
    public void testNullMapping() throws Exception {
        assertNull(underTest.map(null));
    }

    @Test
    public void testNullCopyStatus() throws Exception {
        when(repoCopy.getCopyStatus()).thenReturn(null);
        assertNull(underTest.map(repoCopy.getCopyStatus()));
    }

    @Test
    public void testKnownAcceptedTerminalMapping() throws Exception {
        Deposit.DepositStatus expectedMapping = Deposit.DepositStatus.ACCEPTED;
        when(repoCopy.getCopyStatus()).thenReturn(RepositoryCopy.CopyStatus.COMPLETE);
        assertEquals(expectedMapping, underTest.map(repoCopy.getCopyStatus()));
    }

    @Test
    public void testKnownWildcardIntermediateMapping() throws Exception {
        Deposit.DepositStatus expectedMapping = Deposit.DepositStatus.SUBMITTED;
        when(repoCopy.getCopyStatus())
                .thenReturn(RepositoryCopy.CopyStatus.ACCEPTED)
                .thenReturn(RepositoryCopy.CopyStatus.IN_PROGRESS);
        assertEquals(expectedMapping, underTest.map(repoCopy.getCopyStatus()));
        assertEquals(expectedMapping, underTest.map(repoCopy.getCopyStatus()));
    }

}