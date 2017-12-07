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

package org.dataconservancy.nihms.assembler.nihmsnative;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.apache.commons.compress.archivers.ArchiveStreamFactory.TAR;

public class NihmsPackageStream implements PackageStream {

    static final String MANIFEST_ENTRY_NAME = "manifest.txt";

    static final String METADATA_ENTRY_NAME = "bulk_meta.xml";

    static final String ERR_CREATING_ARCHIVE_STREAM = "Error creating a %s archive output stream: %s";

    static final String ERR_PUT_RESOURCE = "Error putting resource '%s' into archive output stream: %s";

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPackageStream.class);

    private static final int ONE_MIB = 2 ^ 20;

    private StreamingSerializer manifestSerializer;

    private StreamingSerializer metadataSerializer;

    private List<org.springframework.core.io.Resource> packageFiles;

    private Metadata metadata;

    public NihmsPackageStream(StreamingSerializer manifestSerializer, StreamingSerializer metadataSerializer,
                              List<org.springframework.core.io.Resource> packageFiles, Metadata metadata) {
        this.manifestSerializer = manifestSerializer;
        this.metadataSerializer = metadataSerializer;
        this.packageFiles = packageFiles;
        this.metadata = metadata;
    }

    @Override
    public InputStream open() {

        PipedInputStream pipedIn = new PipedInputStream(ONE_MIB);
        PipedOutputStream pipedOut;
        try {
            pipedOut = new PipedOutputStream(pipedIn);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        TarArchiveOutputStream archiveOut;
        GZIPOutputStream gzipOut;

        try {
            gzipOut = new GZIPOutputStream(pipedOut, true);
            archiveOut = new TarArchiveOutputStream(gzipOut);
        } catch (Exception e) {
            throw new RuntimeException(String.format(ERR_CREATING_ARCHIVE_STREAM, TAR, e.getMessage()), e);
        }


        // put below in a thread, and start
        // then return pipedIn

        ThreadedOutputStreamWriter threadedWriter = new ThreadedOutputStreamWriter("Archive Piped Writer", archiveOut, packageFiles, manifestSerializer, metadataSerializer);
        threadedWriter.setCloseStreamHandler(() -> {
                    try {
                        pipedOut.close();
                    } catch (IOException e) {
                        LOG.info("Error closing piped output stream: {}", e.getMessage(), e);
                    }

                    try {
                        gzipOut.close();
                    } catch (IOException e) {
                        LOG.info("Error closing the gzip output stream: {}", e.getMessage(), e);
                    }

                    try {
                        archiveOut.close();
                    } catch (IOException e) {
                        try {
                            archiveOut.closeArchiveEntry();
                        } catch (IOException e1) {
                            LOG.info("Error closing archive entry: {}", e.getMessage(), e);
                        }

                        try {
                            archiveOut.close();
                        } catch (IOException e1) {
                            // too bad
                            LOG.info("Error closing the archive output stream: {}", e.getMessage(), e);
                        }
                    }
                }
        );
        threadedWriter.start();

        return pipedIn;

    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public InputStream open(String packageResource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Resource> resources() {
        throw new UnsupportedOperationException();
    }


}
