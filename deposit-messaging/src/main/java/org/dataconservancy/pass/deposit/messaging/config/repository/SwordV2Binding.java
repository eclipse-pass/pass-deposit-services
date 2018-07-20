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

import java.util.Objects;

public class SwordV2Binding extends ProtocolBinding {

    static final String PROTO = "SWORDv2";

    private String username;

    private String password;

    @JsonProperty("service-doc")
    private String serviceDocUrl;

    @JsonProperty("default-collection")
    private String defaultCollectionUrl;

    @JsonProperty("on-behalf-of")
    private String onBehalfOf;

    @JsonProperty("deposit-receipt")
    private boolean depositReceipt;

    @JsonProperty("user-agent")
    private String userAgent;

    public SwordV2Binding() {
        this.setProtocol(PROTO);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getServiceDocUrl() {
        return serviceDocUrl;
    }

    public void setServiceDocUrl(String serviceDocUrl) {
        this.serviceDocUrl = serviceDocUrl;
    }

    public String getDefaultCollectionUrl() {
        return defaultCollectionUrl;
    }

    public void setDefaultCollectionUrl(String defaultCollectionUrl) {
        this.defaultCollectionUrl = defaultCollectionUrl;
    }

    public String getOnBehalfOf() {
        return onBehalfOf;
    }

    public void setOnBehalfOf(String onBehalfOf) {
        this.onBehalfOf = onBehalfOf;
    }

    public boolean isDepositReceipt() {
        return depositReceipt;
    }

    public void setDepositReceipt(boolean depositReceipt) {
        this.depositReceipt = depositReceipt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SwordV2Binding that = (SwordV2Binding) o;
        return depositReceipt == that.depositReceipt &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(serviceDocUrl, that.serviceDocUrl) &&
                Objects.equals(defaultCollectionUrl, that.defaultCollectionUrl) &&
                Objects.equals(onBehalfOf, that.onBehalfOf) &&
                Objects.equals(userAgent, that.userAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), username, password, serviceDocUrl, defaultCollectionUrl, onBehalfOf, depositReceipt, userAgent);
    }

}
