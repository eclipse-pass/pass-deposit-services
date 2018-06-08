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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ContentLengthObserver;
import org.apache.commons.io.input.DigestObserver;
import org.apache.commons.io.input.ObservableInputStream;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.assembler.ResourceBuilder;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Thread} responsible for assembling the custodial content and metadata of a package, and writing each
 * resource to the {@link ArchiveOutputStream} supplied on construction.
 * <p>
 * Assembling {@code PackageStream.Resource} objects includes characterizing the resource in the form of
 * {@link PackageStream.Resource#metadata() resource metadata}, and writing the bytes of the resource to the package
 * stream.
 * </p>
 * <p>
 * After the package resources have been assembled, {@link #assembleResources(DepositSubmission, List)} is invoked,
 * allowing sub-classes to generate any metadata (in the form of additional package resources) appropriate to the
 * packaging format (e.g. BagIt tag files or DSpace METS.xml files).
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class AbstractThreadedOutputStreamWriter extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractThreadedOutputStreamWriter.class);

    private List<Resource> packageFiles;

    private AbstractThreadedOutputStreamWriter.CloseOutputstreamCallback closeStreamHandler;

    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    private ResourceBuilderFactory rbf;

    private MetadataBuilder metadataBuilder;

    private DepositSubmission submission;

    protected static final int THIRTY_TWO_KIB = 32 * 2 ^ 10;

    protected ArchiveOutputStream archiveOut;

    /**
     * Constructs an {@code ArchiveOutputStream} that is supplied with the output stream being written to, the custodial
     * content being packaged, the submission, and other supporting classes.
     *
     * @param threadName the name used by this {@code Thread}
     * @param archiveOut the output stream being written to by this writer
     * @param submission the submission
     * @param packageFiles the custodial content of the package
     * @param rbf factory for building {@link org.dataconservancy.nihms.assembler.PackageStream.Resource
     *            package resources}
     */
    public AbstractThreadedOutputStreamWriter(String threadName, ArchiveOutputStream archiveOut,
                                              DepositSubmission submission, List<Resource> packageFiles,
                                              ResourceBuilderFactory rbf, MetadataBuilder metadataBuilder) {
        super(threadName);
        this.archiveOut = archiveOut;
        this.packageFiles = packageFiles;
        this.rbf = rbf;
        this.submission = submission;
        this.metadataBuilder = metadataBuilder;
    }

    /**
     * Writes the package to the output stream supplied on construction.  This includes:
     * <ol>
     *     <li>Creating {@link PackageStream.Resource}s for the custodial content of the package
     *     <ul>
     *         <li>Includes setting up the {@link ObservableInputStream} for characterizing each {@link Resource SpringResource} in the package</li>
     *     </ul></li>
     *     <li>Creating an {@link ArchiveEntry} for each {@code PackageStream.Resource}, and writing the resource to
     *         the output stream</li>
     *     <li>Invoking {@link #assembleResources(DepositSubmission, List)}, allowing sub-classes to add package-
     *         specific metadata to the output stream</li>
     * </ol>
     */
    @Override
    public void run() {
        List<PackageStream.Resource> assembledResources = new ArrayList<>();

        try {
            // prepare a tar entry for each file in the archive

            // (1) need to know the name of each file going into the tar
            // (2) the size of each file going into the tar?

            packageFiles.forEach(resource -> {
                ResourceBuilder rb = rbf.newInstance();
                try (InputStream resourceIn = resource.getInputStream();
                     BufferedInputStream buffIn = resourceIn.markSupported() ?
                             null : new BufferedInputStream(resourceIn)) {

                    InputStream in;

                    if (buffIn != null) {
                        in = buffIn;
                    } else {
                        in = resourceIn;
                    }

                    DefaultDetector detector = new DefaultDetector();
                    MediaType mimeType = detector.detect(in, new Metadata());
                    in.reset();
                    rb.mimeType(mimeType.toString());



                    ContentLengthObserver clObs = new ContentLengthObserver(rb);
                    DigestObserver md5Obs = new DigestObserver(rb, PackageStream.Algo.MD5);
                    DigestObserver sha256Obs = new DigestObserver(rb, PackageStream.Algo.SHA_256);
                    try (ObservableInputStream observableIn = new ObservableInputStream(in)) {
                        observableIn.add(clObs);
                        observableIn.add(md5Obs);
                        observableIn.add(sha256Obs);

                        rb.name(nameResource(resource));
                        PackageStream.Resource packageResource = rb.build();
                        long length = resource.contentLength();
                        ArchiveEntry archiveEntry = createEntry(packageResource.name(), length);
                        putResource(archiveOut, archiveEntry, observableIn);
                    }

                    LOG.debug(">>>> Adding resource: {}", rb.build());
                    assembledResources.add(rb.build());
                    rb.reset();
                } catch (IOException e) {
                    throw new RuntimeException(String.format(AbstractZippedPackageStream.ERR_PUT_RESOURCE, resource.getFilename(), e.getMessage()), e);
                }
            });

            // TODO: manifests, etc are built and serialized to the archiveOut stream
            // (must create TarArchiveEntry for each manifest)
            // build METS manifest from assembledResources
            // build NIHMS manifest from assembledResources and NihmsManifest (needed b/c it has the classifier info: manuscript, figure, table, etc.
            // build NIHMS bulk metadata from NihmsMetadata, Manuscript metadata, Journal metadata, Person metadata, Article metadata

            assembleResources(submission, assembledResources);

            // TODO: archiveOut should be closed and finished by the parent thread?
            archiveOut.finish();
            archiveOut.close();
        } catch (Exception e) {
            LOG.warn("Exception encountered streaming package, cleaning up by closing the archive output stream ({}) " +
                            "and any underlying output streams", archiveOut);

            if (uncaughtExceptionHandler != null) {
                uncaughtExceptionHandler.uncaughtException(this, e);
            }

            // special care needs to be taken when exceptions are encountered.  it is essential that the underlying
            // pipedoutputstream be closed. (1) the archive output stream prevents the underlying output streams from
            // being closed (2) this class isn't aware of the underlying output streams; there may be multiple of them
            // so the creator of this instance also supplies a callback which is invoked to close each of the underlying
            // streams, insuring that the pipedoutputstream is closed.
            if (closeStreamHandler != null) {
                closeStreamHandler.closeAll();
            }
        }
    }

    /**
     * Provide the name to set on the {@code PackageStream.Resource}
     * TODO: have some kind of adapter from a Spring Resource to a PackageStream.Resource
     * Useful to override if sub-classes want to include a path in the name, otherwise this implementation defaults
     * to {@link Resource#getFilename()}
     *
     * @param resource the resource
     * @return the name of the resource
     */
    protected String nameResource(Resource resource) {
        return resource.getFilename();
    }

    /**
     * Create an ArchiveEntry from a {@code String} name and a {@code long} length
     * @param name the name for the antry
     * @param length the length to be assigned to the entry if the entry type supports setSize()
     *               setSize() is not attempted if length < 0
     * @return the ArchiveEntry
     */
    protected ArchiveEntry createEntry(String name, long length) {
        PackageStream.Metadata metadata = metadata();
        if(metadata.archive().equals(PackageStream.ARCHIVE.TAR)) {
            TarArchiveEntry entry = new TarArchiveEntry(name);
            if (length >= 0) {
                entry.setSize(length);
            }
            return entry;
        } else if (metadata.archive().equals(PackageStream.ARCHIVE.ZIP)) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            if (length >= 0) {
                entry.setSize(length);
            }
            return entry;
        } else return null;
    }

    /**
     * A poorly-named method, provides implementations the hook to add package-specific metadata resources like BagIT
     * tag files or DSpace/METS METS.xml file.  Implementations are provided a list of resources in the package (i.e.
     * the custodial content), then they are able to compute, derive, or construct necessary metadata, and then add
     * the metadata to the package by calling {@link #putResource(ArchiveOutputStream, ArchiveEntry, InputStream)}.
     * <p>TODO: Rename method to make intent clear</p>
     * <p>TODO: Evaluate the visibility of the method, its arguments, and return value</p>
     *
     * @param submission the submission that resulted in the supplied resources
     * @param resources the custodial content of the package
     * @throws IOException if there are any errors adding resources to the package
     */
    public abstract void assembleResources(DepositSubmission submission, List<PackageStream.Resource> resources)
            throws IOException;

    /**
     * Called when an exception occurs writing to the piped output stream, or after all resources have been successfully
     * streamed to the piped output stream.
     *
     * @return the handler invoked to close output streams when an exception is encountered writing to the piped output
     * stream
     */
    public AbstractThreadedOutputStreamWriter.CloseOutputstreamCallback getCloseStreamHandler() {
        return closeStreamHandler;
    }

    /**
     * Called when an exception occurs writing to the piped output stream, or after all resources have been successfully
     * streamed to the piped output stream.
     *
     * @param callback the handler invoked to close output streams when an exception is encountered writing to the piped
     * output stream
     */
    public void setCloseStreamHandler(AbstractThreadedOutputStreamWriter.CloseOutputstreamCallback callback) {
        this.closeStreamHandler = callback;
    }

    @Override
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    /**
     * The bytes of the {@code inputStream} will be copied to the {@code ArchiveOutputStream} using the metadata in
     * {@code ArchiveEntry}.
     *
     * @param archiveOut the OutputStream of the package being written
     * @param archiveEntry metadata describing the bytes supplied by {@code inputStream}
     * @param inputStream the bytes to write to the package, described by {@code archiveEntry}
     * @throws IOException if putting the resource fails
     */
    protected void putResource(ArchiveOutputStream archiveOut, ArchiveEntry archiveEntry, InputStream inputStream)
            throws IOException {
        archiveOut.putArchiveEntry(archiveEntry);
        int bytesWritten = IOUtils.copy(inputStream, archiveOut);
        LOG.debug(">>>> Wrote {}: {} bytes", archiveEntry.getName(), bytesWritten);
        archiveOut.closeArchiveEntry();
    }

    protected InputStream updateLength(ArchiveEntry entry, InputStream toSize) throws IOException {
        org.apache.commons.io.output.ByteArrayOutputStream baos =
                new org.apache.commons.io.output.ByteArrayOutputStream(THIRTY_TWO_KIB);
        IOUtils.copy(toSize, baos);
        if (entry instanceof TarArchiveEntry) {
            ((TarArchiveEntry) entry).setSize(baos.size());
        } else if (entry instanceof ZipArchiveEntry) {
            ((ZipArchiveEntry) entry).setSize(baos.size());
         }
        LOG.debug("Updating archive entry {} size to {}", entry.getName(), baos.size());
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * The {@link ArchiveOutputStream} provided on {@link #AbstractThreadedOutputStreamWriter(String, ArchiveOutputStream,
     * DepositSubmission, List, ResourceBuilderFactory, MetadataBuilder) construction} is written to when the
     * {@link #run()} method is executed.  If there is a problem writing to the {@code ArchiveOutputStream}, it <em>must
     * </em> be {@link ArchiveOutputStream#close() closed} due to the threaded nature of {@link
     * java.io.PipedOutputStream} and {@link java.io.PipedInputStream}.
     * <p>
     * Now, the {@code ArchiveOutputStream} may wrap an unknown number of underlying {@code OutputStreams}s, and those
     * underlying streams may not properly chain a call to {@link ArchiveOutputStream#close()} when error conditions
     * arise.  Therefore, callers are encouraged to provide an implementation of {@code CloseOutputstreamCallback},
     * which, when invoked, will properly close the {@code ArchiveOutputStream} and all other underlying streams.
     * </p>
     */
    interface CloseOutputstreamCallback {
        void closeAll();
    }

    protected PackageStream.Metadata metadata() {
        return metadataBuilder.build();
    }
}
