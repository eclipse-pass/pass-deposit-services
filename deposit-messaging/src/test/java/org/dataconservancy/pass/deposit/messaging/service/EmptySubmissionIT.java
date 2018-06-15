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
package org.dataconservancy.pass.deposit.messaging.service;

import afu.org.checkerframework.checker.igj.qual.I;
import org.junit.Ignore;
import org.junit.Test;

import java.io.InputStream;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class EmptySubmissionIT extends AbstractSubmissionIT {

    private static final String SUBMISSION_RESOURCES = "SubmissionProcessorIT-no-files.json";

    @Override
    protected InputStream getSubmissionResources() {
        return SubmissionTestUtil.getSubmissionResources(SUBMISSION_RESOURCES);
    }

    @Test
    @Ignore("TODO: Implement test when failure handling is properly implemented by SubmissionProcessor.")
    public void submissionWithNoFiles() throws Exception {

        // This submission should fail off the bat because there's no files in the submission.
        underTest.accept(submission);

        // We should observe a Submission with a status of FAILURE, no Deposits created, nor any RepositoryCopies
        // created.

        // The problem is that the failure handling for Submissions is inadequate at this point, and so Submissions
        // are not yet marked as FAILED when this happens.  This will happen in a future PR.

        // TODO: Implement test
    }
}
