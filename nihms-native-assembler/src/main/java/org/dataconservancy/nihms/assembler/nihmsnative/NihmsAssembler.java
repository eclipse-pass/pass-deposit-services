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

import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.MetadataBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NihmsAssembler extends AbstractAssembler {

    private static final String ERR_MAPPING_LOCATION = "Unable to resolve the location of a submitted file ('%s') to a Spring Resource type.";

    private static final String FILE_PREFIX = "file:";

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String WILDCARD_CLASSPATH_PREFIX = "classpath*:";

    private static final String HTTP_PREFIX = "http:";

    private static final String HTTPS_PREFIX = "https:";

    @Autowired
    public NihmsAssembler(MetadataBuilderFactory mbf, ResourceBuilderFactory rbf) {
        super(mbf, rbf);
    }

    @Override
    protected PackageStream createPackageStream(DepositSubmission submission, List<Resource> custodialResources, MetadataBuilder mb, ResourceBuilderFactory rbf) {
        // Add manifest entry for metadata file
        DepositFile metadataFile = new DepositFile();
        metadataFile.setName(NihmsZippedPackageStream.METADATA_ENTRY_NAME);
        metadataFile.setType(DepositFileType.bulksub_meta_xml);
        metadataFile.setLabel("Metadata");
        submission.getManifest().getFiles().add(metadataFile);

        NihmsZippedPackageStream stream = new NihmsZippedPackageStream(submission, custodialResources, mb, rbf);
        stream.setManifestSerializer(new NihmsManifestSerializer(submission.getManifest()));
        stream.setMetadataSerializer(new NihmsMetadataSerializer(submission.getMetadata()));
        return stream;
    }

}
