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

import org.dataconservancy.pass.deposit.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;

import static java.util.Collections.emptyMap;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCTERMS_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCT_BIBLIOCITATION;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_PUBLISHER;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_CONTRIBUTOR;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_NS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static submissions.SubmissionResourceUtil.lookupStream;

/**
 * Tests the transport of metadata from a Submission Fedora resource through the
 * deposit services data model and on to the DSpace XML that is suitable for JScholarship.
 */
public class MetadataTest {

    private DspaceMetadataDomWriter domWriter;
    private FilesystemModelBuilder modelBuilder;

    private static final URI SUBMISSION_RESOURCE_1 = URI.create("fake:submission13");
    private static final URI SUBMISSION_RESOURCE_2 = URI.create("fake:submission14");

    @Before
    public void setup() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        domWriter = new DspaceMetadataDomWriter(dbf);
        modelBuilder = new FilesystemModelBuilder();
    }

    @Test
    public void commonContributorsAndFewAuthors() throws Exception {
        // Create deposit services data model from JSON representing Submission
        DepositSubmission depositSubmission = modelBuilder.build(lookupStream(SUBMISSION_RESOURCE_1), emptyMap());
        // Create XML DOM for DSpace metadata from deposit services data model
        Element qdc = domWriter.createDublinCoreMetadataDCMES(depositSubmission);

        // Take publisher from "common" if not in "crossref".
        assertNotNull(qdc.getElementsByTagNameNS(DC_NS, DC_PUBLISHER).item(0).getTextContent());
        assertEquals("Elsevier", qdc.getElementsByTagNameNS(DC_NS, DC_PUBLISHER).item(0).getTextContent());

        // Contributor list does not include submitting user, only PIs (from Grant) and authors (from metadata)
        assertEquals(4, qdc.getElementsByTagNameNS(DC_NS, DC_CONTRIBUTOR).getLength());
        for (int i = 0; i < 3; i++) {
            // Contributors must have names, whether they come from
            // first/middle/last or only display name (in Submission), or from metadata.
            Element contributor = (Element) qdc.getElementsByTagNameNS(DC_NS, DC_CONTRIBUTOR).item(i);
            assertNotEquals("", contributor.getTextContent());
        }

        // In citation, list up to three authors.  Take publication date from "common" if not in "crossref".
        assertNotNull(qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
        assertEquals("Christine Cagney, Mary Beth Lacey. (Fall 2016). \"My Test Article.\" Nature Communications.",
                qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
    }


    @Test
    public void crossrefAndManyAuthors() throws Exception {
        // Create deposit services data model from JSON representing Submission
        DepositSubmission depositSubmission = modelBuilder.build(lookupStream(SUBMISSION_RESOURCE_2), emptyMap());
        // Create XML DOM for DSpace metadata from deposit services data model
        Element qdc = domWriter.createDublinCoreMetadataDCMES(depositSubmission);

        // Publisher name in "crossref" has precedence over one in "common".
        assertNotNull(qdc.getElementsByTagNameNS(DC_NS, DC_PUBLISHER).item(0).getTextContent());
        assertEquals("Springer", qdc.getElementsByTagNameNS(DC_NS, DC_PUBLISHER).item(0).getTextContent());

        // In citation, use "et al" for more than three authors.
        // Publication date in "crossref" has precedence over one in "common".
        assertNotNull(qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
        String foo = qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent();
        assertEquals("Christine Cagney, Mary Beth Lacey, David Michael Starsky, et al. (Spring 2018). \"My Test Article.\" Nature Communications.",
                qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());
    }
}
