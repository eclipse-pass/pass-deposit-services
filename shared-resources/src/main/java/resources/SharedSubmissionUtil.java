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
package resources;

import static submissions.SubmissionResourceUtil.lookupUri;

import java.net.URI;

import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SharedSubmissionUtil {

    public DepositSubmission asDepositSubmission(URI submissionUri, SubmissionBuilder builder) throws InvalidModel {
        URI submissionJsonUri = lookupUri(submissionUri);

        if (submissionJsonUri == null) {
            throw new RuntimeException("Unable to look up test resource URI for submission '" + submissionUri + "'");
        }

        return builder.build(submissionJsonUri.toString());
    }

}
