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

package org.dataconservancy.pass.deposit.assembler.shared;

import static org.dataconservancy.pass.deposit.assembler.shared.AssemblerSupport.buildMetadata;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dataconservancy.deposit.util.spring.EncodingClassPathResource;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.util.UriUtils;

/**
 * Abstract assembler implementation, which provides an implementation of {@link Assembler#assemble(
 *DepositSubmission, Map)} and {@link #resolveCustodialResources(List)}.  Sub-classes are expected to implement {@link
 * #createPackageStream(DepositSubmission, List, MetadataBuilder, ResourceBuilderFactory, Map)}.
 */
public abstract class AbstractAssembler implements Assembler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAssembler.class);

    private static final String ERR_MAPPING_LOCATION = "Unable to resolve the location of a submitted file ('%s') to " +
                                                       "a Spring Resource type.";

    private static final String FILE_PREFIX = "file:";

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String WILDCARD_CLASSPATH_PREFIX = "classpath*:";

    private static final String HTTP_PREFIX = "http:";

    private static final String HTTPS_PREFIX = "https:";

    private static final String ENCODED_CLASSPATH_PREFIX = EncodingClassPathResource.RESOURCE_KEY;

    private static final String JAR_PREFIX = "jar:";

    private MetadataBuilderFactory mbf;

    private ResourceBuilderFactory rbf;

    private String fedoraBaseUrl;

    private String fedoraUser;

    private String fedoraPassword;

    private boolean followRedirects;

    /**
     * Constructs a new assembler that provides {@link MetadataBuilderFactory} and {@link ResourceBuilderFactory} for
     * implementations to create and amend the state of package metadata and resources.
     *
     * @param mbf used by implementations to create package metadata
     * @param rbf used by implementations to create package resource metadata
     */
    public AbstractAssembler(MetadataBuilderFactory mbf, ResourceBuilderFactory rbf) {
        this.mbf = mbf;
        this.rbf = rbf;
    }

    /**
     * This abstract implementation will resolve the custodial content of the package as a {@code List} of
     * {@link DepositFileResource}s, then invoke
     * {@link #createPackageStream(DepositSubmission, List, MetadataBuilder, ResourceBuilderFactory, Map)
     * createPackageStream(...)}, which accepts the {@code DepositFileResource}s for inclusion in the returned {@link
     * PackageStream}.
     * <p>
     * Subclasses are expected to implement {@code createPackageStream(...)} and return a {@link PackageStream} that
     * includes the {@code DepositFileResource}s (as {@link PackageStream.Resource}s) and any package-specific metadata.
     * </p>
     *
     * @param submission the custodial content to be streamed by the returned {@code PackageStream}
     * @param options    the options used by subclasses when creating the package
     * @return a PackageStream ready to be {@link PackageStream#open() opened}
     */
    @Override
    public PackageStream assemble(DepositSubmission submission, Map<String, Object> options) {
        MetadataBuilder metadataBuilder = mbf.newInstance();
        buildMetadata(metadataBuilder, options);
        metadataBuilder.name(sanitizeFilename(submission.getName()));
        metadataBuilder.submissionMeta(submission.getSubmissionMeta());

        List<DepositFileResource> custodialResources = resolveCustodialResources(submission.getFiles());

        return createPackageStream(submission, custodialResources, metadataBuilder, rbf, options);
    }

    /**
     * Implementors are supplied with the {@link DepositSubmission submission}, the custodial content of the submission
     * in the form of {@link DepositFileResource}s, the package {@link MetadataBuilder}, and the package {@link
     * ResourceBuilderFactory}.  The returned {@link PackageStream} must satisfy the contract of {@link
     * PackageStream#open()} and {@link PackageStream#metadata()}.  Other methods on {@code PackageStream} are optional.
     * <p>
     * The returned {@code PackageStream} will be serialized according to the underlying implementation returned by this
     * method.For example, one implementation may serialize the package according to a BagIt profile, another
     * implementation may be configured to provide a DSpace/METS package profile.  While implementations are supplied
     * the custodial content of the package, they are required to generate the metadata content specific to the
     * implementation.  For example, A DSpace/METS implementation will be responsible for generating a {@code METS.xml}
     * file.  BagIt implementations will be responsible for generating the various BagIt tag files.  These
     * package-specific metadata are <em>not</em> included as {@code custodialResources}.
     * </p>
     *
     * @param submission         the submission of content and metadata, typically derived from the
     * {@link SubmissionBuilder
     *                           submission builder API}
     * @param custodialResources the custodial content to be included in the returned {@code PackageStream}
     * @param mdb                the interface for adding metadata describing the {@code PackageStream}
     * @param rbf                the interface for adding metadata for individual resources in the package stream
     * @param options            the options used by implementations when building the {@code PackageStream}
     * @return the {@code PackageStream} including the custodial content and implementation-specific metadata, ready to
     * be {@link PackageStream#open() opened} by the caller
     */
    protected abstract PackageStream createPackageStream(DepositSubmission submission,
                                                         List<DepositFileResource> custodialResources,
                                                         MetadataBuilder mdb, ResourceBuilderFactory rbf,
                                                         Map<String, Object> options);

    /**
     * Implementations are provided a manifest of custodial resources (in the form of {@code List<DepositFile>})
     * to be packaged.  Implementations are responsible for resolving each {@code DepositFile} to a byte stream, and
     * mapping the {@code DepositFile} to a {@link DepositFileResource}.
     * <p>
     * Custodial resources are the content to be preserved by, curated by, or deposited to a target system.  This
     * includes content uploaded by the end user to PASS, but <em>excludes</em> files related to packaging, such as
     * BagIT tag files, METS XML, or ORE Resource Maps.  Concrete assembler implementations will have the opportunity to
     * add packaging-related metadata in other methods.
     * </p>
     * <p>
     * The implementation provided by this method evaluates the URL returned by {@link DepositFile#getLocation()},
     * creates an appropriate Spring {@link Resource} (e.g. {@link FileSystemResource}, {@link ClassPathResource},
     * {@link UrlResource}, {@link AuthenticatedResource}), and places the {@code DepositFileResource} in the returned
     * {@code List}.  Ordering of the {@code manifest} is preserved in the returned {@code List}.  Callers expect that a
     * bytestream be returned when calling {@link DepositFileResource#getInputStream()} on elements of the
     * returned {@code List}.
     * </p>
     *
     * @param manifest a {@code List} of the custodial content to be assembled into a package
     * @return a Spring {@code DepositFileResource} for each entry in the manifest; entries in the returned {@code List}
     * are expected to {@link DepositFileResource#getInputStream() resolve} to byte streams
     */
    protected List<DepositFileResource> resolveCustodialResources(List<DepositFile> manifest) {
        // Locate byte streams containing uploaded manuscript and any supplement data
        // essentially, the custodial content of the package (i.e. excluding package-specific
        // metadata such as bagit tag files, or mets xml files)
        return manifest
            .stream()
            .map(DepositFileResource::new)
            .peek(dfr -> {
                try {
                    LOG.trace("Processing DepositFileResource:" +
                              "\n\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t\t{}: '{}'",
                              "resource", dfr.getResource(),
                              "resource.URI", dfr.getResource() != null ? dfr.getResource().getURI() : null,
                              "depositFile", dfr.getDepositFile(),
                              "depositFile.name", dfr.getDepositFile() != null ? dfr.getDepositFile().getName() : null,
                              "depositFile.label",
                              dfr.getDepositFile() != null ? dfr.getDepositFile().getLabel() : null,
                              "depositFile.type", dfr.getDepositFile() != null ? dfr.getDepositFile().getType() : null,
                              "depositFile.location",
                              dfr.getDepositFile() != null ? dfr.getDepositFile().getLocation() : null);
                } catch (IOException e) {
                    LOG.trace("Caught exception processing DepositFileResource:" +
                              "\n\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t\t{}: '{}'" +
                              "\n\t\t{}: '{}'",
                              "resource", dfr.getResource(),
                              "resource.URI", dfr.getResource() != null ? "Error getting URI: " + e.getMessage() : null,
                              "depositFile", dfr.getDepositFile(),
                              "depositFile.name", dfr.getDepositFile() != null ? dfr.getDepositFile().getName() : null,
                              "depositFile.label",
                              dfr.getDepositFile() != null ? dfr.getDepositFile().getLabel() : null,
                              "depositFile.type", dfr.getDepositFile() != null ? dfr.getDepositFile().getType() : null,
                              "depositFile.location",
                              dfr.getDepositFile() != null ? dfr.getDepositFile().getLocation() : null, e);
                }
            })
            .peek(dfr -> {
                String location = dfr.getDepositFile().getLocation();
                Resource delegateResource = null;

                if (location.startsWith(FILE_PREFIX)) {
                    try {
                        delegateResource = new UrlResource(location);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Unable to create URL resource for file location '" + location
                                                   + "': " + e.getMessage(), e);
                    }
                } else if (location.startsWith(CLASSPATH_PREFIX) ||
                           location.startsWith(WILDCARD_CLASSPATH_PREFIX)) {
                    if (location.startsWith(WILDCARD_CLASSPATH_PREFIX)) {
                        delegateResource = new ClassPathResource(
                            location.substring(WILDCARD_CLASSPATH_PREFIX.length()));
                    } else {

                        delegateResource = new ClassPathResource(location.substring(CLASSPATH_PREFIX.length()));
                    }
                } else if (location.startsWith(ENCODED_CLASSPATH_PREFIX)) {
                    delegateResource = new EncodingClassPathResource(
                        location.substring(ENCODED_CLASSPATH_PREFIX.length()));
                } else

                    // Defend against callers that have not specified Fedora auth creds, or repositories that
                    // do not require authentication
                    // TODO: a more flexible mechanism for authenticating to origin servers when retrieving resources
                    if (fedoraBaseUrl != null && location.startsWith(fedoraBaseUrl)) {
                        if (fedoraUser != null) {
                            try {
                                LOG.trace("Returning AuthenticatedResource for {}", location);
                                delegateResource = new AuthenticatedResource(new URL(location), fedoraUser,
                                                                             fedoraPassword);
                            } catch (MalformedURLException e) {
                                throw new RuntimeException(e.getMessage(), e);
                            }
                        }
                    } else if (location.startsWith(HTTP_PREFIX) || location.startsWith(HTTPS_PREFIX) ||
                               location.startsWith(JAR_PREFIX)) {
                        try {
                            delegateResource = new UrlResource(location);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    } else if (location.contains("/") || location.contains("\\")) {
                        // assume it is a file
                        delegateResource = new FileSystemResource(location);
                    }

                if (delegateResource == null) {
                    throw new RuntimeException(String.format(ERR_MAPPING_LOCATION, location));
                }

                dfr.setResource(delegateResource);

            })
            .collect(Collectors.toList());
    }

    /**
     * Sanitizes the supplied string, which is a candidate for use as a posix filename.  Alpha-numeric characters from
     * the latin-1 codeblock are allowed, all others are removed.  Path elements like {@code \} and {@code /} are
     * <em>not</em> allowed: this method does not accept file names with path components.
     *
     * @param candidateFilename the candidate filename which may contain illegal characters
     * @return the sanitized filename, with any illegal characters removed
     * @throws IllegalArgumentException if the supplied filename is null or empty, or if the result would return an
     *                                  empty string (i.e. the candidate filename is composed entirely of illegal
     *                                  characters)
     */
    public static String sanitizeFilename(String candidateFilename) {
        if (candidateFilename == null || candidateFilename.length() == 0) {
            throw new IllegalArgumentException("Supplied name was null or the empty string.");
        }

        String result = UriUtils.encodePathSegment(candidateFilename, "UTF-8");

        LOG.trace("Filename was sanitized from '{}' to '{}'", candidateFilename, result);

        return result;
    }

    public String getFedoraBaseUrl() {
        return fedoraBaseUrl;
    }

    @Value("${pass.fedora.baseurl}")
    public void setFedoraBaseUrl(String fedoraBaseUrl) {
        this.fedoraBaseUrl = fedoraBaseUrl;
    }

    public String getFedoraUser() {
        return fedoraUser;
    }

    @Value("${pass.fedora.user}")
    public void setFedoraUser(String fedoraUser) {
        this.fedoraUser = fedoraUser;
    }

    public String getFedoraPassword() {
        return fedoraPassword;
    }

    @Value("${pass.fedora.password}")
    public void setFedoraPassword(String fedoraPassword) {
        this.fedoraPassword = fedoraPassword;
    }

}
