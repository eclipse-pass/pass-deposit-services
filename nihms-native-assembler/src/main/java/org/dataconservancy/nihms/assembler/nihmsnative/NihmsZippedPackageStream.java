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
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractZippedPackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractThreadedOutputStreamWriter;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NihmsZippedPackageStream extends AbstractZippedPackageStream {

    static final String MANIFEST_ENTRY_NAME = "manifest.txt";

    static final String METADATA_ENTRY_NAME = "bulk_meta.xml";

    private static final Logger LOG = LoggerFactory.getLogger(NihmsZippedPackageStream.class);

    private StreamingSerializer manifestSerializer;

    private StreamingSerializer metadataSerializer;

    private DepositSubmission submission;

    public NihmsZippedPackageStream(DepositSubmission submission, List<org.springframework.core.io.Resource> custodialResources,
                                    MetadataBuilder metadata, ResourceBuilderFactory rbf) {
        super(custodialResources, metadata, rbf);
        this.submission = submission;
    }

    @Override
    public AbstractThreadedOutputStreamWriter getStreamWriter(ArchiveOutputStream archiveOut,
                                                              ResourceBuilderFactory rbf) {
        ThreadedOutputStreamWriter threadedWriter = new ThreadedOutputStreamWriter("Archive Piped Writer",
                archiveOut, submission, custodialContent, rbf, manifestSerializer, metadataSerializer);

        return threadedWriter;
    }

    public StreamingSerializer getManifestSerializer() {
        return manifestSerializer;
    }

    public void setManifestSerializer(StreamingSerializer manifestSerializer) {
        this.manifestSerializer = manifestSerializer;
    }

    public StreamingSerializer getMetadataSerializer() {
        return metadataSerializer;
    }

    public void setMetadataSerializer(StreamingSerializer metadataSerializer) {
        this.metadataSerializer = metadataSerializer;
    }

        if ((fileName.contentEquals(NihmsZippedPackageStream.METADATA_ENTRY_NAME) &&
            fileType != DepositFileType.bulksub_meta_xml) ||
            fileName.contentEquals(NihmsZippedPackageStream.MANIFEST_ENTRY_NAME)) {
            fileName = "SUBMISSION-" + fileName;
        }
        return fileName;
    }
}
