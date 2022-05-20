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

import java.net.URI;
import java.util.Collection;
import java.util.Set;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DepositUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(DepositUpdater.class);

    private static final String STATUS_ATTRIBUTE = "depositStatus";

    private PassClient passClient;

    private DepositTaskHelper depositHelper;

    @Autowired
    public DepositUpdater(PassClient passClient, DepositTaskHelper depositHelper) {
        this.passClient = passClient;
        this.depositHelper = depositHelper;
    }

    public void doUpdate() {
        doUpdate(depositUrisToUpdate(passClient));
    }

    void doUpdate(Collection<URI> depositUris) {
        depositUris.forEach(depositUri -> {
            try {
                depositHelper.processDepositStatus(depositUri);
            } catch (Exception e) {
                LOG.warn("Failed to update {}: {}", depositUri, e.getMessage(), e);
            }
        });
    }

    private static Collection<URI> depositUrisToUpdate(PassClient passClient) {
        Set<URI> depositUris = passClient.findAllByAttribute(
            Deposit.class, STATUS_ATTRIBUTE, Deposit.DepositStatus.FAILED.toString());
        depositUris.addAll(passClient.findAllByAttribute(
            Deposit.class, STATUS_ATTRIBUTE, Deposit.DepositStatus.SUBMITTED.toString()));
        return depositUris;
    }

}
