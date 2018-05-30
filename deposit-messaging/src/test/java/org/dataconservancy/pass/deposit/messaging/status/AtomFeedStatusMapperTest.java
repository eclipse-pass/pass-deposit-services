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
import org.junit.Before;
import org.junit.Test;

import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomTestUtil.parseFeed;
import static org.junit.Assert.assertEquals;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomFeedStatusMapperTest extends AbstractStatusMapperTest {

    private AtomFeedStatusMapper underTest;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        underTest = new AtomFeedStatusMapper(mapping);
    }

    @Test
    public void testWildCardMapping() throws Exception {
        Deposit.DepositStatus expectedMapping = Deposit.DepositStatus.SUBMITTED;
        assertEquals(expectedMapping, underTest.map(parseFeed("AtomStatusParser-inprogress.xml")));
        assertEquals(expectedMapping, underTest.map(parseFeed("AtomStatusParser-inreview.xml")));
    }

    @Test
    public void testKnownAcceptedTerminalMapping() throws Exception {
        Deposit.DepositStatus expectedMapping = Deposit.DepositStatus.ACCEPTED;
        assertEquals(expectedMapping, underTest.map(parseFeed("AtomStatusParser-archived.xml")));
    }

    @Test
    public void testKnownRejectedTerminalMapping() throws Exception {
        Deposit.DepositStatus expectedMapping = Deposit.DepositStatus.REJECTED;
        assertEquals(expectedMapping, underTest.map(parseFeed("AtomStatusParser-withdrawn.xml")));
    }

    @Test
    public void testKnownWildcardIntermediateMapping() throws Exception {
        Deposit.DepositStatus expectedMapping = Deposit.DepositStatus.SUBMITTED;
        assertEquals(expectedMapping, underTest.map(parseFeed("AtomStatusParser-inprogress.xml")));
        assertEquals(expectedMapping, underTest.map(parseFeed("AtomStatusParser-inreview.xml")));
    }

}