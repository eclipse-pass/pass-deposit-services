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

import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.BaseAssemblerIT;
import org.junit.Before;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.DepositTestUtil.asList;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.DspaceMetsAssembler.APPLICATION_ZIP;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.DspaceMetsAssembler.SPEC_DSPACE_METS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FLOCAT;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XLINK_HREF;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XLINK_NS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class BaseDspaceMetsAssemblerIT extends BaseAssemblerIT {

    /**
     * The mets.xml from the package built and extracted by {@link #setUp()}, parsed into a {@link Document}
     */
    protected Document metsDoc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        metsDoc = DspaceDepositTestUtil.getMetsXml(extractedPackageDir);
    }

    @Override
    protected DspaceMetsAssembler assemblerUnderTest() {
        return new DspaceMetsAssembler(mbf, rbf,
                new DspaceMetadataDomWriter(DocumentBuilderFactory.newInstance()));
    }

    @Override
    protected void verifyStreamMetadata(PackageStream.Metadata metadata) {
        assertEquals(PackageStream.COMPRESSION.ZIP, metadata.compression());
        assertEquals(PackageStream.ARCHIVE.ZIP, metadata.archive());
        assertTrue(metadata.archived());
        assertEquals(SPEC_DSPACE_METS, metadata.spec());
        assertEquals(APPLICATION_ZIP, metadata.mimeType());
    }

    protected static void verifyPackageStructure(Document metsDoc, File extractedPackageDir, List<Resource>
            custodialResources) {
        List<String> custodialResourceFilenames = custodialResources.stream()
                .map(Resource::getFilename).collect(Collectors.toList());

        // expect a file for every resource under data/ directory
        // expect a mets xml file at the base directory
        // expect mets xml to have a fileSec with a file for each resource with the correct path

        // Each custodial resource is represented in the package under the 'data/' directory
        custodialResources.forEach(custodialResource -> {
            assertTrue(extractedPackageDir.toPath().resolve("data/" + custodialResource.getFilename()).toFile().exists());
        });

        // Each custodial resource in the package has a File and Fptr in METS.xml, pointing to the correct location.
        /*
        <fileSec ID="b7a991c6-4d9a-4c93-9646-aeb3a314d0ae">
            <fileGrp ID="b6efacc8-b9b6-418e-8dd2-a42c60c6f08e" USE="CONTENT">
                <file CHECKSUM="03ffb26edcf6efe77c8acfaefa5ceffb" CHECKSUMTYPE="MD5"
                    ID="5f37c90d-d8d8-42d3-92e4-294ccec71976" MIMETYPE="image/tiff" SIZE="6640585">
                    <FLocat ID="91195427-9bea-42d6-8815-c392016b63c0" LOCTYPE="URL"
                        xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="data/Figure1.tif"/>
                </file>
                <file CHECKSUM="9bd7573f8820f44548d6fe2a225fbe7a" CHECKSUMTYPE="MD5"
                    ID="6dd5f7c6-d4d9-4435-9e36-cfb373f225fb" MIMETYPE="text/plain" SIZE="72">
                    <FLocat ID="ca54ce92-6fde-4932-bd9a-fa2a9ecac786" LOCTYPE="URL"
                        xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="data/manifest.txt"/>
                </file>
                <file CHECKSUM="8ac686ffd8a01c888bfed5130d3ac58c" CHECKSUMTYPE="MD5"
                    ID="99d33e71-4ae5-4895-9014-c96de74bcc50" MIMETYPE="application/xml" SIZE="591">
                    <FLocat ID="8fe756b2-a687-4aaf-b6e1-b9e485c9d855" LOCTYPE="URL"
                        xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="data/meta.xml"/>
                </file>
                <file CHECKSUM="2e5675221da66a2a4faa25d7c71dfb09" CHECKSUMTYPE="MD5"
                    ID="ca11b3e9-5689-4f77-8661-558d0f61087e" MIMETYPE="application/x-tika-msoffice"
                    SIZE="68608">
                    <FLocat ID="fc25705a-71b8-4cbb-aa12-da657710e931" LOCTYPE="URL"
                        xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="data/Sample2.doc"/>
                </file>
            </fileGrp>
        </fileSec>
         */

        List<Element> flocats = asList(metsDoc.getElementsByTagNameNS(METS_NS, METS_FLOCAT));
        List<String> flocatHrefs = flocats.stream()
                .map(flocat -> flocat.getAttributeNS(XLINK_NS, XLINK_HREF)).collect(Collectors.toList());

        // each custodial resource has an flocat, and each flocat has a custodial resource
        assertEquals("Expected '" + custodialResources.size() + "' flocat elements in the package metadata",
                custodialResources.size(), flocats.size());
        custodialResources.forEach(custodialResource -> {
            assertTrue(flocatHrefs.contains("data/" + custodialResource.getFilename()));
        });
        flocatHrefs.forEach(flocat -> {
            assertTrue(custodialResourceFilenames.contains(flocat.substring("data/".length())));
        });
    }

}
