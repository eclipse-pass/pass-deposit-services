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

package org.dataconservancy.pass.deposit.builder.fs;

import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.StreamingSubmissionBuilder;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a submission from a file on a locally mounted filesystem.
 * The file contains JSON data representing PassEntity objects that have unique IDs and link to each other.
 * The file must contain a single Submission object, which is the root of the data tree for a deposit.
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
public class FilesystemModelBuilder extends ModelBuilder implements SubmissionBuilder, StreamingSubmissionBuilder {

    /***
     * Build a DepositSubmission from the JSON data in named file.
     * @param formDataUrl url to the local file containing the JSON data
     * @return a deposit submission data model
     * @throws InvalidModel if the JSON data cannot be successfully parsed into a valid submission model
     */
    @Override
    public DepositSubmission build(String formDataUrl) throws InvalidModel {
        try {
            URI resource = new URI(formDataUrl);
            InputStream is;

            if (resource.getScheme() == null) {
                is = new FileInputStream(formDataUrl);
            } else if (resource.getScheme().startsWith("http") ||
                    resource.getScheme().startsWith("file") ||
                    resource.getScheme().startsWith("jar")) {
                is = resource.toURL().openStream();
            } else {
                throw new InvalidModel(String.format("Unknown scheme '%s' for URL '%s'",
                        resource.getScheme(), formDataUrl));
            }


            PassJsonFedoraAdapter reader = new PassJsonFedoraAdapter();
            HashMap<URI, PassEntity> entities = new HashMap<>();
            Submission submissionEntity = reader.jsonToPass(is, entities);
            is.close();
            return createDepositSubmission(submissionEntity, entities);
        } catch (FileNotFoundException e) {
            throw new InvalidModel(String.format("Could not open the data file '%s'.", formDataUrl), e);
        } catch (IOException e) {
            throw new InvalidModel(String.format("Failed to close the data file '%s'.", formDataUrl), e);
        } catch (URISyntaxException e) {
            throw new InvalidModel(String.format("Malformed URL '%s'.", formDataUrl), e);
        }
    }

    @Override
    public DepositSubmission build(InputStream stream, Map<String, String> streamMd) throws InvalidModel {
        PassJsonFedoraAdapter reader = new PassJsonFedoraAdapter();
        HashMap<URI, PassEntity> entities = new HashMap<>();
        Submission submissionEntity = reader.jsonToPass(stream, entities);
        return createDepositSubmission(submissionEntity, entities);
    }
}
