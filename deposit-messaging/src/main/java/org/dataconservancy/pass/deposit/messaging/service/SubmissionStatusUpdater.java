/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.messaging.service;

import java.net.URI;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.SubmissionStatusService;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Recalculates the {@code Submission.submissionStatus} for a collection of Submission URIs.
 * <p>
 * The calculation of {@code Submission.submissionStatus} is handled by the {@link SubmissionStatusService}.  Any
 * {@code Submission} with a {@code submissionStatus} that is <em>not </em> {@code COMPLETE} or {@code CANCELLED} make
 * up the collection of Submission URIs to be processed.
 * </p>
 * <p>
 * The criteria for determining which Submissions need to have their {@code submissionStatus} updated is hard-coded and
 * limited in part by the {@link PassClient}.  An alternate approach would be to configure a resource with a query in
 * the ElasticSearch DSL, and communicate directly with the ElasticSearch Query API.  This allows more surgical
 * retrieval of candidate Submissions, and externalizes the query from compiled code.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SubmissionStatusUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionStatusUpdater.class);

    private SubmissionStatusService statusService;

    private PassClient passClient;

    private CriticalRepositoryInteraction cri;

    @Autowired
    public SubmissionStatusUpdater(SubmissionStatusService statusService, PassClient passClient,
                                   CriticalRepositoryInteraction cri) {
        this.statusService = statusService;
        this.passClient = passClient;
        this.cri = cri;
    }

    /**
     * Determines the Submissions to be updated, and updates the status of each in turn.
     */
    public void doUpdate() {
        doUpdate(toUpdate(passClient));
    }

    /**
     * Accepts a collection of Submissions to be updated, and updates the status of each in turn.
     *
     * @param submissionUris a collection of Submission URIs to be updated
     */
    public void doUpdate(Collection<URI> submissionUris) {
        if (submissionUris == null || submissionUris.size() == 0) {
            LOG.trace("No submissions to update.");
            return;
        } else {
            LOG.trace("Updating the Submission.submissionStatus of {} Submission{}", submissionUris.size(),
                      submissionUris.size() > 1 ? "s" : "");
        }

        submissionUris.forEach(uri -> {
            try {
                LOG.trace("Updating Submission.submissionStatus for {}", uri);
                cri.performCritical(uri, Submission.class, CriFunc.preCondition, CriFunc.postCondition,
                                    CriFunc.critical(statusService));
            } catch (Exception e) {
                LOG.warn("Unable to update the 'submissionStatus' of {}", uri, e);
            }
        });
    }

    /**
     * Returns all Submissions that have any Submission.SubmissionStatus <em>except</em> SubmissionStatus.COMPLETE or
     * SubmissionStatus.CANCELLED.
     *
     * @param passClient the client used to communicate with the index
     * @return the URIs of Submissions that may need their SubmissionStatus updated
     */
    static Collection<URI> toUpdate(PassClient passClient) {
        return Stream.of(Submission.SubmissionStatus.values())
                     .filter(status -> status != Submission.SubmissionStatus.COMPLETE)
                     .filter(status -> status != Submission.SubmissionStatus.CANCELLED)
                     .map(status -> status.name().toLowerCase())
                     .map(status -> passClient.findAllByAttribute(Submission.class, "submissionStatus", status))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    /**
     * Convenience class encapsulating the pre- and post-conditions for executing the critical function over the
     * Submission.
     */
    static class CriFunc {

        /**
         * Verifies the expected state of the Submission before updating the Submission.submissionStatus:
         * <ul>
         *     <li>Submission.submitted must be 'true'</li>
         *     <li>Submission.submissionStatus must not be 'COMPLETE'</li>
         *     <li>Submission.submissionStatus must not be 'CANCELLED'</li>
         * </ul>
         */
        static Predicate<Submission> preCondition = (submission) ->
                submission.getSubmissionStatus() != null &&
                submission.getSubmissionStatus() != Submission.SubmissionStatus.COMPLETE &&
                submission.getSubmissionStatus() != Submission.SubmissionStatus.CANCELLED &&
                Boolean.TRUE == submission.getSubmitted();

        /**
         * Verifies the expected state of the Submission after updating Submission.submissionStatus:
         * <ul>
         *     <li>Submission.submissionStatus must not be 'null'</li>
         *     <li>Submission.submitted must be 'true'</li>
         * </ul>
         */
        static Predicate<Submission> postCondition = (submission -> submission.getSubmissionStatus() != null &&
                                                                    Boolean.TRUE == submission.getSubmitted());

        /**
         * Critical section calculates the Submission.submissionStatus, which may or may not be different from the
         * initial Submission.submissionStatus.
         */
        static Function<Submission, Submission> critical(SubmissionStatusService statusService) {
            return (submission -> {
                submission.setSubmissionStatus(statusService.calculateSubmissionStatus(submission));
                return submission;
            });
        }
    }

}
