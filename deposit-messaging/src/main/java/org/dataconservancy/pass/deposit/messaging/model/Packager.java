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

import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusProcessor;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.messaging.service.DepositTask;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;

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

    private DepositStatusProcessor depositStatusProcessor;

    private RepositoryConfig repositoryConfig;

    public Packager(String name, Assembler assembler, Transport transport, RepositoryConfig repositoryConfig) {
        this(name, assembler, transport, repositoryConfig, null);
    }

    public Packager(String name, Assembler assembler, Transport transport, RepositoryConfig repositoryConfig,
                    DepositStatusProcessor depositStatusProcessor) {
        this.name = name;
        this.assembler = assembler;
        this.transport = transport;
        this.depositStatusProcessor = depositStatusProcessor;
        this.repositoryConfig = repositoryConfig;
    }

    public String getName() {
        return name;
    }

    public Assembler getAssembler() {
        return assembler;
    }

    public Map<String, Object> getAssemblerOptions() {
        LOG.debug(">>>> Packager {}@{} RepositoryConfig: {}", this.getClass().getSimpleName(),
                toHexString(identityHashCode(this)),
                (repositoryConfig != null) ? ">>>> " + repositoryConfig : ">>>> null");

        return repositoryConfig.getAssemblerConfig()
                .getOptions()
                .asOptionsMap();
    }

    public Transport getTransport() {
        return transport;
    }

    public Map<String, String> getConfiguration() {
        return repositoryConfig.getTransportConfig().getProtocolBinding().asPropertiesMap();
    }

    /**
     * The {@link DepositStatusProcessor}, may be {@code null}.
     *
     * @return the {@link DepositStatusProcessor}, may be {@code null}.
     */
    public DepositStatusProcessor getDepositStatusProcessor() {
        return depositStatusProcessor;
    }

}
