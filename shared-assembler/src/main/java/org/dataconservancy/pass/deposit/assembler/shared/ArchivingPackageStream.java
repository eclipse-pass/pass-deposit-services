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

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import static org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive.OPTS.TAR;
import static org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive.OPTS.ZIP;

/**
 * Creates {@link PackageStream}s in a supported {@link Archive archival format}.  Package options, including the
 * archival format, are supplied upon construction.
 * <p>
 * This implementation employs {@link PipedOutputStream} and {@link PipedInputStream} to decouple write and read
 * operations to the {@code PackageStream}.  The intent is the caller (i.e. the client of {@code PackageStream}) can
 * {@link PackageStream#open() open} the stream and begin to read it without blocking.  At the same time,
 * the concrete implementation of {@code ArchivingPackageStream} begins to write the contents of the package in a
 * separate thread.
 * </p>
 * <p>
 * Subclasses of {@code ArchivingPackageStream} are expected to use the {@link MetadataBuilder} and
 * {@link ResourceBuilder} interfaces for adding metadata describing the stream and resources within the stream
 * ({@code ResourceBuilder} instances are obtained from the {@code ResourceBuilderFactory} supplied on construction).
 * </p>
 */
public abstract class ArchivingPackageStream implements PackageStream {

    static final String ERR_PUT_RESOURCE = "Error putting resource '%s' into archive output stream: %s";

    private static final Logger LOG = LoggerFactory.getLogger(ArchivingPackageStream.class);

    private static final int ONE_MIB = 2 ^ 20;

    protected static final String ERR_CREATING_ARCHIVE_STREAM = "Error creating a %s archive output stream: %s";

    protected static final String ERR_NO_ARCHIVE_FORMAT = "No supported archive format was specified in the metadata builder";

    /**
     * The custodial content to be packaged and streamed.
     */
    protected List<DepositFileResource> custodialContent;

    /**
     * The options to be consulted when building and streaming the package
     */
    protected Map<String, Object> packageOptions;

    private MetadataBuilder metadataBuilder;

    private ResourceBuilderFactory rbf;

    private ExceptionHandlingThreadPoolExecutor executorService;

