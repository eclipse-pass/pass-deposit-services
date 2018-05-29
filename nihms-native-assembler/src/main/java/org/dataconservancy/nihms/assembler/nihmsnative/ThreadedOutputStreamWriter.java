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

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractThreadedOutputStreamWriter;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.dataconservancy.nihms.assembler.nihmsnative.NihmsZippedPackageStream.MANIFEST_ENTRY_NAME;
import static org.dataconservancy.nihms.assembler.nihmsnative.NihmsZippedPackageStream.METADATA_ENTRY_NAME;

class ThreadedOutputStreamWriter extends AbstractThreadedOutputStreamWriter {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadedOutputStreamWriter.class);

    private StreamingSerializer manifestSerializer;

    private StreamingSerializer metadataSerializer;

    public ThreadedOutputStreamWriter(String threadName, ArchiveOutputStream archiveOut, DepositSubmission submission,
                                      List<Resource> packageFiles, ResourceBuilderFactory rbf,
                                      StreamingSerializer manifestSerializer, StreamingSerializer metadataSerializer) {
        super(threadName, archiveOut, submission, packageFiles, rbf);
        this.manifestSerializer = manifestSerializer;
        this.metadataSerializer = metadataSerializer;
    }

    @Override
    public void assembleResources(DepositSubmission submission, List<PackageStream.Resource> resources)
            throws IOException {
        ZipArchiveEntry manifestEntry = new ZipArchiveEntry(MANIFEST_ENTRY_NAME);
        ZipArchiveEntry metadataEntry = new ZipArchiveEntry(METADATA_ENTRY_NAME);
        putResource(archiveOut, manifestEntry, updateLength(manifestEntry, manifestSerializer.serialize()));
        putResource(archiveOut, metadataEntry, updateLength(metadataEntry, metadataSerializer.serialize()));
        debugResources(resources);
    }

    private void debugResources(List<PackageStream.Resource> resources) {
        resources.forEach(r -> LOG.debug(">>>> Assembling resource: {}", r));
    }

    /**
     * {@inheritdoc}
     *
     * Returns the file name for the resource, or a modified version thereof if the name matches that of
     * one of the default files that are included in a NIHMS deposit.  The file type is not known at this
     * point in the code, but we do know that it must be user-supplied and therefore not "bulksub_meta_xml"
     * (the only type that warrants special treatment in getNoncollidingFilename()).  So, the type is
     * always passed as "supplemental" to avoid that special treatment.
     *
     * @param resource the resource for which a safe file name will be returned.
     * @return a collision-free name for the provided resource file.
     */
    @Override
    protected String nameResource(Resource resource) {
        return NihmsZippedPackageStream.getNonCollidingFilename(resource.getFilename(), DepositFileType.supplemental);
    }

}
