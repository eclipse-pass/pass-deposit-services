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

import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_AUTHMODE;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_PORT;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_USERNAME;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints;

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

    @JsonProperty("collection-hints")
    private Map<String, String> collectionHints;

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

    public Map<String, String> getCollectionHints() {
        return collectionHints;
    }

    public void setCollectionHints(Map<String, String> collectionHints) {
        this.collectionHints = collectionHints;
    }

    /**
     * Encodes the map of hints to SWORD collection urls as a string (to maintain compatibility with {@link
     * #asPropertiesMap()}).  Note that any spaces in the urls *must* be properly encoded in the configuration file.
     *
     * @param collectionHints the map of collection hints
     * @return a string encoding of those hints
     */
    String hintsToPropertyString(Map<String, String> collectionHints) {
        if (collectionHints == null) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(Sword2TransportHints.HINT_TUPLE_SEPARATOR);
        joiner.setEmptyValue("");

        collectionHints.forEach((hint, url) -> {
            joiner.add(hint.trim() + Sword2TransportHints.HINT_URL_SEPARATOR + url.trim());
        });

        return joiner.toString();
    }

    @Override
    public Map<String, String> asPropertiesMap() {
        Map<String, String> transportProperties = new HashMap<>();

        transportProperties.put(TRANSPORT_USERNAME, getUsername());
        transportProperties.put(TRANSPORT_PASSWORD, getPassword());
        transportProperties.put(TRANSPORT_AUTHMODE, Transport.AUTHMODE.userpass.name());
        transportProperties.put(TRANSPORT_PROTOCOL, Transport.PROTOCOL.SWORDv2.name());
        transportProperties.put(TRANSPORT_SERVER_FQDN, getServerFqdn());
        transportProperties.put(TRANSPORT_SERVER_PORT, getServerPort());
        transportProperties.put(Sword2TransportHints.SWORD_SERVICE_DOC_URL, getServiceDocUrl());
        transportProperties.put(Sword2TransportHints.SWORD_COLLECTION_URL, getDefaultCollectionUrl());
        transportProperties.put(Sword2TransportHints.SWORD_ON_BEHALF_OF_USER, getOnBehalfOf());
        transportProperties.put(Sword2TransportHints.SWORD_DEPOSIT_RECEIPT_FLAG, String.valueOf(isDepositReceipt()));
        transportProperties.put(Sword2TransportHints.SWORD_CLIENT_USER_AGENT, getUserAgent());

        String collectionHints = hintsToPropertyString(this.collectionHints);
        if (collectionHints != null && collectionHints.length() > 0) {
            transportProperties.put(Sword2TransportHints.SWORD_COLLECTION_HINTS, collectionHints);
        }

        return transportProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SwordV2Binding that = (SwordV2Binding) o;
        return depositReceipt == that.depositReceipt && Objects.equals(username, that.username) && Objects.equals(
            password, that.password) && Objects.equals(serviceDocUrl, that.serviceDocUrl) && Objects.equals(
            defaultCollectionUrl, that.defaultCollectionUrl) && Objects.equals(onBehalfOf,
                                                                               that.onBehalfOf) && Objects.equals(
            userAgent, that.userAgent) && Objects.equals(collectionHints, that.collectionHints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), username, password, serviceDocUrl, defaultCollectionUrl, onBehalfOf,
                            depositReceipt, userAgent, collectionHints);
    }

    @Override
    public String toString() {
        return new StringJoiner("\n  ", SwordV2Binding.class.getSimpleName() + "[", "]").add(
            "username='" + username + "'").add("password='" + password + "'").add(
            "serviceDocUrl='" + serviceDocUrl + "'").add("defaultCollectionUrl='" + defaultCollectionUrl + "'").add(
            "onBehalfOf='" + onBehalfOf + "'").add("depositReceipt=" + depositReceipt).add(
            "userAgent='" + userAgent + "'").add("collectionHints=" + collectionHints).toString();
    }
}
