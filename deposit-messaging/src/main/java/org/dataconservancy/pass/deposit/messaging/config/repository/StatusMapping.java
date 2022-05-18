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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StatusMapping {

    @JsonProperty("default-mapping")
    private String defaultMapping;

    private Map<String, String> statusMap = new HashMap<>();

    public String getDefaultMapping() {
        return defaultMapping;
    }

    public void setDefaultMapping(String defaultMapping) {
        this.defaultMapping = defaultMapping;
    }

    @JsonAnyGetter
    public Map<String, String> getStatusMap() {
        return statusMap;
    }

    @JsonAnySetter
    public void addStatusEntry(String domainStatus, String passStatus) {
        statusMap.put(domainStatus, passStatus);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StatusMapping that = (StatusMapping) o;
        return Objects.equals(defaultMapping, that.defaultMapping) &&
               Objects.equals(statusMap, that.statusMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultMapping, statusMap);
    }

    @Override
    public String toString() {
        return "StatusMapping{" + "defaultMapping='" + defaultMapping + '\'' + ", statusMap=" + statusMap + '}';
    }
}
