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

import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.MetadataBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

@Component
public class NihmsAssembler extends AbstractAssembler {

    /**
     * Package specification URI identifying the NIHMS native packaging spec, as specified by their 07/2017
     * bulk publishing pdf.
     */
    public static final String SPEC_NIHMS_NATIVE_2017_07 = "nihms-native-2017-07";

    /**
     * Mime type of zip files.
     */
    public static final String APPLICATION_GZIP = "application/gzip";

    private static final String PACKAGE_FILE_NAME = "%s_%s_%s";

    @Autowired
    public NihmsAssembler(MetadataBuilderFactory mbf, ResourceBuilderFactory rbf) {
        super(mbf, rbf);
    }

    @Override
    protected PackageStream createPackageStream(DepositSubmission submission,
                                                List<DepositFileResource> custodialResources, MetadataBuilder mb,
                                                ResourceBuilderFactory rbf) {
        mb.spec(SPEC_NIHMS_NATIVE_2017_07);
        mb.archive(PackageStream.ARCHIVE.TAR);
        mb.archived(true);
        mb.compressed(true);
        mb.compression(PackageStream.COMPRESSION.GZIP);
        mb.mimeType(APPLICATION_GZIP);

        namePackage(submission, mb);

        NihmsZippedPackageStream stream = new NihmsZippedPackageStream(submission, custodialResources, mb, rbf);
        stream.setManifestSerializer(new NihmsManifestSerializer(submission.getManifest()));
        stream.setMetadataSerializer(new NihmsMetadataSerializer(submission.getMetadata()));
        return stream;
    }

    private static void namePackage(DepositSubmission submission, MetadataBuilder mb) {
        String submissionUuid = null;

        try {
            URI submissionUri = URI.create(submission.getId());
            submissionUuid = submissionUri.getPath().substring(submissionUri.getPath().lastIndexOf("/"));
        } catch (Exception e) {
            submissionUuid = UUID.randomUUID().toString();
        }

        String packageFileName = String.format(PACKAGE_FILE_NAME,
                SPEC_NIHMS_NATIVE_2017_07,
                OffsetDateTime.now(ZoneId.of("UTC")).format(ISO_LOCAL_DATE_TIME),
                submissionUuid);

        StringBuilder ext = new StringBuilder(packageFileName);
        PackageStream.Metadata md = mb.build();
        if (md.archived()) {
            switch (md.archive()) {
                case TAR:
                    ext.append(".tar");
                    break;
                case ZIP:
                    ext.append(".zip");
                    break;
            }
        }

        if (md.compressed()) {
            switch (md.compression()) {
                case BZIP2:
                    ext.append(".bz2");
                    break;
                case GZIP:
                    ext.append(".gzip");
                    break;
            }
        }

        mb.name(sanitizeFilename(ext.toString()));
    }

}
