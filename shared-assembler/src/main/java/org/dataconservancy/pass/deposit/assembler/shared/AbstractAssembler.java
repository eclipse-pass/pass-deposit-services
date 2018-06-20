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

import org.dataconservancy.nihms.assembler.Assembler;
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Abstract assembler implementation, which provides an implementation of {@link #assemble(DepositSubmission)} and
 * {@link #resolveCustodialResources(List)}.  Sub-classes are expected to implement {@link #createPackageStream(DepositSubmission, List, MetadataBuilder, ResourceBuilderFactory)}.
 */
public abstract class AbstractAssembler implements Assembler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAssembler.class);

    private static final String ERR_MAPPING_LOCATION = "Unable to resolve the location of a submitted file ('%s') to a Spring Resource type.";

    private static final String FILE_PREFIX = "file:";

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String WILDCARD_CLASSPATH_PREFIX = "classpath*:";

    private static final String HTTP_PREFIX = "http:";

    private static final String HTTPS_PREFIX = "https:";

    private MetadataBuilderFactory mbf;

    private ResourceBuilderFactory rbf;

    private String fedoraBaseUrl;

    private String fedoraUser;

    private String fedoraPassword;

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
     * Assembles Java {@code Object} references to <em>{@code InputStream}s</em> for each file in the package.  The
     * references are supplied to the {@code NihmsPackageStream} implementation, which does the heavy lifting of
     * actually creating a stream for the tar.gz archive.
     *
     * @param submission the custodial content being packaged
     * @return a PackageStream which actually creates the stream for the tar.gz archive
     */
    @Override
    public PackageStream assemble(DepositSubmission submission) {
        MetadataBuilder metadataBuilder = mbf.newInstance();
        metadataBuilder.name(sanitizeFilename(submission.getName()));

        List<DepositFileResource> custodialResources = resolveCustodialResources(submission.getFiles());

        return createPackageStream(submission, custodialResources, metadataBuilder, rbf);
    }

    /**
     * Implementors are supplied with the {@code submission}, the custodial content of the package in the form of
     * Spring {@link Resource}s, the package {@link MetadataBuilder}, and the package {@link ResourceBuilderFactory}.
     * The returned {@link PackageStream} must satisfy the contract of {@link PackageStream#open()} and {@link
     * PackageStream#metadata()}.  Other methods on {@code PackageStream} are optional.
     * <p>
     * The returned {@code PackageStream} will be serialized according to the underlying implementation.  For example,
     * one implementation may serialize the package according to a BagIt profile, another implementation may be
     * configured to provide a DSpace/METS package profile.  While implementations are supplied the custodial content
     * of the package, they are required to generate the metadata content specific to the implementation.  For example,
     * A DSpace/METS implementation will be responsible for generating a {@code METS.xml} file.  BagIt implementations
     * will be responsible for generating the various BagIt tag files.  These package-specific metadata are <em>not</em>
     * provided as {@code custodialResources}.
     * </p>
     *
     * @param submission the submission of content and metadata
     * @param custodialResources
     * @param mdb
     * @param rbf
     * @return
     */
    protected abstract PackageStream createPackageStream(DepositSubmission submission, List<DepositFileResource> custodialResources,
                                                         MetadataBuilder mdb, ResourceBuilderFactory rbf);

    /**
     * Implementations are provided a "manifest" of custodial resources (in the form of {@code List<DepositFile>})
     * that are to be packaged.  The implementation is responsible for providing a Spring {@link Resource} for each
     * {@link DepositFile} in the manifest.  The returned Spring {@code Resource}s are expected to resolve to bytestreams.
     * <p>
     * Custodial resources are the content to be preserved by, curated by, or deposited to a target system.  This
     * includes content uploaded by the end user to PASS, but <em>excludes</em> files related to packaging, such as
     * BagIT tag files, METS XML, or ORE Resource Maps.  Concrete assembler implementations will have the opportunity to
     * add packaging-related metadata in other methods.
     * </p>
     * <p>
     * The implementation provided by this method evaluates the URL returned by {@link DepositFile#getLocation()},
     * creates an appropriate Spring {@link Resource} ({@link FileSystemResource}, {@link ClassPathResource},
     * {@link UrlResource}), and places the Spring {@code Resource} in the returned {@code List}.  Ordering of the
     * {@code manifest} is preserved in the returned {@code List}.  Callers of this method may expect that a
     * bytestream be returned when calling {@link Resource#getInputStream()} on elements of the returned {@code List}.
     * </p>
     *
     * @param manifest resources that represent the custodial content to be assembled
     * @return a Spring {@code Resource} for each entry in the manifest; {@code Resource}s in the returned {@code List}
     *         are expected to resolve to bytestreams
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
                                "depositFile.label", dfr.getDepositFile() != null ? dfr.getDepositFile().getLabel() : null,
                                "depositFile.type", dfr.getDepositFile() != null ? dfr.getDepositFile().getType() : null,
                                "depositFile.location", dfr.getDepositFile() != null ? dfr.getDepositFile().getLocation() : null);
                    } catch (IOException e) {
                        LOG.trace("Processing DepositFileResource:" +
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
                                "depositFile.label", dfr.getDepositFile() != null ? dfr.getDepositFile().getLabel() : null,
                                "depositFile.type", dfr.getDepositFile() != null ? dfr.getDepositFile().getType() : null,
                                "depositFile.location", dfr.getDepositFile() != null ? dfr.getDepositFile().getLocation() : null);
                    }
                })
                .peek(dfr -> {
                    String location = dfr.getDepositFile().getLocation();
                    Resource delegateResource = null;

                    if (location.startsWith(FILE_PREFIX)) {
                        delegateResource = new FileSystemResource(location.substring(FILE_PREFIX.length()));
                    } else if (location.startsWith(CLASSPATH_PREFIX) ||
                            location.startsWith(WILDCARD_CLASSPATH_PREFIX)) {
                        if (location.startsWith(WILDCARD_CLASSPATH_PREFIX)) {
                            delegateResource = new ClassPathResource(location.substring(WILDCARD_CLASSPATH_PREFIX.length()));
                        } else {

                            delegateResource = new ClassPathResource(location.substring(CLASSPATH_PREFIX.length()));
                        }
                    } else

                    // Defend against callers that have not specified Fedora auth creds, or repositories that
                    // do not require authentication
                    // TODO: a more flexible mechanism for authenticating to origin servers when retrieving resources
                    if (fedoraBaseUrl != null && location.startsWith(fedoraBaseUrl)) {
                        if (fedoraUser != null) {
                            try {
                                LOG.trace(">>>> Returning AuthenticatedResource for {}", location);
                                delegateResource = new AuthenticatedResource(new URL(location), fedoraUser, fedoraPassword);
                            } catch (MalformedURLException e) {
                                throw new RuntimeException(e.getMessage(), e);
                            }
                        }
                    } else if (location.startsWith(HTTP_PREFIX) || location.startsWith(HTTPS_PREFIX)) {
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

        String result = candidateFilename
                .chars()
                .filter(AbstractAssembler::isValidChar)
                .mapToObj(c -> Character.toString((char)c))
                .collect(Collectors.joining());

        if (result.length() == 0) {
            throw new IllegalArgumentException("The supplied name was invalid, and cannot be sanitized.");
        }

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

    /**
     * Returns {@code true} if the supplied character is acceptable for use in a posix file name
     *
     * @param ch a character that may be used in file name
     * @return true if the character is acceptable, false otherwise
     */
    private static boolean isValidChar(int ch) {
        int i = ch & 0x0000FFFF;

        // outside of the latin-1 code block
        if (i >= 0x007f) {
            return false;
        }

        // a - z 0x61 - 0x7a
        if (i >= 0x0061 && i <= 0x007a) {
            return true;
        }

        // A - Z 0x41 - 0x5a
        if (i >= 0x0041 && i <= 0x005a) {
            return true;
        }

        // 0 - 9 0x30 - 0x39
        if (i >= 0x0030 && i <= 0x0039) {
            return true;
        }

        // Allow period (0x2e), dash (0x2d), underscore (0x5f)
        if (i == 0x002e || i == 0x002d || i == 0x005f) {
            return true;
        }

        // otherwise it's an illegal character inside of the latin-1 code block
        return false;
    }
}
