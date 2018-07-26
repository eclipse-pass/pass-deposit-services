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

/**
 * Contains parameters and configuration for communicating with a downstream repository for the purpose of transferring
 * the custody of materials deposited by a user of PASS.
 * <p>
 * A "repository" may be a DSpace instance, an FTP server, a Fedora repository, or any system capable of receiving a
 * package of materials.
 * </p>
 */
public class RepositoryConfig {

    private String id;

    @JsonProperty("deposit-status")
    private DepositStatus depositStatus;

    @JsonProperty("transport-config")
    private TransportConfig transportConfig;

    @JsonProperty("assembler")
    private AssemblerConfig assemblerConfig;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DepositStatus getDepositStatus() {
        return depositStatus;
    }

    public void setDepositStatus(DepositStatus depositStatus) {
        this.depositStatus = depositStatus;
    }

    public TransportConfig getTransportConfig() {
        return transportConfig;
    }

    public void setTransportConfig(TransportConfig transportConfig) {
        this.transportConfig = transportConfig;
    }

    public AssemblerConfig getAssemblerConfig() {
        return assemblerConfig;
    }

    public void setAssemblerConfig(AssemblerConfig assemblerConfig) {
        this.assemblerConfig = assemblerConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RepositoryConfig that = (RepositoryConfig) o;

        if (id != null ? !id.equals(that.id) : that.id != null)
            return false;
        if (depositStatus != null ? !depositStatus.equals(that.depositStatus) : that.depositStatus != null)
            return false;
        if (transportConfig != null ? !transportConfig.equals(that.transportConfig) : that.transportConfig != null)
            return false;
        return assemblerConfig != null ? assemblerConfig.equals(that.assemblerConfig) : that.assemblerConfig == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (depositStatus != null ? depositStatus.hashCode() : 0);
        result = 31 * result + (transportConfig != null ? transportConfig.hashCode() : 0);
        result = 31 * result + (assemblerConfig != null ? assemblerConfig.hashCode() : 0);
        return result;
    }
}
