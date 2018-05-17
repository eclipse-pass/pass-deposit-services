/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.nihms.builder.fs;

import org.dataconservancy.nihms.builder.InvalidModel;
import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;

/**
 * Builds a submission from a file on a locally mounted filesystem.
 * The file contains JSON data representing PassEntity objects that have unique IDs and link to each other.
 * The file must contain a single Submission object, which is the root of the data tree for a deposit.
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
public class FilesystemModelBuilder extends ModelBuilder implements SubmissionBuilder {

    /***
     * Build a DepositSubmission from the JSON data in named file.
     * @param formDataUrl url to the local file containing the JSON data
     * @return a deposit submission data model
     * @throws InvalidModel if the JSON data cannot be successfully parsed into a valid submission model
     */
    @Override
    public DepositSubmission build(String formDataUrl) throws InvalidModel {
        try {
            InputStream is = new FileInputStream(formDataUrl);
            PassJsonFedoraAdapter reader = new PassJsonFedoraAdapter();
            HashMap<URI, PassEntity> entities = new HashMap<>();
            Submission submissionEntity = reader.jsonToPass(is, entities);
            is.close();
            return createDepositSubmission(submissionEntity, entities);
        } catch (FileNotFoundException e) {
            throw new InvalidModel(String.format("Could not open the data file '%s'.", formDataUrl), e);
        } catch (IOException e) {
            throw new InvalidModel(String.format("Failed to close the data file '%s'.", formDataUrl), e);
        }
    }

}
