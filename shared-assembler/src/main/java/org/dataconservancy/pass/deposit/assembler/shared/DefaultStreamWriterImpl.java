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
package org.dataconservancy.pass.deposit.assembler.shared;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.dataconservancy.pass.deposit.assembler.shared.ArchivingPackageStream.ERR_PUT_RESOURCE;
import static org.dataconservancy.pass.deposit.assembler.shared.ArchivingPackageStream.STREAMING_IO_LOG;
import static org.dataconservancy.pass.deposit.assembler.shared.AssemblerSupport.detectMediaType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ContentLengthObserver;
import org.apache.commons.io.input.DigestObserver;
import org.apache.commons.io.input.ObservableInputStream;
import org.apache.tika.detect.DefaultDetector;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.assembler.shared.PackageProvider.SupplementalResource;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DefaultStreamWriterImpl implements StreamWriter {

    private List<DepositFileResource> packageFiles;

    private ResourceBuilderFactory rbf;

    private DepositSubmission submission;

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultStreamWriterImpl.class);

    protected ArchiveOutputStream archiveOut;

    protected Map<String, Object> packageOptions;

    protected PackageProvider packageProvider;

    /**
     * Constructs an {@code StreamWriter} that is supplied with the output stream being written to, the custodial
     * content being packaged, the submission, and other supporting classes.
     *
     * @param submission      the submission
     * @param packageFiles    the custodial content of the package
     * @param rbf             factory for building {@link PackageStream.Resource package resources}
     * @param packageOptions  options used for building the package
     * @param packageProvider used to resources within a package, and generate non-custodial package resources
     */
    public DefaultStreamWriterImpl(DepositSubmission submission,
                                   List<DepositFileResource> packageFiles,
                                   ResourceBuilderFactory rbf,
                                   Map<String, Object> packageOptions,
                                   PackageProvider packageProvider) {
        this.packageFiles = packageFiles;
        this.rbf = rbf;
        this.submission = submission;
        this.packageOptions = packageOptions;
        this.packageProvider = packageProvider;
    }

    @Override
    public void start(List<DepositFileResource> custodialFiles, ArchiveOutputStream archiveOut) throws IOException {
        this.archiveOut = archiveOut;
        List<PackageStream.Resource> assembledResources = new ArrayList<>();

        try {
            // prepare a tar entry for each file in the archive

            // (1) need to know the name of each file going into the tar
            // (2) the size of each file going into the tar?

            if (packageFiles.size() < 1) {
                throw new RuntimeException("Refusing to create an empty package: no Resources were supplied to this " +
                                           this.getClass().getName());
            }

            packageProvider.start(submission, custodialFiles, packageOptions);

            packageFiles.forEach(custodialFile -> assembledResources.add(assembleResource(custodialFile)));

            List<SupplementalResource> supplementalResources =
                packageProvider.finish(submission, assembledResources);

            supplementalResources.forEach(supplementalResource ->
                                              assembledResources.add(assembleResource(supplementalResource)));

            finish(submission, assembledResources);

            close();
        } catch (Exception e) {
            LOG.warn("Exception encountered streaming package, cleaning up by closing the archive output stream ({}) "
                     + "and any underlying output streams", archiveOut);

            // Must re-throw this exception when an error occurs streaming the package so that the caller knows to
            // fail the deposit
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        archiveOut.close();
    }

    @Override
    public void finish(DepositSubmission submission, List<PackageStream.Resource> custodialResources)
        throws IOException {
        archiveOut.finish();
    }

    @SuppressWarnings("unchecked")
    @Override
    public PackageStream.Resource writeResource(ResourceBuilder resourceBuilder, Resource resource) throws IOException {
        try (InputStream resourceIn = resource.getInputStream(); BufferedInputStream buffIn =
            resourceIn.markSupported() ? null : new BufferedInputStream(resourceIn)) {

            InputStream in;

            if (buffIn != null) {
                in = buffIn;
            } else {
                in = resourceIn;
            }

            resourceBuilder.mimeType(detectMediaType(in, new DefaultDetector()).toString());

            try (ObservableInputStream observableIn = new ObservableInputStream(in)) {
                ContentLengthObserver clObs = new ContentLengthObserver(resourceBuilder);
                observableIn.add(clObs);

                ((List<PackageOptions.Checksum.OPTS>) packageOptions.getOrDefault(
                    PackageOptions.Checksum.KEY, emptyList()))
                    .forEach(algo -> observableIn.add(new DigestObserver(resourceBuilder, algo)));

                if (resource instanceof DepositFileResource) {
                    resourceBuilder.name(packageProvider.packagePath((DepositFileResource) resource));
                }

                if (resource instanceof SupplementalResource) {
                    resourceBuilder.name(((SupplementalResource) resource).getPackagePath());
                }

                PackageStream.Resource packageResource = resourceBuilder.build();
                long length = resource.contentLength();
                ArchiveEntry archiveEntry = createEntry(packageResource.name(), length);
                writeResource(archiveOut, archiveEntry, observableIn);
            }

            LOG.debug("Adding resource: {}", resourceBuilder.build());
            return resourceBuilder.build();
        }
    }

    /**
     * Create an ArchiveEntry from a {@code String} name and a {@code long} length
     *
     * @param name   the name for the antry
     * @param length the length to be assigned to the entry if the entry type supports setSize() setSize() is
     *               not attempted if length &lt; 0
     * @return the ArchiveEntry
     */
    protected ArchiveEntry createEntry(String name, long length) {
        switch ((PackageOptions.Archive.OPTS) packageOptions.getOrDefault(PackageOptions.Archive.KEY,
                                                                          PackageOptions.Archive.OPTS.NONE)) {
            case TAR: {
                TarArchiveEntry entry = new TarArchiveEntry(name);
                if (length >= 0) {
                    entry.setSize(length);
                }
                return entry;
            }

            case ZIP: {
                ZipArchiveEntry entry = new ZipArchiveEntry(name);
                if (length >= 0) {
                    entry.setSize(length);
                }
                return entry;
            }

            default:
                return null;
        }
    }

    /**
     * Write the bytes supplied by {@code archiveEntryIn} to the supplied {@code ArchiveOutputStream}.  The supplied
     * {@code ArchiveEntry} is written to the stream first, followed by the bytes of {@code archiveEntryIn}.
     * <p>
     * Note this method closes the {@code ArchiveEntry} after the bytes of {@code archiveEntryIn} are written.
     * </p>
     *
     * @param archiveOut     the package output stream
     * @param archiveEntry   metadata describing {@code archiveEntryIn}, closed before this method returns
     * @param archiveEntryIn the bytes to be written
     * @throws IOException if there is an error encountered writing the bytes
     */
    private void writeResource(ArchiveOutputStream archiveOut, ArchiveEntry archiveEntry, InputStream archiveEntryIn)
        throws IOException {
        archiveOut.putArchiveEntry(archiveEntry);
        int bytesWritten = IOUtils.copy(archiveEntryIn, archiveOut);
        STREAMING_IO_LOG.debug("Wrote {}: {} bytes", archiveEntry.getName(), bytesWritten);
        archiveOut.closeArchiveEntry();
    }

    /**
     * Accepts a Spring {@code Resource} (typically a {@link DepositFileResource} or {@link SupplementalResource}), and
     * uses the {@link #rbf ResourceBuilderFactory} to build a {@link PackageStream.Resource} representation of the
     * supplied resource.  The bytes of the supplied resource are written to the package stream.
     *
     * @param resource the Spring {@code Resource} representing custodial or supplemental content to be written to the
     *                 package stream
     * @return the metadata describing the {@code resource} written to the package stream
     */
    private PackageStream.Resource assembleResource(Resource resource) {
        ResourceBuilder rb;
        try {
            rb = rbf.newInstance();
            return writeResource(rb, resource);
        } catch (IOException e) {
            throw new RuntimeException(format(ERR_PUT_RESOURCE, resource.getFilename(), e.getMessage()), e);
        }
    }

}
