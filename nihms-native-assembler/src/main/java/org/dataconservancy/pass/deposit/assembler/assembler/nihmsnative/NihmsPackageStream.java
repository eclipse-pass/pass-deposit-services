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
import org.dataconservancy.pass.deposit.assembler.shared.DefaultStreamWriterImpl;
import org.dataconservancy.pass.deposit.assembler.shared.ExceptionHandlingThreadPoolExecutor;
import org.dataconservancy.pass.deposit.assembler.shared.StreamWriter;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.ArchivingPackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class NihmsPackageStream extends ArchivingPackageStream {

    static final String REMEDIATED_FILE_PREFIX = "SUBMISSION-";

    static final String MANIFEST_ENTRY_NAME = "manifest.txt";

    static final String METADATA_ENTRY_NAME = "bulk_meta.xml";

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPackageStream.class);

    private StreamingSerializer manifestSerializer;

    private StreamingSerializer metadataSerializer;

    private DepositSubmission submission;

    private MetadataBuilder metadata;

    public NihmsPackageStream(DepositSubmission submission,
                              List<DepositFileResource> custodialResources,
                              MetadataBuilder metadata,
                              ResourceBuilderFactory rbf,
                              Map<String, Object> packageOptions,
                              ExceptionHandlingThreadPoolExecutor executorService) {
        super(custodialResources, metadata, rbf, packageOptions, executorService);
        this.submission = submission;
        this.metadata = metadata;
    }

    @Override
    public StreamWriter getStreamWriter(ArchiveOutputStream archiveOut,
                                        ResourceBuilderFactory rbf) {
        return new DefaultStreamWriterImpl(archiveOut, submission, custodialContent, rbf, packageOptions, new NihmsPackageProvider());
    }

}
