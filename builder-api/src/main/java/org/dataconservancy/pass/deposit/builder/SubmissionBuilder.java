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
package org.dataconservancy.pass.deposit.builder;

import org.dataconservancy.pass.deposit.model.DepositSubmission;

/**
 * Responsible for creating an instance of a {@link DepositSubmission submission}.  Knowledgeable of the
 * view model (i.e. the model used by the forms collecting submission information), the
 * {@link org.dataconservancy.pass.deposit.model submission model}, and the required metadata needed for building a
 * submission.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface SubmissionBuilder {

    /**
     * Retrieves the form data that was stored by the view using {@code formDataUrl}
     * Parses data into the submission model
     * Validates the submission model
     * Returns the submission
     *
     * @param formDataUrl url to the form data captured by the view
     * @return a submission for the NIHMS system based on the form data
     * @throws InvalidModel if the form data cannot be successfully parsed into a valid submission model
     */
    DepositSubmission build(final String formDataUrl) throws InvalidModel;

}
