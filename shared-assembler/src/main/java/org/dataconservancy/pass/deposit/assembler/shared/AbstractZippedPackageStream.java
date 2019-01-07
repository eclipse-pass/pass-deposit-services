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
import org.dataconservancy.pass.deposit.assembler.PackageOptions.ARCHIVE;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.COMPRESSION;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.dataconservancy.pass.deposit.assembler.PackageOptions.ARCHIVE.TAR;
import static org.dataconservancy.pass.deposit.assembler.PackageOptions.ARCHIVE.ZIP;
import static org.dataconservancy.pass.deposit.assembler.PackageOptions.ARCHIVE_KEY;
import static org.dataconservancy.pass.deposit.assembler.PackageOptions.COMPRESSION_KEY;

public abstract class AbstractZippedPackageStream implements PackageStream {

    static final String ERR_PUT_RESOURCE = "Error putting resource '%s' into archive output stream: %s";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractZippedPackageStream.class);

    private static final int ONE_MIB = 2 ^ 20;

    protected static final String ERR_CREATING_ARCHIVE_STREAM = "Error creating a %s archive output stream: %s";
    protected static final String ERR_NO_ARCHIVE_FORMAT = "No supported archive format was specified in the metadata builder";

    protected List<DepositFileResource> custodialContent;

    protected Map<String, Object> packageOptions;

    private MetadataBuilder metadataBuilder;

    private ResourceBuilderFactory rbf;

    public AbstractZippedPackageStream(List<DepositFileResource> custodialContent,
                                       MetadataBuilder metadataBuilder,
                                       ResourceBuilderFactory rbf,
                                       Map<String, Object> packageOptions) {
        this.custodialContent = custodialContent;
        this.metadataBuilder = metadataBuilder;
        this.rbf = rbf;
        this.packageOptions = packageOptions;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implemetation returns an {@code InputStream} whose bytes are supplied by the
     * {@link #getStreamWriter(ArchiveOutputStream, ResourceBuilderFactory)}
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

        // Set on the writer, and used to report any exceptions caught by the writer to the reader.  That way a full
        // stack trace of the exception will be reported when it is encountered by the reader
        Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> {
            // Make the exception caught by the writer available to the reader; set it on the PipedInputStream
            // The reader will use this to close any resources it has open when an exception occurs, and allow the
            // thread to be cleaned up.
            pipedIn.setWriterEx(e);
        };

        PipedOutputStream pipedOut;
        try {
            pipedOut = new PipedOutputStream(pipedIn);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // Wrap the output stream in an ArchiveOutputStream
        // we support zip, tar and tar.gz so far
        ArchiveOutputStream archiveOut;

        if (packageOptions.getOrDefault(ARCHIVE_KEY, ARCHIVE.NONE) == TAR) {
            try {
                if (packageOptions.getOrDefault(COMPRESSION_KEY, COMPRESSION.NONE) == COMPRESSION.GZIP) {
                    archiveOut = new TarArchiveOutputStream(new GzipCompressorOutputStream(pipedOut));
                } else {
                    archiveOut = new TarArchiveOutputStream(pipedOut);
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format(ERR_CREATING_ARCHIVE_STREAM, TAR, e.getMessage()), e);
            }
        } else if (packageOptions.getOrDefault(ARCHIVE_KEY, ARCHIVE.NONE) == ZIP) {
            try {
                archiveOut = new ZipArchiveOutputStream(pipedOut);
            } catch (Exception e) {
                throw new RuntimeException(String.format(ERR_CREATING_ARCHIVE_STREAM, ZIP, e.getMessage()), e);
            }
        } else {
            throw new RuntimeException(ERR_NO_ARCHIVE_FORMAT);
        }


        AbstractThreadedOutputStreamWriter streamWriter = getStreamWriter(archiveOut, rbf);
        streamWriter.setCloseStreamHandler(getCloseOutputstreamHandler(pipedOut, archiveOut));
        streamWriter.setUncaughtExceptionHandler(exceptionHandler);
        streamWriter.start();

        return pipedIn;

    }

    /**
     * Implementations must provide an {@link AbstractThreadedOutputStreamWriter} that encapsulates the logic for
     * writing the package to the supplied {@code archiveOutputStream} and composing
     * {@link PackageStream.Resource package resources}.  Implementations are
     * encouraged to extend and return {@link AbstractThreadedOutputStreamWriter}.
     *
     * @param archiveOutputStream the output stream that the package contents will be written to
     * @param rbf the builder factory used to create package resources
     * @return a {@link Thread} capable of writing a package to {@code archiveOutputStream} in its {@code run()} method
     */
    public abstract AbstractThreadedOutputStreamWriter getStreamWriter(ArchiveOutputStream archiveOutputStream,
                                                                       ResourceBuilderFactory rbf);

    /**
     * Provides a callback that implements closure of the {@code PipedOutputStream} followed by the {@code
     * ArchiveOutputStream} and any currently open {@code ArchiveEntry}s.
     *
     * @param pipedOut the {@code PipedOutputStream} to be closed
     * @param archiveOut the {@code ArchiveOutputStream} to be closed
     * @return the callback providing an orderly closure of the output streams being written to
     */
    private AbstractThreadedOutputStreamWriter.CloseOutputstreamCallback getCloseOutputstreamHandler(
            PipedOutputStream pipedOut, ArchiveOutputStream archiveOut) {
        return () -> {
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
    }

    @Override
    public PackageStream.Metadata metadata() {
        return metadataBuilder.build();
    }

    @Override
    public InputStream open(String packageResource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<PackageStream.Resource> resources() {
        throw new UnsupportedOperationException();
    }

}
