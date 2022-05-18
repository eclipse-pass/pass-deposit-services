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
package org.dataconservancy.pass.deposit.messaging;

import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomIntermediateAggregatedDepositStatus;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomIntermediateDepositStatus;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomTerminalAggregatedDepositStatus;
import static org.dataconservancy.pass.deposit.messaging.DepositMessagingTestUtil.randomTerminalDepositStatus;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.dataconservancy.pass.deposit.messaging.config.spring.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.policy.Policy;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Tests insuring that the Suppliers created by {@link DepositMessagingTestUtil} are congruent with the concrete
 * {@link Policy} implementations.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@PropertySource("classpath:/application.properties")
@TestPropertySource(properties = "pass.deposit.jobs.disabled=true")
@Import(DepositConfig.class)
@ComponentScan(basePackages = "org.dataconservancy.pass")
public class DepositMessagingTestUtilTest {

    @Autowired
    private Policy<Deposit.DepositStatus> intermediateDepositStatusPolicy;

    @Autowired
    private Policy<Deposit.DepositStatus> terminalDepositStatusPolicy;

    @Autowired
    private Policy<Submission.AggregatedDepositStatus> terminalSubmissionStatusPolicy;

    @Autowired
    private Policy<Submission.AggregatedDepositStatus> intermediateSubmissionStatusPolicy;

    private int tries = 20;

    @Test
    public void terminalAggregatedDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertTrue(terminalSubmissionStatusPolicy.test(randomTerminalAggregatedDepositStatus.get()));
            assertFalse(intermediateSubmissionStatusPolicy.test(randomTerminalAggregatedDepositStatus.get()));
        }
    }

    @Test
    public void intermediateAggregatedDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertFalse(terminalSubmissionStatusPolicy.test(randomIntermediateAggregatedDepositStatus.get()));
            assertTrue(intermediateSubmissionStatusPolicy.test(randomIntermediateAggregatedDepositStatus.get()));
        }
    }

    @Test
    public void intermediateDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertTrue(intermediateDepositStatusPolicy.test(randomIntermediateDepositStatus.get()));
            assertFalse(terminalDepositStatusPolicy.test(randomIntermediateDepositStatus.get()));
        }
    }

    @Test
    public void terminalDepositStatusSupplier() {
        for (int i = 0; i < tries; i++) {
            assertFalse(intermediateDepositStatusPolicy.test(randomTerminalDepositStatus.get()));
            assertTrue(terminalDepositStatusPolicy.test(randomTerminalDepositStatus.get()));
        }
    }
}