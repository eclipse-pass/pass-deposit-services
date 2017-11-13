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
package org.dataconservancy.nihms.submission;

import org.dataconservancy.nihms.assembler.Assembler;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.nihms.model.NihmsSubmission;
import org.dataconservancy.nihms.transport.Transport;
import org.dataconservancy.nihms.transport.TransportMetadata;
import org.dataconservancy.nihms.transport.TransportResponse;
import org.dataconservancy.nihms.transport.TransportSession;

import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionEngine {

    private static final String MODEL_ERROR = "Error building submission model: %s";

    private static final String SUBMISSION_ERROR = "Submission of package %s failed: %s";

    private SubmissionBuilder builder;

    private Assembler assembler;

    private Transport transport;

    public void submit(String formDataUrl) throws SubmissionFailure {

        NihmsSubmission submission = null;

        try {
            submission = builder.build(formDataUrl);
        } catch (Exception e) {
            throw new SubmissionFailure(format(MODEL_ERROR, e.getMessage()), e);
        }

        final String resource = format("%s/%s", nihmsDestinationDirectory(), nihmsDestinationFile());
        final TransportResponse response;

        try (TransportSession session = transport.open(Collections.emptyMap())) {
            PackageStream stream = assembler.assemble(submission);
            response = session
                    .send(resource, getTransportHints(), stream.open())
                    .get(3, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new SubmissionFailure(format(SUBMISSION_ERROR, resource, e.getMessage()), e);
        }

        if (!response.success()) {
            if (response.error() != null) {
                throw new SubmissionFailure(format(SUBMISSION_ERROR, resource, response.error().getMessage()), response.error());
            } else {
                throw new SubmissionFailure(format(SUBMISSION_ERROR, resource, "Cause unknown."));
            }
        }

    }

    private TransportMetadata getTransportHints() {
        return null;
    }

    private String nihmsDestinationDirectory() {
        return null;
    }

    private String nihmsDestinationFile() {
        return null;
    }

}
