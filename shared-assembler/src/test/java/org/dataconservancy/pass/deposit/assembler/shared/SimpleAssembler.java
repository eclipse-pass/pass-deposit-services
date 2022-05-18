/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.assembler.shared;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class SimpleAssembler extends AbstractAssembler {

    public SimpleAssembler(MetadataBuilderFactory mbf, ResourceBuilderFactory rbf) {
        super(mbf, rbf);
    }

    @Override
    protected PackageStream createPackageStream(DepositSubmission submission,
                                                List<DepositFileResource> custodialResources,
                                                MetadataBuilder mdb, ResourceBuilderFactory rbf,
                                                Map<String, Object> options) {
        return new ArchivingPackageStream(submission, custodialResources, mdb, rbf, options, new PackageProvider() {
            @Override
            public void start(DepositSubmission submission,
                              List<DepositFileResource> custodialResources,
                              Map<String, Object> packageOptions) {
                // no-op
            }

            @Override
            public String packagePath(DepositFileResource custodialResource) {
                return custodialResource.getFilename();
            }

            @Override
            public List<SupplementalResource> finish(DepositSubmission submission,
                                                     List<PackageStream.Resource> packageResources) {
                return Collections.emptyList();
            }
        });
    }
}
