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

package org.dataconservancy.pass.deposit.assembler.dspace.mets;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.ArchivingPackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.ThreadStreamWriter;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;

import java.util.List;
import java.util.Map;

public class DspacePackageStream extends ArchivingPackageStream {

    private DspaceMetadataDomWriterFactory metsWriterFactory;

    private DepositSubmission submission;

    private MetadataBuilder metadataBuilder;

    public DspacePackageStream(DepositSubmission submission,
                               List<DepositFileResource> custodialResources,
                               MetadataBuilder metadataBuilder, ResourceBuilderFactory rbf,
                               DspaceMetadataDomWriterFactory metsWriterFactory,
                               Map<String, Object> packageOptions) {

        super(custodialResources, metadataBuilder, rbf, packageOptions);

        if (metsWriterFactory == null) {
            throw new IllegalArgumentException("METS writer must not be null.");
        }

        if (submission == null) {
            throw new IllegalArgumentException("Submission must not be null.");
        }

        // TODO: this metadata writer used is - in part - a function of the package specification (DSpace METS)
        this.metsWriterFactory = metsWriterFactory;
        this.submission = submission;
        this.metadataBuilder = metadataBuilder;
    }

    @Override
    public ThreadStreamWriter getStreamWriter(ArchiveOutputStream archiveOutputStream,
                                              ResourceBuilderFactory rbf) {
        return new DspaceMetsStreamWriter("DSpace Archive Writer", archiveOutputStream,
                submission, custodialContent, rbf, metadataBuilder, metsWriterFactory.newInstance(), packageOptions);
    }
}
