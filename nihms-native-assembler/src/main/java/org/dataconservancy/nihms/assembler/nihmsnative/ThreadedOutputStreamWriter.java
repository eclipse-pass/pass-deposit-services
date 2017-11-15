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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static java.lang.String.format;
import static org.dataconservancy.nihms.assembler.nihmsnative.NihmsPackageStream.ERR_PUT_RESOURCE;
import static org.dataconservancy.nihms.assembler.nihmsnative.NihmsPackageStream.MANIFEST_ENTRY_NAME;
import static org.dataconservancy.nihms.assembler.nihmsnative.NihmsPackageStream.METADATA_ENTRY_NAME;

class ThreadedOutputStreamWriter extends Thread {

    private static final int THIRTY_TWO_KIB = 32 * 2 ^ 10;

    private static final Logger LOG = LoggerFactory.getLogger(ThreadedOutputStreamWriter.class);

    private ArchiveOutputStream archiveOut;

    private List<Resource> packageFiles;

    private StreamingSerializer manifestSerializer;

    private StreamingSerializer metadataSerializer;

    private CloseOutputstreamCallback closeStreamHandler;

    public ThreadedOutputStreamWriter(String threadName, ArchiveOutputStream archiveOut, List<Resource> packageFiles,
                                      StreamingSerializer manifestSerializer, StreamingSerializer metadataSerializer) {
        super(threadName);
        this.archiveOut = archiveOut;
        this.packageFiles = packageFiles;
        this.manifestSerializer = manifestSerializer;
        this.metadataSerializer = metadataSerializer;
    }

    @Override
    public void run() {
        try {
            // prepare a tar entry for each file in the archive

            // (1) need to know the name of each file going into the tar
            // (2) the size of each file going into the tar?

            TarArchiveEntry manifestEntry = new TarArchiveEntry(MANIFEST_ENTRY_NAME);

            TarArchiveEntry metadataEntry = new TarArchiveEntry(METADATA_ENTRY_NAME);
            putResource(archiveOut, manifestEntry, updateLength(manifestEntry, manifestSerializer.serialize()));
            putResource(archiveOut, metadataEntry, updateLength(metadataEntry, metadataSerializer.serialize()));
            packageFiles.forEach(resource -> {
                try {
                    final TarArchiveEntry archiveEntry = new TarArchiveEntry(resource.getFilename());
                    archiveEntry.setSize(resource.contentLength());
                    putResource(archiveOut, archiveEntry, resource.getInputStream());
                } catch (IOException e) {
                    throw new RuntimeException(format(ERR_PUT_RESOURCE, resource.getFilename(), e.getMessage()), e);
                }
            });
            archiveOut.finish();
            archiveOut.close();
        } catch (Exception e) {
            LOG.info("Exception encountered streaming package, cleaning up by closing the archive output stream: {}",
                    e.getMessage(), e);

            // special care needs to be taken when exceptions are encountered.  it is essential that the underlying
            // pipedoutputstream be closed. (1) the archive output stream prevents the underlying output streams from
            // being closed (2) this class isn't aware of the underlying output streams; there may be multiple of them
            // so the creator of this instance also supplies a callback which is invoked to close each of the underlying
            // streams, insuring that the pipedoutputstream is closed.
            closeStreamHandler.closeAll();
        }
    }

    /**
     * Called when an exception occurs writing to the piped output stream, or after all resources have been successfully
     * streamed to the piped output stream.
     *
     * @return the handler invoked to close output streams when an exception is encountered writing to the piped output
     * stream
     */
    public CloseOutputstreamCallback getCloseStreamHandler() {
        return closeStreamHandler;
    }

    public void setCloseStreamHandler(CloseOutputstreamCallback callback) {
        this.closeStreamHandler = callback;
    }

    private void putResource(ArchiveOutputStream archiveOut, ArchiveEntry archiveEntry, InputStream inputStream)
            throws IOException {
        archiveOut.putArchiveEntry(archiveEntry);
        IOUtils.copy(inputStream, archiveOut);
        archiveOut.closeArchiveEntry();
    }

    private InputStream updateLength(TarArchiveEntry entry, InputStream toSize) throws IOException {
        org.apache.commons.io.output.ByteArrayOutputStream baos =
                new org.apache.commons.io.output.ByteArrayOutputStream(THIRTY_TWO_KIB);
        IOUtils.copy(toSize, baos);
        entry.setSize(baos.size());
        LOG.debug("Updating tar entry {} size to {}", entry.getName(), baos.size());
        return new ByteArrayInputStream(baos.toByteArray());
    }

    interface CloseOutputstreamCallback {
        void closeAll();
    }


}
