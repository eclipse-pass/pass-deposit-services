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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AssemblerConfig {

    @JsonProperty("specification")
    private String spec;

    private AssemblerOptions options;

    private String beanName;

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public AssemblerOptions getOptions() {
        return options;
    }

    public void setOptions(AssemblerOptions options) {
        this.options = options;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AssemblerConfig that = (AssemblerConfig) o;
        return Objects.equals(spec, that.spec) && Objects.equals(options, that.options) &&
                Objects.equals(beanName, that.beanName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spec, options, beanName);
    }

    @Override
    public String toString() {
        return "AssemblerConfig{" + "spec='" + spec + '\'' + ", options=" + options +
               ", beanName='" + beanName + '\'' + '}';
    }

}
