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

import static java.lang.Runtime.getRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ArchivingPackageStream implements PackageStream {

    static final Logger STREAMING_IO_LOG = LoggerFactory.getLogger("STREAMING_IO_LOG");

    static final String ERR_PUT_RESOURCE = "Error putting resource '%s' into archive output stream: %s";

    private static final Logger LOG = LoggerFactory.getLogger(ArchivingPackageStream.class);

    private static final int ONE_MIB = 2 ^ 20;

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

    private StreamWriter streamWriter;

    private ArchiveOutputStreamFactory archiveOutputStreamFactory;

    private PackageProvider packageProvider;

    public ArchivingPackageStream(DepositSubmission submission,
                                  List<DepositFileResource> custodialContent,
                                  MetadataBuilder metadataBuilder,
                                  ResourceBuilderFactory rbf,
                                  Map<String, Object> packageOptions,
                                  PackageProvider packageProvider) {
        this.custodialContent = custodialContent;
        this.metadataBuilder = metadataBuilder;
        this.rbf = rbf;
        this.packageOptions = packageOptions;
        this.executorService = new ExceptionHandlingThreadPoolExecutor(getRuntime().availableProcessors(),
                                                                       getRuntime().availableProcessors() * 2, 1,
                                                                       TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
        this.packageProvider = packageProvider;
        this.streamWriter = new DefaultStreamWriterImpl(submission, custodialContent, rbf, packageOptions,
                                                        packageProvider);
        if (STREAMING_IO_LOG.isDebugEnabled()) {
            this.archiveOutputStreamFactory = new DebuggingArchiveOutputStreamFactory(packageOptions);
        } else {
            this.archiveOutputStreamFactory = new DefaultArchiveOutputStreamFactory(packageOptions);
        }
    }

    /**
     * Creates a package stream that uses the archive format specified in the {@code packageOptions}.  The supplied
     * {@code custodialContent} will be present in the stream.  Metadata supplied to the {@code MetadataBuilder} and
     * {@code ResourceBuilder} (created from the {@code rbf}) may be included in the stream.
     *
     * @param custodialContent the custodial content of the package
     * @param metadataBuilder  interface used to add metadata describing the package
     * @param rbf              interface used to instantiate {@code ResourceBuilder} instances, used to add metadata
     *                         describing
     *                         individual resources in the package
     * @param packageOptions   the options used when building the package
     * @param executorService  used to launch a thread which <em>writes</em> content to the package stream
     * @param streamWriter     used to write content to the package stream
     */
    public ArchivingPackageStream(List<DepositFileResource> custodialContent,
                                  MetadataBuilder metadataBuilder,
                                  ResourceBuilderFactory rbf,
                                  Map<String, Object> packageOptions,
                                  ExceptionHandlingThreadPoolExecutor executorService,
                                  StreamWriter streamWriter) {
        this.custodialContent = custodialContent;
        this.metadataBuilder = metadataBuilder;
        this.rbf = rbf;
        this.packageOptions = packageOptions;
        this.executorService = executorService;
        this.streamWriter = streamWriter;
        if (STREAMING_IO_LOG.isDebugEnabled()) {
            this.archiveOutputStreamFactory = new DebuggingArchiveOutputStreamFactory(packageOptions);
        } else {
            this.archiveOutputStreamFactory = new DefaultArchiveOutputStreamFactory(packageOptions);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns an {@code PipedInputStream} whose bytes are supplied by an internal {@link
     * StreamWriter}.
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
        ArchiveOutputStream archiveOut = archiveOutputStreamFactory.newInstance(packageOptions, pipedOut);

        // Set on the writer, and used to report any exceptions caught by the writer to the reader.  That way a full
        // stack trace of the exception will be reported when it is encountered by the reader
        BiConsumer<Runnable, Throwable> exceptionHandler = (runnable, throwable) -> {

            // Make the exception caught by the writer available to the reader; set it on the PipedInputStream
            // The reader will use this to close any resources it has open when an exception occurs, and allow the
            // thread to be cleaned up.
            pipedIn.setWriterEx(throwable);

            if (throwable != null) {
                LOG.error("Error encountered when writing the package stream.", throwable);
            }

            STREAMING_IO_LOG.debug("{} closing {} and {}", this, pipedOut, archiveOut);
            try {

                pipedOut.close();
            } catch (IOException e) {
                STREAMING_IO_LOG.trace("Error closing piped output stream: {}", e.getMessage(), e);
            }

            try {
                archiveOut.close();
            } catch (IOException e) {
                try {
                    archiveOut.closeArchiveEntry();
                } catch (IOException e1) {
                    STREAMING_IO_LOG.trace("Error closing archive entry: {}", e1.getMessage(), e1);
                }

                try {
                    archiveOut.close();
                } catch (IOException e1) {
                    // too bad
                    STREAMING_IO_LOG.trace("Error closing the archive output stream: {}", e1.getMessage(), e1);
                }
            }
        };

        executorService.setExceptionHandler(exceptionHandler);

        // invoke call() from another thread
        CallableStreamWriter<?> callableSw = new CallableStreamWriter<>(streamWriter, archiveOut, custodialContent);
        executorService.submit(callableSw);

        return pipedIn;
    }

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
