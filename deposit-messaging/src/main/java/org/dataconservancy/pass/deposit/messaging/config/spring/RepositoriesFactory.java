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

package org.dataconservancy.pass.deposit.messaging.config.spring;

import java.net.URI;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.messaging.config.repository.Repositories;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

/**
 * Parses the global repository configuration file, and returns the configured {@link Repositories}.
 * <p>
 * The location of the repository configuration file isi identified as a Spring Resource using the {@code
 * pass.deposit.repository.configuration} property key.
 * </p>
 */
public class RepositoriesFactory implements FactoryBean<Repositories> {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoriesFactory.class);

    private Resource repositoryConfigResource;

    private ObjectMapper mapper;

    public RepositoriesFactory(Resource repositoryConfigResource, ObjectMapper mapper) {
        if (repositoryConfigResource == null) {
            throw new IllegalArgumentException("Repository configuration resource must not be null.");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("Jackson ObjectMapper must not be null.");
        }
        this.repositoryConfigResource = repositoryConfigResource;
        this.mapper = mapper;
    }

    /**
     * Parses the {@code repositories.json} configuration file.  Each entry in the file represents the configuration for
     * a is mapped to a {@link RepositoryConfig}
     *
     * @return the configured Repositories object
     * @throws Exception if the object cannot be configured
     */
    @Nullable
    @Override
    public Repositories getObject() throws Exception {
        URI configurationUri = repositoryConfigResource.getURI();
        LOG.trace("Repositories configuration resource: {}", configurationUri);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Configuration dump:\n{}", IOUtils.toString(
                repositoryConfigResource.getInputStream(), "UTF-8"));
        }

        JsonNode configNode = mapper.readTree(repositoryConfigResource.getInputStream());

        Repositories result = new Repositories();

        if (configNode.size() == 0) {
            LOG.warn("Empty Repositories configuration resource, no Repositories will be configured: {}",
                     repositoryConfigResource);
            return result;
        }

        configNode.fields().forEachRemaining(configEntry -> {
            String id = configEntry.getKey();
            JsonNode repositoryConfigNode = configEntry.getValue();
            RepositoryConfig config = null;
            try {
                config = mapper.treeToValue(repositoryConfigNode, RepositoryConfig.class);
            } catch (JsonProcessingException e) {
                LOG.error("Error processing Repository configuration node from {}:\n{}",
                          configurationUri, repositoryConfigNode.asText());
                throw new RuntimeException(e);
            }
            config.setRepositoryKey(id);
            result.addRepositoryConfig(id, config);
        });

        return result;
    }

    @Nullable
    @Override
    public Class<?> getObjectType() {
        return Repositories.class;
    }

}
