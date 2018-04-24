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
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractZippedPackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractThreadedOutputStreamWriter;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;

import java.util.List;

public class DspaceMetsZippedPackageStream extends AbstractZippedPackageStream {

    private DspaceMetadataDomWriter metsWriter;

    private DepositSubmission submission;

    public DspaceMetsZippedPackageStream(DepositSubmission submission,
                                         List<org.springframework.core.io.Resource> custodialResources,
                                         MetadataBuilder metadataBuilder, ResourceBuilderFactory rbf,
                                         DspaceMetadataDomWriter metsWriter) {

        super(custodialResources, metadataBuilder, rbf);

        if (metsWriter == null) {
            throw new IllegalArgumentException("METS writer must not be null.");
        }

        if (submission == null) {
            throw new IllegalArgumentException("Submission must not be null.");
        }

        this.metsWriter = metsWriter;
        this.submission = submission;
    }

    @Override
    public AbstractThreadedOutputStreamWriter getStreamWriter(ArchiveOutputStream archiveOutputStream,
                                                              ResourceBuilderFactory rbf) {
        return new DspaceMetsThreadedOutputStreamWriter("DSpace Archive Writer", archiveOutputStream,
                submission, custodialContent, rbf, metsWriter);
    }
}
