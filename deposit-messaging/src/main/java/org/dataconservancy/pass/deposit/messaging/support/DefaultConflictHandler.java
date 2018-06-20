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
package org.dataconservancy.pass.deposit.messaging.support;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.PassEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Resolves {@code 412 Precondition Failed} responses by re-retrieving the latest state of the resource from the
 * repository, insuring the pre-condition for applying the update still holds, and then applying the update.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DefaultConflictHandler implements ConflictHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConflictHandler.class);

    private PassClient passClient;

    public DefaultConflictHandler(PassClient passClient) {
        this.passClient = passClient;
    }

    /**
     * {@inheritDoc}
     * <h4>Implementation notes</h4>
     * Resolves {@code 412 Precondition Failed} responses by re-retrieving the latest state of the resource from the
     * repository, insuring the pre-condition for applying the update still holds, and then applying the update.
     *
     * @param conflictedResource the resource with the state to be updated
     * @param resourceClass the runtime class of the resource
     * @param preCondition the precondition that must be satisfied in order for the {@code criticalUpdate} to be applied
     * @param criticalUpdate the update to be applied to the resource
     * @param <T> {@inheritDoc}
     * @param <R> {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public <T extends PassEntity, R> R handleConflict(T conflictedResource, Class<T> resourceClass, Predicate<T>
            preCondition, Function<T, R> criticalUpdate) {

        LOG.debug(">>>> Retrying update for {}, version {}",
                conflictedResource.getId(), conflictedResource.getVersionTag());

        T toUpdate = null;
        try {
            toUpdate = passClient.readResource(conflictedResource.getId(), resourceClass);
        } catch (Exception e) {
            String msg = String.format("Update retry failed for %s (version %s): Unable to successfully re-read the " +
                            "latest version of the resource when retrying: %s", conflictedResource.getId(),
                    conflictedResource.getVersionTag(), e.getMessage());
            LOG.info(msg, e);
        }

        try {
            if (!preCondition.test(toUpdate)) {
                String msg = String.format("Update retry failed for %s (version %s to %s): does "
                        + "not the satisfy the precondition for update.",
                        conflictedResource.getId(), conflictedResource.getVersionTag(), toUpdate.getVersionTag());
                throw new RuntimeException(msg);
            }
            R toReturn = criticalUpdate.apply(toUpdate);
            passClient.updateResource(toUpdate);
            return toReturn;
        } catch (Exception e) {
            String msg = String.format("Update retry failed for %s (version %s to %s)",
                    conflictedResource.getId(), conflictedResource.getVersionTag(), toUpdate.getVersionTag());
            LOG.info(msg, e);
        }

        return null;
    }
}
