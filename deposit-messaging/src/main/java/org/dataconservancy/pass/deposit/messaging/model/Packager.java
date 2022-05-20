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

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;

import java.util.HashMap;
import java.util.Map;

import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.messaging.config.repository.AssemblerOptions;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.service.DepositTask;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusProcessor;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a package {@link Assembler} and repository {@link Transport}, along with {@code Map} carrying configuration
 * properties for both.
 * <p> An instance of {@code Packager} is required for assembling deposit packages from a {@link Submission}.  Each
 * {@link Repository} associated with the {@code Submission} will have an associated {@code Packager} used by the {@link
 * DepositTask} for assembing packages and performing deposits.  The configuration {@code Registry} is used to
 * configure both the assembler and transport.
 * </p>
 * *
 *
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

    /**
     * Returns the options of associated with the Assembler, including the Assembler specification.
     *
     * <p><strong>Example assembler configuration</strong></p>
     * <pre>
     * "assembler": {
     *       "specification": "http://purl.org/net/sword/package/METSDSpaceSIP",
     *       "beanName": "dspaceMetsAssembler",
     *       "options": {
     *         "archive": "ZIP",
     *         "compression": "NONE",
     *         "algorithms": [
     *           "sha512",
     *           "md5"
     *         ]
     *       }
     *     }
     * </pre>
     * This method will return each key in {@code options}, <em>and</em> include {@code specification} as well. Keys in
     * the returned {@code Map} are according to {@link PackageOptions}.
     *
     * @return the Assembler options, including the specification
     */
    public Map<String, Object> getAssemblerOptions() {
        LOG.debug("Packager {}@{} RepositoryConfig: {}", this.getClass().getSimpleName(),
                  toHexString(identityHashCode(this)),
                  (repositoryConfig != null) ? repositoryConfig : "null RepositoryConfig");

        AssemblerOptions assemblerOptions = repositoryConfig.getAssemblerConfig().getOptions();
        if (assemblerOptions == null || assemblerOptions.asOptionsMap() == null ||
            assemblerOptions.asOptionsMap().isEmpty()) {
            LOG.warn("The assembler {} associated with the packager {} does not have any configured options.  " +
                     "This may result in assembly failure or corrupt packages.", assembler.getClass().getName(), name);
        }

        Map<String, Object> optionsMap = (assemblerOptions == null || assemblerOptions.asOptionsMap() == null ||
                                          assemblerOptions.asOptionsMap().isEmpty()) ?
                                         new HashMap<>() : assemblerOptions.asOptionsMap();

        // Include the package specification in the options map
        optionsMap.putIfAbsent(PackageOptions.Spec.KEY, repositoryConfig.getAssemblerConfig().getSpec());

        return optionsMap;
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
