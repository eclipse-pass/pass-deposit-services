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

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TransportConfig {

    @JsonProperty("auth-realms")
    private List<AuthRealm> authRealms;

    @JsonProperty("protocol-binding")
    private ProtocolBinding protocolBinding;

    public List<AuthRealm> getAuthRealms() {
        return authRealms;
    }

    public void setAuthRealms(List<AuthRealm> authRealms) {
        this.authRealms = authRealms;
    }

    public ProtocolBinding getProtocolBinding() {
        return protocolBinding;
    }

    public void setProtocolBinding(ProtocolBinding protocolBinding) {
        this.protocolBinding = protocolBinding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransportConfig that = (TransportConfig) o;
        return Objects.equals(authRealms, that.authRealms) &&
               Objects.equals(protocolBinding, that.protocolBinding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authRealms, protocolBinding);
    }

    @Override
    public String toString() {
        return "TransportConfig{" + "authRealms=" + authRealms + ", protocolBinding=" + protocolBinding + '}';
    }

}
