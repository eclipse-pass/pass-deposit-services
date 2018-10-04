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

import au.edu.apsr.mtk.base.DmdSec;
import au.edu.apsr.mtk.base.File;
import au.edu.apsr.mtk.base.FileGrp;
import au.edu.apsr.mtk.base.FileSec;
import org.apache.tika.io.IOUtils;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositMetadata;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.model.JournalPublicationType;
import org.dataconservancy.pass.deposit.DepositTestUtil;
import org.dataconservancy.pass.deposit.assembler.shared.ChecksumImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCTERMS_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCT_ABSTRACT;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCT_BIBLIOCITATION;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_CONTRIBUTOR;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_TITLE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_DESCRIPTION;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_ELEMENT;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_EMBARGO;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_EMBARGO_LIFT;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_EMBARGO_TERMS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_FIELD;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_MDSCHEMA;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_MDSCHEMA_DC;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_MDSCHEMA_LOCAL;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_PROVENANCE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DIM_QUALIFIER;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_CHECKSUM;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_CHECKSUM_TYPE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_CONTENT;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_DIV;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_DMDID;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_DMDSEC;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FILE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FILEGRP;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FILEID;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FILESEC;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FLOCAT;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FPTR;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_GROUPID;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_ID;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_LOCTYPE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_LOCTYPE_URL;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_MDTYPE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_MDTYPE_DC;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_MIMETYPE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_SIZE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_STRUCTMAP;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_USE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_XMLDATA;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XLINK_HREF;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XLINK_NS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests most of the nuances of the {@link DspaceMetadataDomWriter}.
 */
public class DspaceMetadataDomWriterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final Logger LOG = LoggerFactory.getLogger(DspaceMetadataDomWriterTest.class);

    private List<Resource> custodialContent = Arrays.asList(
            new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/manuscript.txt"),
            new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/figure.jpg"));

    private DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    private DspaceMetadataDomWriter underTest;

    @Before
    public void setUp() throws Exception {
        dbf.setNamespaceAware(true);
        underTest = new DspaceMetadataDomWriter(dbf);
    }

    /**
     * Creates a Mockito object for the DepositMetadata.Person class using the provided names and type.
     *
     * @param first The person's first name
     * @param middle The person's middle name, or null
     * @param last The person's last name
     * @param type The person's type (pi, copi, submitter, author)
     * @return The newly created mock Person
     */
    private DepositMetadata.Person createMockPerson(String first, String middle, String last, DepositMetadata.PERSON_TYPE type) {
        DepositMetadata.Person contributor = mock(DepositMetadata.Person.class);
        when(contributor.getName()).thenCallRealMethod();
        when(contributor.getReversedName()).thenCallRealMethod();

        if (first != null)
            when(contributor.getFirstName()).thenReturn(first);
        if (middle != null)
            when(contributor.getMiddleName()).thenReturn(middle);
        if (last != null)
            when(contributor.getLastName()).thenReturn(last);
        if (type != null)
            when(contributor.getType()).thenReturn(type);

        return contributor;
    }

    /**
     * Writes a sample METS.xml file, and copies it to stderr if DEBUG is enabled
     * @throws Exception
     */
    @Test
    public void writeSampleMets() throws Exception {
        DspaceMetadataDomWriter underTest = new DspaceMetadataDomWriter(DocumentBuilderFactory.newInstance());

        PackageStream.Resource r = mock(PackageStream.Resource.class);
        when(r.checksums()).thenReturn(Collections.singletonList(new ChecksumImpl(PackageStream.Algo.MD5, new byte[128], "base64", "hex")));
        when(r.mimeType()).thenReturn("application/octet-stream");
        when(r.name()).thenReturn("sample-resource.bin");
        when(r.sizeBytes()).thenReturn(1234L);

        DepositSubmission submission = mock(DepositSubmission.class);
        DepositMetadata mdHolder = mock(DepositMetadata.class);
        DepositMetadata.Manuscript manuscript = mock(DepositMetadata.Manuscript.class);
        DepositMetadata.Journal journal = mock(DepositMetadata.Journal.class);
        DepositMetadata.Article article = mock(DepositMetadata.Article.class);

        when(submission.getMetadata()).thenReturn(mdHolder);

        DepositMetadata.Person contributor1 = createMockPerson("Albert", null, "Einstein", DepositMetadata.PERSON_TYPE.author);
        DepositMetadata.Person contributor2 = createMockPerson("Stephen", null, "Hawking", DepositMetadata.PERSON_TYPE.author);
        DepositMetadata.Person contributor3  = createMockPerson("John", "Q.", "Public", DepositMetadata.PERSON_TYPE.author);
        DepositMetadata.Person contributor4  = createMockPerson("Jane", null, "Doe", DepositMetadata.PERSON_TYPE.author);
        List<DepositMetadata.Person> contributors = Arrays.asList(contributor1, contributor2, contributor3, contributor4);
        when(mdHolder.getPersons()).thenReturn(contributors);

        when(mdHolder.getManuscriptMetadata()).thenReturn(manuscript);
        when(mdHolder.getArticleMetadata()).thenReturn(article);
        when(mdHolder.getJournalMetadata()).thenReturn(journal);

        when(manuscript.getTitle()).thenReturn("Two stupendous minds.");
        when(manuscript.getManuscriptUrl()).thenReturn(
                URI.create("https://pass.library.johnshopkins.edu/fcrepo/rest/manuscripts/1234").toURL());
        when(manuscript.isPublisherPdf()).thenReturn(false);
        when(manuscript.getMsAbstract()).thenReturn("This is an abstract for the manuscript, provided by the" +
                " submitter.");

        when(article.getTitle()).thenReturn("Two stupendous minds");
        when(article.getDoi()).thenReturn(URI.create("https://dx.doi.org/123/456"));
        when(article.getEmbargoLiftDate()).thenReturn(ZonedDateTime.now());
        when(article.getVolume()).thenReturn("1");
        when(article.getIssue()).thenReturn("2");

        when(journal.getIssnPubTypes()).thenReturn(new HashMap<String, DepositMetadata.IssnPubType>() {
            {
                put("1236-5678", new DepositMetadata.IssnPubType("1236-5678", JournalPublicationType.PPUB));
            }
        });
        when(journal.getJournalTitle()).thenReturn("American Journal of XYZ Research");
        when(journal.getJournalId()).thenReturn("Am J of XYZ Res");
        when(journal.getPublisherName()).thenReturn("Super Publisher");
        when(journal.getPublicationDate()).thenReturn("2018-09-12");

        underTest.addResource(r);
        underTest.addSubmission(submission);

        java.io.File metsxml = tempFolder.newFile("testSimple-mets.xml");

        LOG.debug(">>>> Writing test METS output to {}", metsxml);
        underTest.write(new FileOutputStream(metsxml));
        if (LOG.isDebugEnabled()) {
            IOUtils.copy(new FileInputStream(metsxml), System.err);
        }
    }

    /**
     * A new root element should have xml namespace prefixes for each namespace in the {@link
     * XMLConstants#NS_TO_PREFIX_MAP}.  If the supplied {@code qualifiedName} is in the form {@code prefix:elementName},
     * and {@code prefix} is present in the {@link XMLConstants#NS_TO_PREFIX_MAP}, the prefix should only appear
     * once as an attribute.
     */
    @Test
    public void testNewRootElement() throws ParserConfigurationException {
        assertFalse(XMLConstants.NS_TO_PREFIX_MAP.isEmpty());

        String novelPrefix = "bar";
        assertFalse("Prefix '" + novelPrefix + "' was not expected to be present in the XMLConstants prefix map.",
                XMLConstants.NS_TO_PREFIX_MAP.values().contains(novelPrefix));
        String duplicatePrefix = XMLConstants.NS_TO_PREFIX_MAP.entrySet().iterator().next().getValue();
        String qualifiedElement = duplicatePrefix + ":foo";
        String ns = XMLConstants.NS_TO_PREFIX_MAP.entrySet().iterator().next().getKey();
        Document doc = dbf.newDocumentBuilder().newDocument();

        Element rootDuplicatePrefix = underTest.newRootElement(doc, ns, qualifiedElement);

        XMLConstants.NS_TO_PREFIX_MAP.values().forEach(prefix -> {
            String attrName = "xmlns:" + prefix;
            assertNotNull("Expected attribute '" + attrName + "' to be present on the root element.",
                    rootDuplicatePrefix.getAttribute(attrName));
        });

        qualifiedElement = novelPrefix + ":foo";
        doc = dbf.newDocumentBuilder().newDocument();

        Element rootNovelPrefix = underTest.newRootElement(doc, ns, qualifiedElement);

        XMLConstants.NS_TO_PREFIX_MAP.values().forEach(prefix -> {
            String attrName = "xmlns:" + prefix;
            assertNotNull("Expected attribute '" + attrName + "' to be present on the root element.",
                    rootNovelPrefix.getAttribute(attrName));
        });

        assertNotNull("Expected novel attribte 'xmlns:" + novelPrefix + "' to be present on the root element.",
                rootNovelPrefix.getAttribute("xmlns:" + novelPrefix));
    }

    /**
     * Adding a package resource to the DOM should include the addition of a {@code File} and {@code FLocat} element,
     * complete with attributes encoding the properties of the resource.  The checksum used will be the primary checksum
     * of the resource.  All files will belong to the same file group, with a {@code USE=CONTENT}.
     */
    @Test
    public void testAddResource() throws Exception {
        String name = "data/foo.txt";
        long sizeBytes = 34;
        String type = "text/plain";
        String checksumMd5Val = "abcdef12345";
        String checksumMd5 = PackageStream.Algo.MD5.name();
        String checksumShaVal = "123456abcdef";
        String checksumSha = PackageStream.Algo.SHA_256.name();

        PackageStream.Checksum checksum = mock(PackageStream.Checksum.class);
        when(checksum.algorithm()).thenReturn(PackageStream.Algo.MD5);
        when(checksum.asHex()).thenReturn(checksumMd5Val);
        PackageStream.Resource resource = mock(PackageStream.Resource.class);
        when(resource.name()).thenReturn(name);
        when(resource.sizeBytes()).thenReturn(sizeBytes);
        when(resource.checksum()).thenReturn(checksum);
        when(resource.mimeType()).thenReturn(type);

        underTest.addResource(resource);

        // Writing out and re-parsing allows the test to be somewhat independent of parsing model used by
        // the DspaceMetadataDomWriter
        Document result = DspaceDepositTestUtil.writeAndParseResults(dbf, underTest);

        assertEquals(1, result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILESEC).getLength());
        Element fileSec = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILESEC).item(0);
        assertNotNull(fileSec);

        assertEquals(1, fileSec.getElementsByTagNameNS(METS_NS, METS_FILEGRP).getLength());
        Element fileGrp = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILEGRP).item(0);
        assertNotNull(fileGrp);

        assertEquals(1, fileGrp.getElementsByTagNameNS(METS_NS, METS_FILE).getLength());
        Element file = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILE).item(0);
        assertNotNull(file);

        assertEquals(1, file.getElementsByTagNameNS(METS_NS, METS_FLOCAT).getLength());
        Element flocat = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FLOCAT).item(0);
        assertNotNull(flocat);

        // FileSec verification id
        assertNotNull(fileSec.getAttribute(METS_ID));

        // FileGrp verification id, use
        assertNotNull(fileGrp.getAttribute(METS_ID));
        assertEquals(METS_CONTENT, fileGrp.getAttribute(METS_USE));

        // File verification checksum, checksumtype, mimetype, size, id
        assertNotNull(file.getAttribute(METS_ID));
        assertEquals(checksumMd5Val, file.getAttribute(METS_CHECKSUM));
        assertEquals(checksumMd5, file.getAttribute(METS_CHECKSUM_TYPE));
        assertEquals(type, file.getAttribute(METS_MIMETYPE));
        assertEquals(String.valueOf(sizeBytes), file.getAttribute(METS_SIZE));

        // Flocat verification
        assertNotNull(flocat.getAttribute(METS_ID));
        assertEquals(METS_LOCTYPE_URL, flocat.getAttribute(METS_LOCTYPE));
        assertNotNull(flocat.getAttributeNS(XLINK_NS, XLINK_HREF));
        assertEquals(name, flocat.getAttributeNS(XLINK_NS, XLINK_HREF));
    }

    /**
     * Files should be added to the METS document in the order resources are added to the writer.
     */
    @Test
    public void testAddResourcesOrder() throws IOException, ParserConfigurationException, SAXException {
        String name1 = "resource1";
        String name2 = "resource2";
        String name3 = "resource3";
        String name4 = "resource4";
        String name5 = "resource5";
        PackageStream.Resource resource1 = mock(PackageStream.Resource.class);
        PackageStream.Resource resource2 = mock(PackageStream.Resource.class);
        PackageStream.Resource resource3 = mock(PackageStream.Resource.class);
        PackageStream.Resource resource4 = mock(PackageStream.Resource.class);
        PackageStream.Resource resource5 = mock(PackageStream.Resource.class);
        when(resource1.name()).thenReturn(name1);
        when(resource2.name()).thenReturn(name2);
        when(resource3.name()).thenReturn(name3);
        when(resource4.name()).thenReturn(name4);
        when(resource5.name()).thenReturn(name5);

        underTest.addResource(resource2);
        underTest.addResource(resource1);
        underTest.addResource(resource5);
        underTest.addResource(resource3);
        underTest.addResource(resource4);

        Document result = DspaceDepositTestUtil.writeAndParseResults(dbf, underTest);

        assertEquals(5, result.getElementsByTagNameNS(METS_NS, METS_FILE).getLength());
        Element file1 = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILE).item(0);
        Element file2 = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILE).item(1);
        Element file3 = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILE).item(2);
        Element file4 = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILE).item(3);
        Element file5 = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILE).item(4);

        assertEquals(name2, ((Element)file1.getFirstChild()).getAttributeNS(XLINK_NS, XLINK_HREF));
        assertEquals(name1, ((Element)file2.getFirstChild()).getAttributeNS(XLINK_NS, XLINK_HREF));
        assertEquals(name5, ((Element)file3.getFirstChild()).getAttributeNS(XLINK_NS, XLINK_HREF));
        assertEquals(name3, ((Element)file4.getFirstChild()).getAttributeNS(XLINK_NS, XLINK_HREF));
        assertEquals(name4, ((Element)file5.getFirstChild()).getAttributeNS(XLINK_NS, XLINK_HREF));
    }

    /**
     * When a resource is added to the METS document, only the first checksum is taken
     * @throws Exception
     */
    @Test
    public void testAddFirstChecksum() throws Exception {
        String name = "resource1";
        String checksumMd5Val = "abcdef12345";
        String checksumShaVal = "123456abcdef";

        PackageStream.Checksum md5 = mock(PackageStream.Checksum.class);
        when(md5.algorithm()).thenReturn(PackageStream.Algo.MD5);
        when(md5.asHex()).thenReturn(checksumMd5Val);
        PackageStream.Checksum sha = mock(PackageStream.Checksum.class);
        when(sha.algorithm()).thenReturn(PackageStream.Algo.SHA_256);
        when(sha.asHex()).thenReturn(checksumShaVal);
        PackageStream.Resource resource = mock(PackageStream.Resource.class);
        when(resource.name()).thenReturn(name);
        when(resource.checksum()).thenReturn(sha);

        underTest.addResource(resource);

        verify(resource, atLeastOnce()).checksum();

        Document result = DspaceDepositTestUtil.writeAndParseResults(dbf, underTest);

        Element file1 = (Element) result.getDocumentElement().getElementsByTagNameNS(METS_NS, METS_FILE).item(0);
        assertEquals(checksumShaVal, file1.getAttribute(METS_CHECKSUM));
    }

    /**
     * Tests that the proper DC elements are generated
     * @throws Exception
     */
    @Test
    public void testCreateDublinCoreMetadata() throws Exception {
        DepositMetadata md = mock(DepositMetadata.class);
        DepositMetadata.Manuscript msMd = mock(DepositMetadata.Manuscript.class);
        String msAbs = "This is the manuscript abstract";
        when(msMd.getMsAbstract()).thenReturn(msAbs);
        String msTitle = "This is the manuscript title.";
        when(msMd.getTitle()).thenReturn(msTitle);
        DepositMetadata.Article artMd = mock(DepositMetadata.Article.class);
        when(artMd.getTitle()).thenReturn("This is the article title");
        ZonedDateTime embargoLiftDate = ZonedDateTime.now().plusDays(10);
        when(artMd.getEmbargoLiftDate()).thenReturn(embargoLiftDate);
        String artDoi = "http://dx.doi.org/1234";
        when(artMd.getDoi()).thenReturn(URI.create(artDoi));
        when(md.getArticleMetadata()).thenReturn(artMd);
        when(md.getManuscriptMetadata()).thenReturn(msMd);
        DepositSubmission submission = mock(DepositSubmission.class);
        when(submission.getMetadata()).thenReturn(md);

        // Only Jane Doe (author) will appear in the citation.
        DepositMetadata.Person person1 = createMockPerson("Jane", null, "Doe", DepositMetadata.PERSON_TYPE.author);
        DepositMetadata.Person person2 = createMockPerson("John", null, "Doe", DepositMetadata.PERSON_TYPE.pi);
        List<DepositMetadata.Person> contributors = Arrays.asList(person1, person2);
        when(md.getPersons()).thenReturn(contributors);

        underTest.mapDmdSec(submission);

        Document result = DspaceDepositTestUtil.writeAndParseResults(dbf, underTest);

        Element dmdSec = (Element) result.getDocumentElement().getFirstChild();
        assertNotNull(dmdSec);
        assertEquals(METS_DMDSEC, dmdSec.getTagName());
        assertNotNull(dmdSec.getAttribute(METS_ID));
        assertNotNull(dmdSec.getAttribute(METS_GROUPID));

        Element mdWrap = (Element) dmdSec.getFirstChild();
        assertNotNull(mdWrap);
        assertNotNull(mdWrap.getAttribute(METS_ID));
        assertEquals(METS_MDTYPE_DC, mdWrap.getAttribute(METS_MDTYPE));

        assertEquals(METS_XMLDATA, ((Element)mdWrap.getFirstChild()).getTagName());

        Element qdc = (Element)mdWrap.getFirstChild().getFirstChild();
        assertEquals("qualifieddc", qdc.getTagName());

        assertEquals(2, qdc.getElementsByTagNameNS(DC_NS, DC_CONTRIBUTOR).getLength());
        Element contributor1 = (Element) qdc.getElementsByTagNameNS(DC_NS, DC_CONTRIBUTOR).item(0);
        Element contributor2 = (Element) qdc.getElementsByTagNameNS(DC_NS, DC_CONTRIBUTOR).item(1);
        assertEquals("Jane Doe", contributor1.getTextContent());
        assertEquals("John Doe", contributor2.getTextContent());

        assertEquals(msTitle, qdc.getElementsByTagNameNS(DC_NS, DC_TITLE).item(0).getTextContent());
        assertEquals(msAbs, qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_ABSTRACT).item(0).getTextContent());
        assertNotNull(qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_BIBLIOCITATION).item(0).getTextContent());

        // These tests were targeted at the Qualified version of the DC metadata
