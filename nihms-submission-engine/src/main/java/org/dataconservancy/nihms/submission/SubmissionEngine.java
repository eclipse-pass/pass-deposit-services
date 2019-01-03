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

import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.dataconservancy.pass.deposit.transport.Transport.AUTHMODE;
import static org.dataconservancy.pass.deposit.transport.Transport.PROTOCOL;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_AUTHMODE;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_PORT;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_USERNAME;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints.DATA_TYPE;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints.MODE;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints.TRANSFER_MODE;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints.TYPE;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints.USE_PASV;

/**
 * Performs the submission of a manuscript and associated files to a target repository.
 *
 * A {@code SubmissionEngine} instance requires three collaborators:
 * <dl>
 *     <dt>{@link SubmissionBuilder Submission Builder}</dt>
 *     <dd>Responsible for building a {@link DepositSubmission submission model}.  This component, therefore, is
 *         influenced by the type and version of the model being employed.  Different versions or model types may
 *         require a builder specific to that model.</dd>
 *     <dt>{@link Assembler Package Assembler}</dt>
 *     <dd>Responsible for interrogating the submission model and creating a {@link PackageStream streamable} package
 *         of the submission contents.  The package includes everything that is required by the target repository,
 *         including manuscript files, supplement files, and any metadata, manifests or checksums.  Different target
 *         repositories will have different packaging requirements, so this component is largely influenced by the
 *         policy or requirements of the target repository.</dd>
 *     <dt>{@link Transport Transport Layer}</dt>
 *     <dd>Responsible for streaming a {@link PackageStream package} to the target repository.  This includes selecting
 *         the transport protocol (HTTP, FTP, etc.), configuring the parameters for the connection (authentication,
 *         TLS, etc.), the creation of any intermediate resources (e.g. intermediate directories or target
 *         "collections"), and finally streaming the contents of the package to a resource on the target repository.
 *         Different target repositories will support different transports, so this component is largely influenced by
 *         the technical platform and policies of the target repository.</dd>
 * </dl>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionEngine {

    // TODO verify timezone with NIHMS
    public static String BASE_DIRECTORY = format("/logs/upload/%s",
            OffsetDateTime.now(ZoneId.of("UTC")).format(ISO_LOCAL_DATE));

    private static String MODEL_ERROR = "Error building submission model: %s";

    private static String SUBMISSION_ERROR = "Submission of package %s failed: %s";

    private SubmissionBuilder builder;

    private Assembler assembler;

    private Transport transport;

    private Supplier<Map<String, String>> transportHints;

    /**
     * Instantiate a {@code SubmissionEngine} that is associated with a specific model, packaging format, and transport.
     * <p>
     * This instance will be able to {@link SubmissionBuilder#build(String) build} a {@link DepositSubmission submission
     * model}, {@link Assembler#assemble(DepositSubmission, Map) generate} a {@link PackageStream package}, and
     * {@link TransportSession#send(PackageStream, Map) deposit} the package in a target repository.
     * </p>
     *
     * @param builder the submission model builder
     * @param assembler the submission package assembler
     * @param transport the TCP transport used to deposit the package to the target repository
     */
    public SubmissionEngine(SubmissionBuilder builder, Assembler assembler, Transport transport) {
        this.builder = builder;
        this.assembler = assembler;
        this.transport = transport;
    }

    /**
     * Provided a reference to key-value pairs that represent a submission, this engine will build a model, assemble a
     * package, and deposit it to a target repository.  The {@code formDataUrl} references a resource that contains
     * the key-value pairs for a submission that adhere to a specific model.  The {@link
     * #SubmissionEngine(SubmissionBuilder, Assembler, Transport) model builder} supplied on construction must know how
     * to parse the information in the resource specified by {@code formDataUrl}.
     *
     * @param formDataUrl a URL to a resource containing key-value pairs representing a submission
     * @throws SubmissionFailure if the submission fails for any reason
     */
    public void submit(String formDataUrl) throws SubmissionFailure {

        // Build the submission
        DepositSubmission submission = null;

        try {
            submission = builder.build(formDataUrl);
        } catch (Exception e) {
            throw new SubmissionFailure(format(MODEL_ERROR, e.getMessage()), e);
        }

        TransportResponse response;
        String resourceName = null;

        // Open the underlying transport (FTP for NIHMS)
        // Assemble the package
        // Stream it to the target system
        try (TransportSession session = transport.open(getTransportHints(transportHints))) {
            PackageStream stream = assembler.assemble(submission, new HashMap<String, Object>()
            {
                {
                    put(PackageOptions.ARCHIVE_KEY, PackageOptions.ARCHIVE.ZIP);
                }
            });
            resourceName = stream.metadata().name();
            response = session.send(stream, getTransportHints(transportHints));
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

    /**
     * Provided key-value pairs that represent a submission, this engine will build a model, assemble a
     * package, and deposit it to a target repository.  The {@link
     * #SubmissionEngine(SubmissionBuilder, Assembler, Transport) model builder} supplied on construction must know how
     * to parse the information in the supplied {@code submissionProperties}
     * <p>
     * Implementation note: this method simply stores the provided {@code submissionProperties} into a temporary file,
     * and invokes {@link #submit(String)}.
     * </p>
     *
     * @param submissionProperties contains key-value pairs representing a submission
     * @throws SubmissionFailure if the submission fails for any reason
     */
    public void submit(Properties submissionProperties) throws SubmissionFailure {

        File properties;
        try {
            properties = File.createTempFile("SubmissionEngine-", ".properties");
            properties.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary submission properties file: " +
                    e.getMessage(), e);
        }

        try {
            try (FileOutputStream fos = new FileOutputStream(properties)) {
                submissionProperties.store(fos, format("Submission properties generated on %s by %s",
                        ISO_DATE_TIME.withZone(ZoneId.of("UTC")), this.getClass().getName()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to store temporary submission properties file: " + e.getMessage(), e);
        }

        submit(properties.toURI().getPath());

    }

    public Supplier<Map<String, String>> getTransportHints() {
        return transportHints;
    }

    public <T> void setTransportHints(Supplier<Map<String, String>> transportHints) {
        this.transportHints = transportHints;
    }

    private Map<String, String> getTransportHints(Supplier<Map<String, String>> transportHints) {

        if (transportHints != null) {
            Map<String, String> hints = transportHints.get();
            return hints;
        }

        return new HashMap<String, String>() {
            {
                put(TRANSPORT_PROTOCOL, PROTOCOL.ftp.name());
                put(TRANSPORT_AUTHMODE, AUTHMODE.userpass.name());
                put(TRANSPORT_USERNAME, "nihmsftpuser");
                put(TRANSPORT_PASSWORD, "nihmsftppass");
                put(TRANSPORT_SERVER_FQDN, "192.168.99.100");
                put(TRANSPORT_SERVER_PORT, "21");
                put(FtpTransportHints.BASE_DIRECTORY, BASE_DIRECTORY);
                put(TRANSFER_MODE, MODE.stream.name());
                put(USE_PASV, Boolean.TRUE.toString());
                put(DATA_TYPE, TYPE.binary.name());
            }
        };

    }

}
