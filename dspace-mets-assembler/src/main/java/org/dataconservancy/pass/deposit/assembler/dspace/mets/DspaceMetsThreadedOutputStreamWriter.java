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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractThreadedOutputStreamWriter;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DspaceMetsThreadedOutputStreamWriter extends AbstractThreadedOutputStreamWriter {

    private static final String METS_XML = "mets.xml";

    private DspaceMetadataDomWriter metsWriter;

    public DspaceMetsThreadedOutputStreamWriter(String threadName, ArchiveOutputStream archiveOut,
                                                DepositSubmission submission,
                                                List<DepositFileResource> packageFiles, ResourceBuilderFactory rbf,
                                                MetadataBuilder metadataBuilder,
                                                DspaceMetadataDomWriter metsWriter) {
        super(threadName, archiveOut, submission, packageFiles, rbf, metadataBuilder);

        if (metsWriter == null) {
            throw new IllegalArgumentException("DspaceMetadataDomWriter must not be null.");
        }
        this.metsWriter = metsWriter;
    }

    @Override
    public void assembleResources(DepositSubmission submission, List<PackageStream.Resource> resources)
            throws IOException {
        resources.forEach(r -> System.err.println(">>>> Got resource: " + r));

        // this is where we compose and write the METS xml to the ArchiveOutputStream
        resources.forEach(r -> metsWriter.addResource(r));
        metsWriter.addSubmission(submission);

        ByteArrayOutputStream metsOut = new ByteArrayOutputStream();
        metsWriter.write(metsOut);
        ByteArrayInputStream metsIn = new ByteArrayInputStream(metsOut.toByteArray());

        ArchiveEntry metsEntry = createEntry(METS_XML, metsOut.size());

        putResource(archiveOut, metsEntry, metsIn);
    }

    /**
     * Put all custodial content in a {@code data/} sub-directory.  This method will prepend {@code data/} to the name
     * of every {@code Resource}
     * <p>
     * {@inheritDoc}
     * </p>
     * @param resource
     * @return
     */
    @Override
    protected String nameResource(DepositFileResource resource) {
        return "data/" + super.nameResource(resource);
    }

}
