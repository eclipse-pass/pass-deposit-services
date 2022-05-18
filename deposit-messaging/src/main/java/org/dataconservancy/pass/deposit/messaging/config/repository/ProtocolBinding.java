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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "protocol")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FtpBinding.class, name = FtpBinding.PROTO),
    @JsonSubTypes.Type(value = SwordV2Binding.class, name = SwordV2Binding.PROTO),
    @JsonSubTypes.Type(value = FilesystemBinding.class, name = FilesystemBinding.PROTO)
})
public abstract class ProtocolBinding {

    private String protocol;

    @JsonProperty("server-fqdn")
    private String serverFqdn;

    @JsonProperty("server-port")
    private String serverPort;

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getServerFqdn() {
        return serverFqdn;
    }

    public void setServerFqdn(String serverFqdn) {
        this.serverFqdn = serverFqdn;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public abstract Map<String, String> asPropertiesMap();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProtocolBinding that = (ProtocolBinding) o;

        if (protocol != null ? !protocol.equals(that.protocol) : that.protocol != null) {
            return false;
        }
        if (serverFqdn != null ? !serverFqdn.equals(that.serverFqdn) : that.serverFqdn != null) {
            return false;
        }
        return serverPort != null ? serverPort.equals(that.serverPort) : that.serverPort == null;
    }

    @Override
    public int hashCode() {
        int result = protocol != null ? protocol.hashCode() : 0;
        result = 31 * result + (serverFqdn != null ? serverFqdn.hashCode() : 0);
        result = 31 * result + (serverPort != null ? serverPort.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ProtocolBinding{" + "protocol='" + protocol + '\'' + ", serverFqdn='" + serverFqdn + '\'' + ", " +
               "serverPort='" + serverPort + '\'' + '}';
    }
}
