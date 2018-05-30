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
import org.dataconservancy.pass.client.fedora.UpdateConflictException;
import org.dataconservancy.pass.model.PassEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.String.format;

/**
 * Provides the guarantees set by {@link CriticalRepositoryInteraction}, and boilerplate for interacting with, and
 * modifying the state of, repository resources.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class CriticalPath implements CriticalRepositoryInteraction {

    private static final Logger LOG = LoggerFactory.getLogger(CriticalPath.class);

    private PassClient passClient;

    private ConflictHandler conflictHandler;

    public CriticalPath(PassClient passClient, ConflictHandler conflictHandler) {
        this.passClient = passClient;
        this.conflictHandler = conflictHandler;
    }

    /**
     * {@inheritDoc}
     * <h4>Implementation notes</h4>
     * Executes in order:
     * <ol>
     *     <li>Obtain a lock over the interned, string form of the {@code uri}, insuring no interference from other
     *         threads executing in this JVM</li>
     *     <li>Read the {@code PassEntity} identified by {@code uri} from the repository, short-circuiting the
     *         interaction by returning a {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>Apply the pre-condition {@code Predicate}, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if the condition fails</li>
     *     <li>Perform the {@code critical} interaction, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>Update the resource, and re-read it from the repository.  If a {@code ConflictUpdateException} is thrown,
     *         it is supplied to the {@link ConflictHandler} for resolution.  If any other {@code Exception} is thrown,
     *         the interaction is short-circuited, and a {@code CriticalResult} returned.</li>
     *     <li>Apply the post-condition {@code Predicate} and returns {@code CriticalResult}</li>
     * </ol>
     * @param uri the uri of the {@code PassEntity} which is the subject of the {@code critical} pathv
     * @param clazz the concrete {@code Class} of the {@code PassEntity} represented by {@code uri}
     * @param precondition precondition that must evaluate to {@code true} for the {@code critical} path to execute
     * @param postcondition postcondition that must evaluate to {@code true} for the {@code CriticalResult} to be
     *                      considered successful
     * @param critical the critical interaction with the repository, which may return a result of type {@code R}
     * @param <T> the type of {@code PassEntity}
     * @param <R> the type of {@code Object} returned by {@code critical}
     * @return a {@code CriticalResult} encapsulating the {@code PassEntity}, the return from the {@code critical} path,
     *         any exception thrown, and the overall success as determined by the post-condition
     */
    @Override
    public <T extends PassEntity, R> CriticalResult<R, T> performCritical(URI uri, Class<T> clazz,
                                                                          Predicate<T> precondition,
                                                                          Predicate<T> postcondition,
                                                                          Function<T, R> critical) {
        return performCritical(uri, clazz, precondition, (t, r) -> postcondition.test(t), critical);
    }

    /**
     * {@inheritDoc}
     * <h4>Implementation notes</h4>
     * Executes in order:
     * <ol>
     *     <li>Obtain a lock over the interned, string form of the {@code uri}, insuring no interference from other
     *         threads executing in this JVM</li>
     *     <li>Read the {@code PassEntity} identified by {@code uri} from the repository, short-circuiting the
     *         interaction by returning a {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>Apply the pre-condition {@code Predicate}, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if the condition fails</li>
     *     <li>Perform the {@code critical} interaction, short-circuiting the interaction by returning a
     *         {@code CriticalResult} if an {@code Exception} is thrown</li>
     *     <li>Update the resource, and re-read it from the repository.  If a {@code ConflictUpdateException} is thrown,
     *         it is supplied to the {@link ConflictHandler} for resolution.  If any other {@code Exception} is thrown,
     *         the interaction is short-circuited, and a {@code CriticalResult} returned.</li>
     *     <li>Apply the post-condition {@code BiPredicate} and returns {@code CriticalResult}</li>
     * </ol>
     * @param uri the uri of the {@code PassEntity} which is the subject of the {@code critical} pathv
     * @param clazz the concrete {@code Class} of the {@code PassEntity} represented by {@code uri}
     * @param precondition precondition that must evaluate to {@code true} for the {@code critical} path to execute
     * @param postcondition postcondition that must evaluate to {@code true} for the {@code CriticalResult} to be
     *                      considered successful
     * @param critical the critical interaction with the repository, which may return a result of type {@code R}
     * @param <T> the type of {@code PassEntity}
     * @param <R> the type of {@code Object} returned by {@code critical}
     * @return a {@code CriticalResult} encapsulating the {@code PassEntity}, the return from the {@code critical} path,
     *         any exception thrown, and the overall success as determined by the post-condition
     */
    @Override
    public <T extends PassEntity, R> CriticalResult<R, T> performCritical(URI uri, Class<T> clazz,
                                                                          Predicate<T> precondition,
                                                                          BiPredicate<T, R> postcondition,
                                                                          Function<T, R> critical) {

        CriticalResult<R, T> cr = null;

        // 1. Obtain a lock over the repository resource URI, then enter the critical section

        synchronized (uri.toString().intern()) {

            // 2. Read the resource from the repository

            T resource = null;
            try {
                resource = passClient.readResource(uri, clazz);
            } catch (Exception e) {
                return new CriticalResult<>(null, null,false, e);
            }

            // 3. Verify that the state of the resource is what is expected from the caller.  If not, return indicating
            //    failure, with a copy of the resource.

            if (!precondition.test(resource)) {
                LOG.debug(">>>> Precondition for applying the critical path on resource {} failed.", resource.getId());
                return new CriticalResult<>(null, resource, false);
            }

            // 4.  Apply the critical update to the resource.

            R updateResult = null;
            try {
                updateResult = critical.apply(resource);
            } catch (Exception e) {
                return new CriticalResult<>(updateResult, resource,false, e);
            }

            // 5. Attempt to update the resource, knowing that another process may have modified the state of the
            //    resource in the interim.  Any conflicts are handled by the ConflictHandler
            // TODO: update this class to allow the ConflictHandler to be pluggable

            try {
                resource = passClient.updateAndReadResource(resource, (Class<T>)resource.getClass());
            } catch (UpdateConflictException e) {
                try {
                    // If the ConflictHandler is successful, the resource with its updated state is returned
                    // (presumably a merge of the state in the repository with the state from the critical function)
                    resource = conflictHandler.handleConflict(resource, clazz);
                } catch (Exception handlerE) {
                    // If the ConflictHandler fails, wrap the exception with an explanation
                    RuntimeException rte = new RuntimeException(
                            format("Error resolving conflict attempting critical update: %s",
                                    handlerE.getMessage()), e);
                    return new CriticalResult<>(updateResult, resource, false, rte);
                }
            } catch (Exception e) {
                return new CriticalResult<>(updateResult, resource, false, e);
            }

            // 6. Verify the expected end state, and create the result.  Note that the success or failure of a
            //    critical path rests entirely on the verification of this final state: the caller wants to know:
            //    "Did the update I perform result in the state I expected?"

            if (!postcondition.test(resource, updateResult)) {
                LOG.debug(">>>> Postcondition over resource {} and result {} failed.", resource.getId(), updateResult);
                return new CriticalResult<>(updateResult, resource, false);
            }

            cr = new CriticalResult<>(updateResult, resource, true);
        }

        return cr;
    }


}
