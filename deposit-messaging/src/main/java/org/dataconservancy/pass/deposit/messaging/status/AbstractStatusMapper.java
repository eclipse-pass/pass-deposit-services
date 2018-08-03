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
package org.dataconservancy.pass.deposit.messaging.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;

import static org.dataconservancy.pass.model.Deposit.*;

/**
 * Base class for mapping the notion of "status" of different objects to the notion of {@link Deposit#getDepositStatus()
 * Deposit status} as understood by PASS Deposit Services.
 * <p>
 * This class is configured by a external file, located by the {@code pass.deposit.status.mapping} configuration key.
 * The default location is the classpath resource {@code /statusmapping.json}.  This location may be overridden by
 * setting {@code pass.deposit.status.mapping} to another location.  The value should be a valid Spring
 * {@link org.springframework.core.io.Resource} URI.
 * </p>
 * <p>
 * An example configuration is as follows.  The "domain" status - the status to be mapped <em>into</em> the Deposit
 * Services' notion of status is on the left hand side, and the corresponding value in the Deposit Services model is
 * on the right hand side.  The statuses used correspond to enums (c.f.
 * {@link CopyStatus}, {@link SwordDspaceDepositStatus}, {@link DepositStatus}).  In the example below:
 * <ul>
 *     <li>{@link CopyStatus#COMPLETE} will be mapped to {@link DepositStatus#ACCEPTED}</li>
 *     <li>All other statuses of {@code CopyStatus} will be mapped to {@link DepositStatus#SUBMITTED}</li>
 *     <li>{@link SwordDspaceDepositStatus#SWORD_STATE_ARCHIVED} will be mapped to {@link DepositStatus#ACCEPTED}</li>
 *     <li>{@link SwordDspaceDepositStatus#SWORD_STATE_WITHDRAWN} will be mapped to {@link DepositStatus#REJECTED}</li>
 *     <li>All other statuses of {@code SwordDspaceDepositStatus} will be mapped to {@link DepositStatus#SUBMITTED}</li>
 * </ul>
 * </p>
 * <pre>
 * {
 *  "RepositoryCopyv2": {
 *      "COMPLETE": "ACCEPTED",
 *      "*": "SUBMITTED" },
 *
 *  "SWORDv2DspaceStatement": {
 *      "SWORD_STATE_ARCHIVED": "ACCEPTED",
 *      "SWORD_STATE_WITHDRAWN": "REJECTED",
 *      "*": "SUBMITTED" }
 * }
 * </pre>
 * <p>
 * Mappings can be updated at runtime by editing this file.  However, adding a top-level key (i.e. a sibling of
 * {@code RepositoryCopyv2} or {@code SWORDv2DspaceStatement}) will also require implementing an instance of this
 * abstract class that answers to the new key.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractStatusMapper<T> implements DepositStatusMapper<T> {

    /**
     * The key identifying the mapping configuration for this mapper
     */
    public static final String SWORDV2_MAPPING_KEY = "SWORDv2DspaceStatement";
    public static final String REPO_COPY_MAPPING_KEY = "RepositoryCopyv2";
    public static final String AGG_SUBMISSION_STATUS_MAPPING_KEY = "SubmissionAggregatedStatusv2";

    /**
     * The JSON containing the status mapping, as documented above
     */
    protected JsonNode statusMap;

    /**
     * Construct an abstract status mapper that is able to resolve its configuration from the supplied JSON.
     *
     * @param statusMap the JSON representing the status mapping as documented above
     */
    public AbstractStatusMapper(JsonNode statusMap) {
        this.statusMap = statusMap;
    }

    /**
     * Map the notion of status from one domain into the notion of status used by {@link DepositStatus}.
     *
     * @param statusToMap the status from another domain
     * @return the {@link DepositStatus} {@code statusToMap} represents, or {@code null} if no mapping could be made
     */
    public DepositStatus mapInternal(String statusToMap) {
        String configurationKey = getConfigurationKey();
        if (configurationKey == null) {
            throw new IllegalStateException("Configuration key must not be null!");
        }
        JsonNode mapping = statusMap.findValue(configurationKey);
        if (mapping == null) {
            return null;
        }

        if (statusToMap == null) {
            return wildCardMapping(mapping);
        }

        JsonNode mappedValue = mapping.findValue(statusToMap.toUpperCase());
        if (mappedValue != null) {
            return DepositStatus.valueOf(mappedValue.asText());
        }

        return wildCardMapping(mapping);
    }

    /**
     * General implementation that parses the supplied {@code node} for the special key {@code *}, that represents the
     * wildcard mapping.  The {@code node} supplied here must be a subset of the {@link #statusMap} that represents
     * <em>only</em> the mapping for the {@link #getConfigurationKey() key}.
     *
     * @param node the JSON representing the configuration for this {@link #getConfigurationKey() configuration key}
     * @return the status, or {@code null} if a wildcard mapping does not exist.
     */
    protected DepositStatus wildCardMapping(JsonNode node) {
        JsonNode wc = node.findValue("*");
        if (wc == null) {
            return null;
        }

        return DepositStatus.valueOf(wc.textValue().toUpperCase());
    }

    /**
     * The top-level key used to uniquely identify the configuration of this mapper.  Must not be {@code null}.
     *
     * @return the configuration key, must not be {@code null}
     */
    protected abstract String getConfigurationKey();
}
