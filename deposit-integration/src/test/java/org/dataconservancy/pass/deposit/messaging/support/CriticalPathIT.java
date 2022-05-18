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
package org.dataconservancy.pass.deposit.messaging.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.config.spring.DrainQueueConfig;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.support.messaging.cri.CriticalPath;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"spring.jms.listener.auto-startup=false"})
@Import(DrainQueueConfig.class)
@DirtiesContext
public class CriticalPathIT {

    @Autowired
    private CriticalPath criticalPath;

    @Autowired
    private PassClient passClient;

    @Test
    public void simpleTest() throws Exception {
        // create a resource, put it in the repo
        Deposit deposit = new Deposit();
        deposit = passClient.readResource(passClient.createResource(deposit), Deposit.class);

        // simply use critical path to update its deposit status

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> result = criticalPath.performCritical(
            deposit.getId(), Deposit.class, (d) -> d.getDepositStatus() == null,
            (d) -> d.getDepositStatus() == Deposit.DepositStatus.SUBMITTED, (d) -> {
                d.setDepositStatus(Deposit.DepositStatus.SUBMITTED);
                return d;
            });

        assertNotNull(result);
        assertTrue(result.success());
        assertNotNull(result.resource());
        assertEquals(Deposit.DepositStatus.SUBMITTED, result.resource().get().getDepositStatus());
        assertNotSame(deposit, result.resource());
    }

    @Test
    public void endStateFailure() throws Exception {
        Boolean[] probe = {Boolean.FALSE};

        // create a resource, put it in the repo
        Deposit deposit = new Deposit();
        deposit = passClient.readResource(passClient.createResource(deposit), Deposit.class);

        // simply use critical path to update its deposit status

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> result = criticalPath.performCritical(
            deposit.getId(), Deposit.class, (d) -> d.getDepositStatus() == null, (d) -> {
                probe[0] = Boolean.TRUE;
                return d.getDepositStatus() == Deposit.DepositStatus.REJECTED;
            }, (d) -> {
                d.setDepositStatus(Deposit.DepositStatus.SUBMITTED);
                return d;
            });

        assertNotNull(result);
        assertFalse(result.success());
        assertNotNull(result.resource());
        assertEquals(Deposit.DepositStatus.SUBMITTED, result.resource().get().getDepositStatus());
        assertNotSame(deposit, result.resource());
        assertTrue(probe[0]);
    }

    @Test
    public void initialStateFailure() throws Exception {
        Boolean[] probe = {Boolean.FALSE};

        // create a resource, put it in the repo
        Deposit deposit = new Deposit();
        deposit = passClient.readResource(passClient.createResource(deposit), Deposit.class);

        // execute serial updates, the second one should fail because the initial state condition
        // (deposit status == null) is not met

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> first = criticalPath.performCritical(
            deposit.getId(), Deposit.class, (d) -> d.getDepositStatus() == null,
            (d) -> d.getDepositStatus() == Deposit.DepositStatus.SUBMITTED, (d) -> {

                d.setDepositStatus(Deposit.DepositStatus.SUBMITTED);
                return d;
            });

        CriticalRepositoryInteraction.CriticalResult<Deposit, Deposit> second = criticalPath.performCritical(
            deposit.getId(),
            Deposit.class, (d) -> d.getDepositStatus() == null,
            (d) -> d.getDepositStatus() == Deposit.DepositStatus.REJECTED, (d) -> {
                probe[0] = Boolean.TRUE;
                d.setDepositStatus(Deposit.DepositStatus.REJECTED);
                return d;
            });

        assertTrue(first.success());
        assertFalse(second.success());
        assertFalse(probe[0]);
        assertEquals(Deposit.DepositStatus.SUBMITTED, first.resource().get().getDepositStatus());
        assertEquals(Deposit.DepositStatus.SUBMITTED, second.resource().get().getDepositStatus());
    }

    @Test
    public void interleavedFailure() throws Exception {

    }
}
