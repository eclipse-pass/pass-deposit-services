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

package org.dataconservancy.pass.deposit.messaging.config.repository;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepositoryConfig {

    private String id;

    @JsonProperty("status-mapping")
    private StatusMapping statusMapping;

    @JsonProperty("transport-config")
    private TransportConfig transportConfig;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public StatusMapping getStatusMapping() {
        return statusMapping;
    }

    public void setStatusMapping(StatusMapping statusMapping) {
        this.statusMapping = statusMapping;
    }

    public TransportConfig getTransportConfig() {
        return transportConfig;
    }

    public void setTransportConfig(TransportConfig transportConfig) {
        this.transportConfig = transportConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RepositoryConfig that = (RepositoryConfig) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (statusMapping != null ? !statusMapping.equals(that.statusMapping) : that.statusMapping != null)
            return false;
        return transportConfig != null ? transportConfig.equals(that.transportConfig) : that.transportConfig == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (statusMapping != null ? statusMapping.hashCode() : 0);
        result = 31 * result + (transportConfig != null ? transportConfig.hashCode() : 0);
        return result;
    }
}
