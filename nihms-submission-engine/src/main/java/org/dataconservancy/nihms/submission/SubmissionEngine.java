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
import org.dataconservancy.nihms.transport.TransportResponse;
import org.dataconservancy.nihms.transport.TransportSession;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.dataconservancy.nihms.transport.Transport.AUTHMODE;
import static org.dataconservancy.nihms.transport.Transport.PROTOCOL;
import static org.dataconservancy.nihms.transport.Transport.TRANSPORT_AUTHMODE;
import static org.dataconservancy.nihms.transport.Transport.TRANSPORT_PASSWORD;
import static org.dataconservancy.nihms.transport.Transport.TRANSPORT_PROTOCOL;
import static org.dataconservancy.nihms.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.dataconservancy.nihms.transport.Transport.TRANSPORT_SERVER_PORT;
import static org.dataconservancy.nihms.transport.Transport.TRANSPORT_USERNAME;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.BASE_DIRECTORY;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.DATA_TYPE;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.MODE;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.TRANSFER_MODE;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.TYPE;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.USE_PASV;

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

        // Build the submission
        NihmsSubmission submission = null;

        try {
            submission = builder.build(formDataUrl);
        } catch (Exception e) {
            throw new SubmissionFailure(format(MODEL_ERROR, e.getMessage()), e);
        }

        final TransportResponse response;
        String resourceName = null;

        // Open the underlying transport (FTP for NIHMS)
        // Assemble the package
        // Stream it to the target system
        try (TransportSession session = transport.open(getTransportHints(submission))) {
            PackageStream stream = assembler.assemble(submission);
            resourceName = stream.metadata().name();
            // this is using the piped input stream (returned from stream.open()).  does this have to occur in a
            // separate thread?
            response = session.send(resourceName, getTransportHints(submission), stream.open());
        } catch (Exception e) {
            throw new SubmissionFailure(format(SUBMISSION_ERROR, resourceName, e.getMessage()), e);
        }

        if (!response.success()) {
            throw new SubmissionFailure(
                    format(SUBMISSION_ERROR,
                            resourceName,
                            (response.error() != null) ? response.error().getMessage() : "Cause unknown"),
                    response.error());
        }

    }

    private Map<String, String> getTransportHints(NihmsSubmission submission) {
        return new HashMap<String, String>() {
            {
                put(TRANSPORT_PROTOCOL, PROTOCOL.ftp.name());
                put(TRANSPORT_AUTHMODE, AUTHMODE.userpass.name());
                put(TRANSPORT_USERNAME, "nihmsftpuser");
                put(TRANSPORT_PASSWORD, "nihmsftppass");
                put(TRANSPORT_SERVER_FQDN, "example.ftp.submission.nih.org");
                put(TRANSPORT_SERVER_PORT, "21");
                // TODO verify timezone with NIHMS
                put(BASE_DIRECTORY, String.format("/logs/upload/%s",
                        OffsetDateTime.now(ZoneId.of("UTC")).format(ISO_LOCAL_DATE)));
                put(TRANSFER_MODE, MODE.stream.name());
                put(USE_PASV, Boolean.TRUE.toString());
                put(DATA_TYPE, TYPE.binary.name());
            }
        };
    }

    // transport concern
    @Deprecated
    private String nihmsDestinationDirectory() {
        return null;
    }

    // filename could be provided by the assembler (e.g. bagit places requirements on the filename of the package)
    @Deprecated
    private String nihmsDestinationFile() {
        return null;
    }

}
