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
package org.dataconservancy.pass.deposit.builder.fs;

import static org.junit.Assert.assertNotNull;

import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.junit.Test;
import resources.SharedSubmissionUtil;
import submissions.SubmissionResourceUtil;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SharedSubmissionUtilTest {

    private SharedSubmissionUtil underTest = new SharedSubmissionUtil();

    private FilesystemModelBuilder builder = new FilesystemModelBuilder();

    @Test
    public void testLookupByUri() throws Exception {
        String msg = "Unable to convert test submission uri '%s' to a DepositSubmission";

        SubmissionResourceUtil.submissionUris().forEach(submissionUri -> {
            try {
                assertNotNull(String.format(msg, submissionUri), underTest.asDepositSubmission(submissionUri, builder));
            } catch (InvalidModel invalidModel) {
                throw new RuntimeException(String.format(msg, submissionUri), invalidModel);
            }
        });
    }
}
