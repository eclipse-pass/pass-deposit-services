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

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.PassEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Abstraction that executions some function over a set of repository resources.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class PassEntityProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(PassEntityProcessor.class);

    private PassClient passClient;

    public PassEntityProcessor(PassClient passClient) {
        this.passClient = passClient;
    }

    /**
     * Applies the supplied {@code Consumer} to each {@code PassEntity} represented in the collection of {@code uris}.
     * Each {@code uri} in the collection must resolve to the same type of {@code PassEntity} expressed by {@code
     * beingUpdated}.
     *
     * @param uris identify {@code PassEntity}s that should be updated by {@code updater}
     * @param updater the {@code Consumer} that is applying the update to the {@code PassEntity}
     * @param beingUpdated the {@code Class} identifying the concrete type of {@code PassEntity}
     * @param <T> the {@code PassEntity} type
     */
    public <T extends PassEntity> void update(Collection<URI> uris, Consumer<T> updater, Class<T> beingUpdated) {
        uris.forEach(uri -> {
            LOG.debug(">>>> Updating {} (a {}) with {}", uri, beingUpdated.getTypeName(), updater);
            try {
                T t = passClient.readResource(uri, beingUpdated);
                updater.accept(t);
            } catch (Exception e) {
                LOG.warn("Unable to update resource {}: {}", uri, e.getMessage(), e);
            }
        });
    }

}
