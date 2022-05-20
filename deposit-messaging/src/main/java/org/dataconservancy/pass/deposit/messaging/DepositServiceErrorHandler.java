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
package org.dataconservancy.pass.deposit.messaging;

import org.dataconservancy.pass.deposit.messaging.service.DepositUtil;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

/**
 * Updates the status of {@code Deposit} or {@code Submission} resources as logically failed.
 * <p>
 * When a {@link DepositServiceRuntimeException} is handled by this class, the type of the {@link
 * DepositServiceRuntimeException#getResource() associated resource} is examined.  If the resource is a {@code Deposit}
 * or {@code Submission}, an attempt is made to contact the repository and mark the resource as failed.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DepositServiceErrorHandler implements ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DepositServiceErrorHandler.class);

    private CriticalRepositoryInteraction cri;

    public DepositServiceErrorHandler(CriticalRepositoryInteraction cri) {
        this.cri = cri;
    }

    @Override
    public void handleError(Throwable t) {
        DepositServiceRuntimeException dsException = null;

        // If the supplied Throwable isn't an instance of DepositServiceRuntimeException, check to see if the cause is
        // TODO: should we iterate over every cause looking for a DepositServiceRuntimeException?
        if (!(t instanceof DepositServiceRuntimeException)) {
            Throwable cause = t.getCause();
            if (!(cause instanceof DepositServiceRuntimeException)) {
                LOG.error("Unrecoverable error: {}", t.getMessage(), t);
                return;
            }
            dsException = (DepositServiceRuntimeException) cause;
        } else {
            dsException = (DepositServiceRuntimeException) t;
        }

        if (dsException.getResource() != null) {
            if (dsException.getResource().getClass() == Deposit.class) {
                LOG.error("Unrecoverable error, marking {} as FAILED", dsException.getResource().getId(), dsException);
                DepositUtil.markDepositFailed(dsException.getResource().getId(), cri);
            }

            if (dsException.getResource().getClass() == Submission.class) {
                LOG.error("Unrecoverable error, marking {} as FAILED", dsException.getResource().getId(), dsException);
                DepositUtil.markSubmissionFailed(dsException.getResource().getId(), cri);
            }

            return;
        }

        LOG.error("Unrecoverable error (note that {} is missing its PassEntity resource - no resources will be be " +
                  "marked as FAILED)",
                  dsException.getClass().getName(), t);

    }
}
