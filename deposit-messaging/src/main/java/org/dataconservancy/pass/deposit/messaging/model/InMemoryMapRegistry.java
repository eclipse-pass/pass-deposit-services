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
package org.dataconservancy.pass.deposit.messaging.model;

import java.util.Collection;
import java.util.Map;

/**
 * {@link Registry} implementation backed by a {@code Map}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class InMemoryMapRegistry<T> implements Registry<T> {

    private Map<String, T> registryMap;

    public InMemoryMapRegistry(Map<String, T> registryMap) {
        this.registryMap = registryMap;
    }

    @Override
    public T get(String key) {
        return registryMap.get(key);
    }

    @Override
    public Collection<T> entries() {
        return registryMap.values();
    }

    public Map<String, T> getRegistryMap() {
        return registryMap;
    }
}