//        description = (Element) qdc.getElementsByTagNameNS(DC_NS, DC_DESCRIPTION).item(1);
//        assertTrue(description.getElementsByTagNameNS(DC_NS, DC_ABSTRACT).item(0).getTextContent().contains
//                (DateTimeFormatter.ISO_LOCAL_DATE.format(embargoLiftDate)));
//        assertTrue(qdc.getElementsByTagNameNS(DC_NS, DC_DESCRIPTION).item(0).getTextContent().contains
//                (DateTimeFormatter.ISO_LOCAL_DATE.format(embargoLiftDate)));
//        assertEquals(artDoi, qdc.getElementsByTagNameNS(DCTERMS_NS, DCT_HASVERSION).item(0).getTextContent());
    }

    /**
     * Submission metadata must carry a manuscript dc:title, or it gets spiked
     *
     * @throws Exception
     */
    @Test
    public void testMissingTitle() throws Exception {
        DepositMetadata md = mock(DepositMetadata.class);
        DepositMetadata.Manuscript msMd = mock(DepositMetadata.Manuscript.class);
        String msAbs = "This is the manuscript abstract";
        when(msMd.getMsAbstract()).thenReturn(msAbs);
        when(msMd.getTitle()).thenReturn(null); // no title, this should get spiked
        when(md.getManuscriptMetadata()).thenReturn(msMd);
        DepositSubmission submission = mock(DepositSubmission.class);
        when(submission.getMetadata()).thenReturn(md);

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("No title found");

        underTest.createDublinCoreMetadataDCMES(submission);
    }

    @Test
    public void testMissingAuthors() throws Exception {
        DepositMetadata md = mock(DepositMetadata.class);
        DepositMetadata.Manuscript msMd = mock(DepositMetadata.Manuscript.class);
        String msAbs = "This is the manuscript abstract";
        when(msMd.getMsAbstract()).thenReturn(msAbs);
        when(msMd.getTitle()).thenReturn("This is the manuscript title");
        when(md.getManuscriptMetadata()).thenReturn(msMd);
        DepositSubmission submission = mock(DepositSubmission.class);
        when(submission.getMetadata()).thenReturn(md);
        // Submission has no authors

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("No authors found");

        underTest.createDublinCoreMetadataDCMES(submission);
    }

    /**
     * Insures the proper dim metadata is written when an embargo date is present on article metadata.
     * Insures that the dim metadata are in a different group (different groupid) than the dc metadata.
     *
     * Test is currently disabled, as the embargo date is not included in the DCMES metadata.
     *
     * @throws Exception
     */
    //@Test
    public void testCreateEmbargoMd() throws Exception {
        DepositMetadata md = mock(DepositMetadata.class);
        DepositMetadata.Manuscript msMd = mock(DepositMetadata.Manuscript.class);
        String msTitle = "This is the manuscript title.";
        when(msMd.getTitle()).thenReturn(msTitle);
        DepositMetadata.Article artMd = mock(DepositMetadata.Article.class);
        ZonedDateTime embargoLiftDate = ZonedDateTime.now().plusDays(10);
        when(artMd.getEmbargoLiftDate()).thenReturn(embargoLiftDate);
        when(md.getArticleMetadata()).thenReturn(artMd);
        when(md.getManuscriptMetadata()).thenReturn(msMd);
        DepositSubmission submission = mock(DepositSubmission.class);
        when(submission.getMetadata()).thenReturn(md);
        DepositMetadata.Person person1 = createMockPerson("Jane", null, "Doe", DepositMetadata.PERSON_TYPE.author);
        when(md.getPersons()).thenReturn(Arrays.asList(person1));

        underTest.mapDmdSec(submission);

        Document result = DspaceDepositTestUtil.writeAndParseResults(dbf, underTest);
        NodeList dmdSecList = result.getElementsByTagNameNS(METS_NS, METS_DMDSEC);
        List<String> dmdSecGroupIds = new ArrayList<>();
        Element dim = null;
        String dmdSecGroupId = null;
        for (int i = 0; i < dmdSecList.getLength(); i++) {
            Element candidate = (Element) dmdSecList.item(i);
            dmdSecGroupId = candidate.getAttribute(METS_GROUPID);
            assertNotNull("Null '" + METS_GROUPID + "' for dmdSec", dmdSecGroupId);
            dmdSecGroupIds.add(dmdSecGroupId);
            final String name = candidate.getFirstChild().getFirstChild().getFirstChild().getLocalName();
            if (name.equals(DIM)) {
                dim = (Element)candidate.getFirstChild().getFirstChild().getFirstChild();
                assertNotNull(candidate.getAttribute(METS_ID));
            }
        }
        assertNotNull("Missing " + DIM + " element.", dim);

        // the dmdSecGroupId should be unique (other dmdSecs should not share the DIM group Id)
        String finalDmdSecGroupId = dmdSecGroupId;
        assertEquals(1, dmdSecGroupIds.stream()
                .filter(id -> id.equals(finalDmdSecGroupId)).collect(
                        Collectors.toList()).size());

        /*
        <dim:field element="embargo" mdschema="local" qualifier="lift">2018-04-29</dim:field>
        <dim:field element="embargo" mdschema="local" qualifier="terms">2018-04-29</dim:field>
        <dim:field element="description" mdschema="dc" qualifier="provenance">Submission published under an embargo,
            which will last until 2018-04-29</dim:field>
         */

        List<Element> dimFields = DepositTestUtil.asList(dim.getElementsByTagNameNS(DIM_NS, DIM_FIELD));
        assertEquals(3, dimFields.size());

        assertTrue(dimFields.stream().anyMatch(
                e -> e.getAttribute(DIM_ELEMENT).equals(DIM_EMBARGO) &&
                        e.getAttribute(DIM_MDSCHEMA).equals(DIM_MDSCHEMA_LOCAL) &&
                        e.getAttribute(DIM_QUALIFIER).equals(DIM_EMBARGO_LIFT)));

        assertTrue(dimFields.stream().anyMatch(
                e -> e.getAttribute(DIM_ELEMENT).equals(DIM_EMBARGO) &&
                        e.getAttribute(DIM_MDSCHEMA).equals(DIM_MDSCHEMA_LOCAL) &&
                        e.getAttribute(DIM_QUALIFIER).equals(DIM_EMBARGO_TERMS)));

        assertTrue(dimFields.stream().anyMatch(
                e -> e.getAttribute(DIM_ELEMENT).equals(DIM_DESCRIPTION) &&
                        e.getAttribute(DIM_MDSCHEMA).equals(DIM_MDSCHEMA_DC) &&
                        e.getAttribute(DIM_QUALIFIER).equals(DIM_PROVENANCE)));
    }

    /**
     * StructMap should contain a single {@code <div>}, with DMDID attribute linking to each DmdSec provided.
     * StructMap should have an {@code <fptr>} for every file in FileSec (fptr FILEID should link to File ID).
     * First <fptr> should be the "primary" file for the submission (i.e. the manuscript).
     *
     * @throws Exception
     */
    @Test
    public void testMapStructMap() throws Exception {
        DepositSubmission submission = mock(DepositSubmission.class);
        String dmdId1 = UUID.randomUUID().toString();
        String dmdId2 = UUID.randomUUID().toString();
        List<String> dmdIds = Arrays.asList(dmdId1, dmdId2);
        DmdSec dmdSec1 = mock(DmdSec.class);
        DmdSec dmdSec2 = mock(DmdSec.class);
        when(dmdSec1.getID()).thenReturn(dmdId1);
        when(dmdSec2.getID()).thenReturn(dmdId2);
        FileSec fileSec = mock(FileSec.class);
        FileGrp fileGrp = mock(FileGrp.class);
        when(fileSec.getFileGrpByUse(DspaceMetadataDomWriter.CONTENT_USE)).thenReturn(Collections.singletonList
                (fileGrp));

        String fileId1 = UUID.randomUUID().toString();
        String fileId2 = UUID.randomUUID().toString();
        String fileId3 = UUID.randomUUID().toString();
        File file1 = mock(File.class);
        File file2 = mock(File.class);
        File file3 = mock(File.class);
        when(file1.getID()).thenReturn(fileId1);
        when(file2.getID()).thenReturn(fileId2);
        when(file3.getID()).thenReturn(fileId3);
        List<File> files = Arrays.asList(file1, file2, file3);
        when(fileGrp.getFiles()).thenReturn(files);

        underTest.mapStructMap(submission, Arrays.asList(dmdSec1, dmdSec2), fileSec);

        Document result = DspaceDepositTestUtil.writeAndParseResults(dbf, underTest);

        List<Element> structMaps = DepositTestUtil.asList(result.getElementsByTagNameNS(METS_NS, METS_STRUCTMAP));
        assertEquals(1, structMaps.size());
        Element structMap = structMaps.get(0);

        // StructMap contains a single div
        assertEquals(1, structMap.getElementsByTagNameNS(METS_NS, METS_DIV).getLength());
        assertEquals(METS_DIV, structMap.getFirstChild().getLocalName());
        Element div = (Element) structMap.getFirstChild();
        // div has DMDIDs for each DmdSec
        Arrays.stream(div.getAttribute(METS_DMDID).split(" "))
                .forEach(dmdId -> assertTrue(dmdIds.stream().anyMatch(candidate -> candidate.equals(dmdId))));

        // fptr for each File, each fptr links to a File
        List<Element> fptrs = DepositTestUtil.asList(div.getElementsByTagNameNS(METS_NS, METS_FPTR));
        assertEquals(files.size(), fptrs.size());
        fptrs.forEach(fptr -> assertTrue(files
                .stream()
                .map(File::getID)
                .anyMatch(fileid -> fileid.equals(fptr.getAttribute(METS_FILEID)))));
    }

}