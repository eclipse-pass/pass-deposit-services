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

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.PassEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DefaultConflictHandler implements ConflictHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConflictHandler.class);

    private PassClient passClient;

    public DefaultConflictHandler(PassClient passClient) {
        this.passClient = passClient;
    }

    @Override
    public <T extends PassEntity> T handleConflict(T resource, Class<? extends T> resourceClass) {
        LOG.debug(">>>> Retrying update for {}", resource.getId());

        try {
            passClient.updateResource(resource);
        } catch (Exception e) {
            LOG.warn(">>>> Update retry failed for {}: {}", resource.getId(), e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return passClient.readResource(resource.getId(), resourceClass);
    }

}
