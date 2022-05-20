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
package org.dataconservancy.pass.deposit.messaging.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.dataconservancy.pass.deposit.messaging.status.StatusEvaluator;
import org.dataconservancy.pass.model.Deposit;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class TerminalDepositStatusPolicyTest {

    private StatusEvaluator<Deposit.DepositStatus> evaluator;

    private TerminalDepositStatusPolicy underTest;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        evaluator = mock(StatusEvaluator.class);
        underTest = new TerminalDepositStatusPolicy(evaluator);
    }

    @Test
    public void testNullStatus() throws Exception {
        assertFalse(underTest.test(null));
        verifyZeroInteractions(evaluator);
    }

    @Test
    public void testTerminalStatus() throws Exception {
        Deposit.DepositStatus terminal = Deposit.DepositStatus.ACCEPTED;
        when(evaluator.isTerminal(terminal)).thenReturn(true);

        assertTrue(underTest.test(terminal));
        verify(evaluator).isTerminal(terminal);
    }

    @Test
    public void testIntermediateStatus() throws Exception {
        Deposit.DepositStatus terminal = Deposit.DepositStatus.SUBMITTED;
        when(evaluator.isTerminal(terminal)).thenReturn(false);

        assertFalse(underTest.test(terminal));
        verify(evaluator).isTerminal(terminal);
    }

}