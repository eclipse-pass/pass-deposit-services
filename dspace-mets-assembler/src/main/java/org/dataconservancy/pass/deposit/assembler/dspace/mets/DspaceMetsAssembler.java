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

import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.MetadataBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.dataconservancy.pass.deposit.assembler.shared.AssemblerSupport.buildMetadata;

@Component
public class DspaceMetsAssembler extends AbstractAssembler {

    // TODO: find a better place for these constants.

    /**
     * Package specification URI identifying a DSpace METS SIP.
     */
    public static final String SPEC_DSPACE_METS = "http://purl.org/net/sword/package/METSDSpaceSIP";

    /**
     * Package specification URI identifying a simple zip file.
     */
    public static final String SPEC_SIMPLE_ZIP = "http://purl.org/net/sword/package/SimpleZip";

    /**
     * Mime type of zip files.
     */
    public static final String APPLICATION_ZIP = "application/zip";

    // TODO: this metadata writer used is - in part - a function of the package specification (DSpace METS)
    private DspaceMetadataDomWriterFactory metsWriterFactory;

    @Autowired
    public DspaceMetsAssembler(MetadataBuilderFactory mbf, ResourceBuilderFactory rbf, DspaceMetadataDomWriterFactory
            metsWriterFactory) {
        super(mbf, rbf);
        // TODO: this metadata writer used is - in part - a function of the package specification (DSpace METS)
        this.metsWriterFactory = metsWriterFactory;
    }

    @Override
    protected PackageStream createPackageStream(DepositSubmission submission,
                                                List<DepositFileResource> custodialResources,
                                                MetadataBuilder mb, ResourceBuilderFactory rbf,
                                                Map<String, Object> options) {
        buildMetadata(mb, options);
        return new DspacePackageStream(submission, custodialResources, mb, rbf, metsWriterFactory, options);
    }

}
