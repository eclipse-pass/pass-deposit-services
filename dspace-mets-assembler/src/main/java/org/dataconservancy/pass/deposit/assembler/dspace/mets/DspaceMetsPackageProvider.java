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
package org.dataconservancy.pass.deposit.assembler.dspace.mets;

import org.dataconservancy.pass.deposit.assembler.PackageStream.Resource;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.PackageProvider;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DspaceMetsPackageProvider implements PackageProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DspaceMetsPackageProvider.class);

    private static final String METS_XML = "mets.xml";

    private DspaceMetadataDomWriter metsWriter;

    private DspaceMetadataDomWriterFactory metsWriterFactory;

    public DspaceMetsPackageProvider(DspaceMetadataDomWriterFactory metsWriterFactory) {
        this.metsWriterFactory = metsWriterFactory;
    }

    @Override
    public void start(DepositSubmission submission, List<DepositFileResource> custodialResources) {
        this.metsWriter = metsWriterFactory.newInstance();
    }

    @Override
    public String packagePath(DepositFileResource custodialResource) {
        String candidateName;
        if (custodialResource.getDepositFile() != null && custodialResource.getDepositFile().getName() != null) {
            candidateName = custodialResource.getDepositFile().getName();
        } else {
            candidateName = custodialResource.getFilename();
        }

        String packagePath = "data/" + AbstractAssembler.sanitizeFilename(candidateName);
        LOG.trace("Pathed {} as {}", custodialResource, packagePath);
        return packagePath;
    }

    @Override
    public List<SupplementalResource> finish(DepositSubmission submission, List<Resource> packageResources) {
        packageResources.forEach(r -> System.err.println(">>>> Got resource: " + r));

        // this is where we compose the METS xml
        packageResources.forEach(r -> metsWriter.addResource(r));
        metsWriter.addSubmission(submission);

        ByteArrayOutputStream metsOut = new ByteArrayOutputStream();
        metsWriter.write(metsOut);
        ByteArrayInputStream metsIn = new ByteArrayInputStream(metsOut.toByteArray());

        return Collections.singletonList(new SupplementalResource() {
            @Override
            public String getPackagePath() {
                return METS_XML;
            }

            @Override
            public boolean exists() {
                return metsIn != null;
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
                return metsOut.size();
            }

            @Override
            public long lastModified() throws IOException {
                return System.currentTimeMillis();
            }

            @Override
            public org.springframework.core.io.Resource createRelative(String relativePath) throws IOException {
                throw new UnsupportedOperationException("Not supported.");
            }

            @Override
            public String getFilename() {
                return METS_XML;
            }

            @Override
            public String getDescription() {
                return "METS.xml describing the DSpace package";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return metsIn;
            }

            @Override
            public String toString() {
                return getDescription();
            }
        });
    }
}
