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
package org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative;

import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.PackageProvider;
import org.dataconservancy.pass.deposit.assembler.shared.SizedStream;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsManifestSerializer.MANIFEST_ENTRY_NAME;
import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsManifestSerializer.METADATA_ENTRY_NAME;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsPackageProvider implements PackageProvider {

    static final String REMEDIATED_FILE_PREFIX = "SUBMISSION-";
    private static final Logger LOG = LoggerFactory.getLogger(NihmsPackageProvider.class);

    private NihmsManifestSerializer manifestSerializer;

    private NihmsMetadataSerializer metadataSerializer;

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
        if ((fileName.contentEquals(NihmsManifestSerializer.METADATA_ENTRY_NAME) &&
            fileType != DepositFileType.bulksub_meta_xml) ||
            fileName.contentEquals(NihmsManifestSerializer.MANIFEST_ENTRY_NAME)) {
            fileName = REMEDIATED_FILE_PREFIX + fileName;
        }
        return fileName;
    }

    @Override
    public void start(DepositSubmission submission, List<DepositFileResource> custodialResources) {
        manifestSerializer = new NihmsManifestSerializer(submission.getManifest());
        metadataSerializer = new NihmsMetadataSerializer(submission.getMetadata());
    }

    /**
     * {@inheritdoc}
     * <p>
     * Returns the file name for the resource, or a modified version thereof if the name matches that of one of the
     * default files that are included in a NIHMS deposit.  The file type is not known at this point in the code, but we
     * do know that it must be user-supplied and therefore not "bulksub_meta_xml" (the only type that warrants special
     * treatment in getNoncollidingFilename()).  So, the type is always passed as "supplement" to avoid that special
     * treatment.
     *
     * @param custodialResource the resource for which a safe file name will be returned.
     * @return a collision-free name for the provided resource file.
     */
    @Override
    public String packagePath(DepositFileResource custodialResource) {
        String candidateName;
        if (custodialResource.getDepositFile() != null && custodialResource.getDepositFile().getName() != null) {
            candidateName = custodialResource.getDepositFile().getName();
        } else {
            candidateName = custodialResource.getFilename();
        }

        String packagePath = getNonCollidingFilename(candidateName,
                custodialResource.getDepositFile().getType());
        LOG.trace("Pathed {} as {}", custodialResource, packagePath);
        return packagePath;
    }

    @Override
    public List<SupplementalResource> finish(DepositSubmission submission, List<PackageStream.Resource> packageResources) {
        ArrayList<SupplementalResource> supplementalResources = new ArrayList<>(2);
        SizedStream manifestStream = manifestSerializer.serialize();
        SizedStream metadataStream = metadataSerializer.serialize();
        supplementalResources.add(new NihmsSupplementalResource(MANIFEST_ENTRY_NAME, MANIFEST_ENTRY_NAME,
                manifestStream.getLength(), manifestStream.getInputStream(), "NIHMS Manifest"));
        supplementalResources.add(new NihmsSupplementalResource(METADATA_ENTRY_NAME, METADATA_ENTRY_NAME,
                metadataStream.getLength(), metadataStream.getInputStream(), "NIMS Metadata"));

        return supplementalResources;
    }

    private class NihmsSupplementalResource implements SupplementalResource {

        private String packagePath;

        private String name;

        private long length;

        private InputStream byteStream;

        private String description;

        public NihmsSupplementalResource(String packagePath, String name, long length, InputStream byteStream,
                                         String description) {
            this.packagePath = packagePath;
            this.name = name;
            this.length = length;
            this.byteStream = byteStream;
            this.description = description;
        }

        @Override
        public String getPackagePath() {
            return packagePath;
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public URL getURL() throws IOException {
            throw new IOException("Resource only exists in memory, and has no descriptor.");
        }

        @Override
        public URI getURI() throws IOException {
            throw new IOException("Resource only exists in memory, and has no descriptor.");
        }

        @Override
        public File getFile() throws IOException {
            throw new IOException("Resource only exists in memory, and has no descriptor.");
        }

        @Override
        public long contentLength() throws IOException {
            return length;
        }

        @Override
        public long lastModified() throws IOException {
            return System.currentTimeMillis();
        }

        @Override
        public Resource createRelative(String relativePath) throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public String getFilename() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return byteStream;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
