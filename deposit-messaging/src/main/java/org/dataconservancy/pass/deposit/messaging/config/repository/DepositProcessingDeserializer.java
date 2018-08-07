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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositProcessingDeserializer extends StdDeserializer<DepositProcessing> {

    private static Logger LOG = LoggerFactory.getLogger(DepositProcessingDeserializer.class);

    private static final String BEAN_NAME = "beanName";

    private ApplicationContext appCtx;

    public DepositProcessingDeserializer() {
        this(null);
    }

    protected DepositProcessingDeserializer(Class<?> vc) {
        super(vc);
    }

    public ApplicationContext getAppCtx() {
        return appCtx;
    }

    public void setAppCtx(ApplicationContext appCtx) {
        if (appCtx == null) {
            throw new IllegalArgumentException("Supplied ApplicationContext must not be null.");
        }
        this.appCtx = appCtx;
    }

    @Override
    public DepositProcessing deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        if (this.appCtx == null) {
            throw new IllegalStateException("ApplicationContext must not be null.  Was setAppCtx(ApplicationContext) " +
                    "called with a non-null argument?");
        }

        JsonNode node = parser.getCodec().readTree(parser);

        if (!node.has(BEAN_NAME)) {
            LOG.debug("{} is missing a '{}' field, Deposit status references will not be processed for this " +
                    "repository configuration.", DepositProcessing.class.getName(), BEAN_NAME);
            return new DepositProcessing();
        }

        String beanName = node.get(BEAN_NAME).asText();

        if (beanName == null || beanName.trim().length() == 0) {
            LOG.debug("{} has a null or empty '{}' field, Deposit status references will not be processed for this " +
                    "repository configuration.", DepositProcessing.class.getName(), BEAN_NAME);
            return new DepositProcessing();
        }

        DepositStatusProcessor dsp = appCtx.getBean(beanName, DepositStatusProcessor.class);

        if (dsp == null) {
            String msg = String.format("No bean named '%s' implementing '%s' was found.  Double check your bean " +
                    "configuration or your `repositories.json` configuration file.",
                    beanName, DepositStatusProcessor.class.getName());
            LOG.error(msg);
            throw new RuntimeException(msg);
        }

        DepositProcessing depositProcessing = new DepositProcessing();
        depositProcessing.setBeanName(beanName);
        depositProcessing.setProcessor(dsp);
        return depositProcessing;
    }
}
