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

import java.net.URI;
import java.util.Map;

import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.config.repository.StatusMapping;
import org.dataconservancy.pass.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the {@link Deposit#getDepositStatusRef() Deposit status reference}, parses the resolved document, and
 * returns a {@link Deposit.DepositStatus}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement">SWORD v2 Profile §11</a>
 */
public class DefaultDepositStatusProcessor implements DepositStatusProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDepositStatusProcessor.class);

    private DepositStatusResolver<URI, URI> statusResolver;

    public DefaultDepositStatusProcessor(DepositStatusResolver<URI, URI> statusResolver) {
        this.statusResolver = statusResolver;
    }

    /**
     * Parses the SWORD statement at {@code depositStatusRef}, and returns a corresponding {@link Deposit.DepositStatus}
     *
     * @param deposit          the Deposit
     * @param repositoryConfig the configuration for the repository containing the Deposit
     * @return the deposit status, may be {@code null}
     */
    @Override
    public Deposit.DepositStatus process(Deposit deposit, RepositoryConfig repositoryConfig) {
        if (deposit.getDepositStatusRef() == null || deposit.getDepositStatusRef().trim().length() == 0) {
            LOG.warn("Deposit {} is missing a depositStatusRef; the deposit status will not be processed.",
                     deposit.getId());
            return null;
        }

        URI swordState = statusResolver.resolve(URI.create(deposit.getDepositStatusRef()), repositoryConfig);

        if (swordState == null) {
            LOG.warn("SWORD state cannot be resolved from SWORD statement {}: no SWORD status was found by " +
                     "DepositStatusResolver impl {}.",
                     deposit.getId(), deposit.getDepositStatusRef(), statusResolver.getClass().getSimpleName());
            return null;
        }

        String status = null;
        try {
            StatusMapping statusMapping = repositoryConfig.getRepositoryDepositConfig().getStatusMapping();
            Map<String, String> statusMap = statusMapping.getStatusMap();
            status = statusMap.getOrDefault(swordState.toString(), statusMapping.getDefaultMapping());
        } catch (RuntimeException e) {
            LOG.error("SWORD state '{}' for {} cannot be mapped to a Deposit.DepositStatus from SWORD statement {}: {}",
                      swordState, deposit.getId(), deposit.getDepositStatusRef(), e.getMessage());
            throw e;
        }

        if (status == null) {
            LOG.warn("Error mapping the SWORD state {} (parsed from the SWORD statement {}) to a " +
                     "Deposit.DepositStatus; returning 'null'.", swordState, deposit.getDepositStatusRef());
            return null;
        }

        try {
            return Deposit.DepositStatus.of(status);
        } catch (IllegalArgumentException e) {
            LOG.error("Status mapper returned an invalid Deposit.DepositStatus uri: '{}'", status);
            throw e;
        }
    }

}
