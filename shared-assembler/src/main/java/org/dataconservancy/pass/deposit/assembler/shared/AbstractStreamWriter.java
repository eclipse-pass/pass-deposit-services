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
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.dataconservancy.pass.deposit.assembler.shared.AssemblerSupport.detectMediaType;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractStreamWriter implements StreamWriter {

    private List<DepositFileResource> packageFiles;

    private ResourceBuilderFactory rbf;

    private DepositSubmission submission;

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractStreamWriter.class);

    protected ArchiveOutputStream archiveOut;

    protected Map<String, Object> packageOptions;

    /**
     * Constructs an {@code StreamWriter} that is supplied with the output stream being written to, the custodial
     * content being packaged, the submission, and other supporting classes.
     *
     * @param archiveOut the output stream being written to by this writer
     * @param submission the submission
     * @param packageFiles the custodial content of the package
     * @param rbf factory for building {@link PackageStream.Resource package resources}
     * @param packageOptions options used for building the package
     */
    public AbstractStreamWriter(ArchiveOutputStream archiveOut, DepositSubmission submission,
                                List<DepositFileResource> packageFiles, ResourceBuilderFactory rbf, Map<String,
            Object> packageOptions) {
        this.archiveOut = archiveOut;
        this.packageFiles = packageFiles;
        this.rbf = rbf;
        this.submission = submission;
        this.packageOptions = packageOptions;
    }

    @Override
    public void start(List<DepositFileResource> custodialFiles) throws IOException {
        List<PackageStream.Resource> assembledResources = new ArrayList<>();

        try {
            // prepare a tar entry for each file in the archive

            // (1) need to know the name of each file going into the tar
            // (2) the size of each file going into the tar?

            if (packageFiles.size() < 1) {
                throw new RuntimeException("Refusing to create an empty package: no Resources were supplied " + "to " + "this " + this.getClass().getName());
            }

            packageFiles.forEach(custodialFile -> {
                ResourceBuilder rb = rbf.newInstance();
                PackageStream.Resource packageResource = null;
                try {
                    packageResource = buildResource(rb, custodialFile);
                } catch (IOException e) {
                    throw new RuntimeException(String.format(ArchivingPackageStream.ERR_PUT_RESOURCE,
                            custodialFile.getFilename(), e.getMessage()), e);
                }
                assembledResources.add(packageResource);
                rb.reset();
            });

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
    public void finish(DepositSubmission submission, List<PackageStream.Resource> custodialResources) throws IOException {
        assembleResources(submission, custodialResources);

        // TODO: archiveOut should be closed and finished by the parent thread?
        archiveOut.finish();
    }

    @SuppressWarnings("unchecked")
    @Override
    public PackageStream.Resource buildResource(ResourceBuilder resourceBuilder, DepositFileResource custodialFile) throws IOException {
        try (InputStream resourceIn = custodialFile.getInputStream(); BufferedInputStream buffIn =
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

                resourceBuilder.name(nameResource(custodialFile));
                PackageStream.Resource packageResource = resourceBuilder.build();
                long length = custodialFile.contentLength();
                ArchiveEntry archiveEntry = createEntry(packageResource.name(), length);
                writeResource(archiveOut, archiveEntry, observableIn);
            }

            LOG.debug(">>>> Adding resource: {}", resourceBuilder.build());
            return resourceBuilder.build();
        }
    }

    /**
     * Provide the name to set on the {@code PackageStream.Resource} TODO: have some kind of adapter from a Spring
     * Resource to a PackageStream.Resource Useful to override if sub-classes want to include a path in the name,
     * otherwise this implementation defaults to {@link Resource#getFilename()}
     *
     * @param resource the resource
     * @return the name of the resource
     */
    protected String nameResource(DepositFileResource resource) {
        if (resource.getDepositFile() != null && resource.getDepositFile().getName() != null) {
            return resource.getDepositFile().getName();
        }

        return resource.getFilename();
    }

    /**
     * Create an ArchiveEntry from a {@code String} name and a {@code long} length
     *
     * @param name the name for the antry
     * @param length the length to be assigned to the entry if the entry type supports setSize() setSize() is
     *         not attempted if length &lt; 0
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

    @Override
    public void writeResource(ArchiveOutputStream archiveOut, ArchiveEntry archiveEntry, InputStream archiveEntryIn) throws IOException {
        archiveOut.putArchiveEntry(archiveEntry);
        int bytesWritten = IOUtils.copy(archiveEntryIn, archiveOut);
        LOG.debug(">>>> Wrote {}: {} bytes", archiveEntry.getName(), bytesWritten);
        archiveOut.closeArchiveEntry();
    }

    /**
     * A poorly-named method, provides implementations the hook to add package-specific metadata resources like BagIT
     * tag files or DSpace/METS METS.xml file.  Implementations are provided a list of resources in the package (i.e.
     * the custodial content), then they are able to compute, derive, or construct necessary metadata, and then add the
     * metadata to the package by calling {@link #writeResource(ArchiveOutputStream, ArchiveEntry, InputStream)}.
     * <p>TODO: Rename method to make intent clear</p>
     * <p>TODO: Evaluate the visibility of the method, its arguments, and return value</p>
     *
     * @param submission the submission that resulted in the supplied resources
     * @param resources the custodial content of the package
     * @throws IOException if there are any errors adding resources to the package
     */
    public abstract void assembleResources(DepositSubmission submission, List<PackageStream.Resource> resources) throws IOException;

}
