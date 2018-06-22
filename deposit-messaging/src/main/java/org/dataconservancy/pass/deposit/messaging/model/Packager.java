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
package org.dataconservancy.pass.deposit.messaging.model;

import org.dataconservancy.nihms.assembler.Assembler;
import org.dataconservancy.nihms.transport.Transport;
import org.dataconservancy.pass.deposit.messaging.service.DepositStatusRefProcessor;
import org.dataconservancy.pass.deposit.messaging.service.DepositTask;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.stream.Collectors.toMap;

/**
 * Provides a package {@link Assembler} and repository {@link Transport}, along with {@code Map} carrying configuration
 * properties for both.
 * <p> An instance of {@code Packager} is required for assembling deposit packages from a {@link Submission}.  Each
 * {@link Repository} associated with the {@code Submission} will have an associated {@code Packager} used by the {@link
 * DepositTask} for assembing packages and performing deposits.  The configuration {@code Registry} is used to
 * configure both the assembler and transport.
 * </p>
 **
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class Packager {

    private static final Logger LOG = LoggerFactory.getLogger(Packager.class);

    private String name;

    private Assembler assembler;

    private Transport transport;

    private DepositStatusRefProcessor depositStatusProcessor;

    private Map<String, String> configuration;

    public Packager(String name, Assembler assembler, Transport transport, Map<String, String> configuration) {
        this(name, assembler, transport, configuration, null);
    }

    public Packager(String name, Assembler assembler, Transport transport, Map<String, String> configuration,
                    DepositStatusRefProcessor depositStatusProcessor) {
        this.name = name;
        this.assembler = assembler;
        this.transport = transport;
        this.depositStatusProcessor = depositStatusProcessor;
        this.configuration = configuration;
    }

    public String getName() {
        return name;
    }

    public Assembler getAssembler() {
        return assembler;
    }

    public Transport getTransport() {
        return transport;
    }

    public Map<String, String> getConfiguration() {
        return configuration.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .peek(entry -> LOG.trace(">>>> Configuring {}@{} with '{}'='{}'",
                        this.getClass().getSimpleName(),
                        toHexString(identityHashCode(this)),
                        entry.getKey(), entry.getValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The {@link DepositStatusRefProcessor}, may be {@code null}.
     *
     * @return the {@link DepositStatusRefProcessor}, may be {@code null}.
     */
    public DepositStatusRefProcessor getDepositStatusProcessor() {
        return depositStatusProcessor;
    }

}
