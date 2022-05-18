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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusProcessor;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositProcessing {

    private String beanName;

    @JsonIgnore
    private DepositStatusProcessor processor;

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public DepositStatusProcessor getProcessor() {
        return processor;
    }

    public void setProcessor(DepositStatusProcessor processor) {
        this.processor = processor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DepositProcessing that = (DepositProcessing) o;

        return beanName != null ? beanName.equals(that.beanName) : that.beanName == null;
    }

    @Override
    public int hashCode() {
        return beanName != null ? beanName.hashCode() : 0;
    }
}
