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

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractZippedPackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractThreadedOutputStreamWriter;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NihmsZippedPackageStream extends AbstractZippedPackageStream {

    static final String REMEDIATED_FILE_PREFIX = "SUBMISSION-";

    static final String MANIFEST_ENTRY_NAME = "manifest.txt";

    static final String METADATA_ENTRY_NAME = "bulk_meta.xml";

    private static final Logger LOG = LoggerFactory.getLogger(NihmsZippedPackageStream.class);

    private StreamingSerializer manifestSerializer;

    private StreamingSerializer metadataSerializer;

    private DepositSubmission submission;

    private MetadataBuilder metadata;

    public NihmsZippedPackageStream(DepositSubmission submission, List<DepositFileResource> custodialResources,
                                    MetadataBuilder metadata, ResourceBuilderFactory rbf) {
        super(custodialResources, metadata, rbf);
        this.submission = submission;
        this.metadata = metadata;
    }

    @Override
    public AbstractThreadedOutputStreamWriter getStreamWriter(ArchiveOutputStream archiveOut,
                                                              ResourceBuilderFactory rbf) {
        ThreadedOutputStreamWriter threadedWriter = new ThreadedOutputStreamWriter("Archive Piped Writer",
                archiveOut, submission, custodialContent, rbf, metadata, manifestSerializer, metadataSerializer);

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

    /**
     * Given a file name and type, returns a file name that can safely be used in a NIHMS deposit
     * without causing a collision with the names of files that are automatically included in the deposit package.
     * If a collision is detected, the returned name is a modification of the supplied name.
     * Since multiple files in a submission may already have the same name, no effort is made to ensure that
     * the collision-free name is unique.
     *
     * @param fileName the name of a file to be included in the deposit, which may conflict with a reserved name.
     * @param fileType the type of the file whose name is being validated.
     * @return the existing file name, or a modified version if the existing name collides with a reserved name.
     */
    public static String getNonCollidingFilename(String fileName, DepositFileType fileType) {
        if ((fileName.contentEquals(NihmsZippedPackageStream.METADATA_ENTRY_NAME) &&
            fileType != DepositFileType.bulksub_meta_xml) ||
            fileName.contentEquals(NihmsZippedPackageStream.MANIFEST_ENTRY_NAME)) {
            fileName = REMEDIATED_FILE_PREFIX + fileName;
        }
        return fileName;
    }
}
