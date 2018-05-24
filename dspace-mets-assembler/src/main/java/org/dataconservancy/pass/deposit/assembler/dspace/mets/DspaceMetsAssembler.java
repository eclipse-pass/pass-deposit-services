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

import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.MetadataBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DspaceMetsAssembler extends AbstractAssembler {

    private DspaceMetadataDomWriter metsWriter;

    @Autowired
    public DspaceMetsAssembler(MetadataBuilderFactory mbf, ResourceBuilderFactory rbf, DspaceMetadataDomWriter metsWriter) {
        super(mbf, rbf);
        this.metsWriter = metsWriter;
    }

    @Override
    protected PackageStream createPackageStream(DepositSubmission submission, List<Resource> custodialResources,
                                                MetadataBuilder mb, ResourceBuilderFactory rbf) {
        return new DspaceMetsZippedPackageStream(submission, custodialResources, mb, rbf, metsWriter);
    }

}