    /**
     * Creates a package stream that uses the archive format specified in the {@code packageOptions}.  The supplied
     * {@code custodialContent} will be present in the stream.  Metadata supplied to the {@code MetadataBuilder} and
     * {@code ResourceBuilder} (created from the {@code rbf}) may be included in the stream.
     *  @param custodialContent the custodial content of the package
     * @param metadataBuilder interface used to add metadata describing the package
     * @param rbf interface used to instantiate {@code ResourceBuilder} instances, used to add metadata describing
 *            individual resources in the package
     * @param packageOptions the options used when building the package
     * @param executorService
     */
    public ArchivingPackageStream(List<DepositFileResource> custodialContent,
                                  MetadataBuilder metadataBuilder,
                                  ResourceBuilderFactory rbf,
                                  Map<String, Object> packageOptions,
                                  ExceptionHandlingThreadPoolExecutor executorService) {
        this.custodialContent = custodialContent;
        this.metadataBuilder = metadataBuilder;
        this.rbf = rbf;
        this.packageOptions = packageOptions;
        this.executorService = executorService;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an {@code PipedInputStream} whose bytes are supplied by the {@code Thread} returned
     * {@link #getStreamWriter(ArchiveOutputStream, ResourceBuilderFactory)}.  Subclasses supply the {@code Thread} by
     * implementing {@link #getStreamWriter(ArchiveOutputStream, ResourceBuilderFactory) getStreamWriter(...)}, and this
     * method invokes {@link Thread#run() run()} prior to returning.
     * </p>
     * <p>
     * De-coupling the reading and writing of the stream allows the caller to open and begin reading the stream, even as
     * bytes are being written to the stream by this implementation.
     * </p>
     *
     * @return {@inheritDoc}
     */
    @Override
    public InputStream open() {

        // Create a pipe: bytes written to the PipedOutputStream will be the source of bytes read from the
        // PipedInputStream.  As the caller reads bytes from the PipedInputStream, bytes will be read from the
        // PipedOutputStream.
        ExHandingPipedInputStream pipedIn = new ExHandingPipedInputStream(ONE_MIB);

        PipedOutputStream pipedOut;
        try {
            pipedOut = new PipedOutputStream(pipedIn);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // Wrap the output stream in an ArchiveOutputStream
        // we support zip, tar and tar.gz so far
        ArchiveOutputStream archiveOut;

        if (packageOptions.getOrDefault(Archive.KEY, Archive.OPTS.NONE) == TAR) {
            try {
                if (packageOptions.getOrDefault(Compression.KEY, Compression.OPTS.NONE) == Compression.OPTS.GZIP) {
                    archiveOut = new TarArchiveOutputStream(new GzipCompressorOutputStream(pipedOut));
                } else {
                    archiveOut = new TarArchiveOutputStream(pipedOut);
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format(ERR_CREATING_ARCHIVE_STREAM, TAR, e.getMessage()), e);
            }
        } else if (packageOptions.getOrDefault(Archive.KEY, Archive.OPTS.NONE) == ZIP) {
            try {
                archiveOut = new ZipArchiveOutputStream(pipedOut);
            } catch (Exception e) {
                throw new RuntimeException(String.format(ERR_CREATING_ARCHIVE_STREAM, ZIP, e.getMessage()), e);
            }
        } else {
            throw new RuntimeException(ERR_NO_ARCHIVE_FORMAT);
        }

        // Set on the writer, and used to report any exceptions caught by the writer to the reader.  That way a full
        // stack trace of the exception will be reported when it is encountered by the reader
        BiConsumer<Runnable, Throwable> exceptionHandler = (runnable, throwable) -> {
            if (throwable == null && runnable instanceof Future<?>) {
                try {
                    Future<?> future = (Future<?>) runnable;
                    if (future.isDone()) {
                        future.get();
                    }
                } catch (CancellationException ce) {
                    throwable = ce;
                } catch (ExecutionException ee) {
                    throwable = ee.getCause();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            // Make the exception caught by the writer available to the reader; set it on the PipedInputStream
            // The reader will use this to close any resources it has open when an exception occurs, and allow the
            // thread to be cleaned up.
            pipedIn.setWriterEx(throwable);

            if (throwable != null) {
                LOG.error("Error encountered when writing the package stream.", throwable);
            }

            LOG.debug(">>>> {} closing {} and {}", this, pipedOut, archiveOut);
            try {

                pipedOut.close();
            } catch (IOException e) {
                LOG.trace("Error closing piped output stream: {}", e.getMessage(), e);
            }

            try {
                archiveOut.close();
            } catch (IOException e) {
                try {
                    archiveOut.closeArchiveEntry();
                } catch (IOException e1) {
                    LOG.trace("Error closing archive entry: {}", e1.getMessage(), e1);
                }

                try {
                    archiveOut.close();
                } catch (IOException e1) {
                    // too bad
                    LOG.trace("Error closing the archive output stream: {}", e1.getMessage(), e1);
                }
            }
        };

        executorService.setExceptionHandler(exceptionHandler);

        StreamWriter streamWriter = getStreamWriter(archiveOut, rbf);

        // invoke call() from another thread
        CallableStreamWriter<?> callableSw = new CallableStreamWriter<>(streamWriter, custodialContent);
        executorService.submit(callableSw);

        return pipedIn;
    }

    /**
     * Implementations must provide an {@link StreamWriter} that encapsulates the logic for
     * writing the package to the supplied {@code archiveOutputStream} and composing
     * {@link PackageStream.Resource package resources}.  Implementations must extend and return a subclass of
     * {@link StreamWriter}.
     *
     * @param archiveOutputStream the output stream that the package contents will be written to
     * @param rbf the builder factory used to create package resources
     * @return a {@link Thread} capable of writing a package to {@code archiveOutputStream} in its {@code run()} method
     */
    public abstract StreamWriter getStreamWriter(ArchiveOutputStream archiveOutputStream, ResourceBuilderFactory rbf);

    @Override
    public PackageStream.Metadata metadata() {
        return metadataBuilder.build();
    }

    /**
     * Unsupported by this implementation, always throws {@code UnsupportedOperationException}.
     *
     * @param packageResource the identifier for a resource within the package
     * @return {@inheritDoc}
     */
    @Override
    public InputStream open(String packageResource) {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported by this implementation, always throws {@code UnsupportedOperationException}.
     *
     * @return {@inheritDoc}
     */
    @Override
    public Iterator<PackageStream.Resource> resources() {
        throw new UnsupportedOperationException();
    }

}
