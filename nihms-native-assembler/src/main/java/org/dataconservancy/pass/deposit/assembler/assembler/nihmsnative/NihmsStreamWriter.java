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

package org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractStreamWriter;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.dataconservancy.pass.deposit.assembler.shared.AssemblerSupport.updateLength;

class NihmsStreamWriter extends AbstractStreamWriter {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsStreamWriter.class);

    private StreamingSerializer manifestSerializer;

    private StreamingSerializer metadataSerializer;

    private MetadataBuilder metadataBuilder;

    private Map<String, Object> packageOptions;


    public NihmsStreamWriter(ArchiveOutputStream archiveOut, DepositSubmission submission,
                             List<DepositFileResource> packageFiles, ResourceBuilderFactory rbf,
                             StreamingSerializer manifestSerializer,
                             StreamingSerializer metadataSerializer, Map<String, Object> packageOptions) {
        super(archiveOut, submission, packageFiles, rbf, packageOptions);
        this.manifestSerializer = manifestSerializer;
        this.metadataSerializer = metadataSerializer;
        this.metadataBuilder = metadataBuilder;
        this.packageOptions = packageOptions;
    }

    @Override
    public void assembleResources(DepositSubmission submission, List<PackageStream.Resource> resources)
            throws IOException {
        ArchiveEntry manifestEntry = createEntry(NihmsPackageStream.MANIFEST_ENTRY_NAME, -1);
        ArchiveEntry metadataEntry = createEntry(NihmsPackageStream.METADATA_ENTRY_NAME, -1);

        writeResource(archiveOut, manifestEntry, updateLength(manifestEntry, manifestSerializer.serialize()));
        writeResource(archiveOut, metadataEntry, updateLength(metadataEntry, metadataSerializer.serialize()));
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
     * always passed as "supplement" to avoid that special treatment.
     *
     * @param resource the resource for which a safe file name will be returned.
     * @return a collision-free name for the provided resource file.
     */
    @Override
    protected String nameResource(DepositFileResource resource) {
        return NihmsPackageStream.getNonCollidingFilename(super.nameResource(resource),
                resource.getDepositFile().getType());
    }

}
