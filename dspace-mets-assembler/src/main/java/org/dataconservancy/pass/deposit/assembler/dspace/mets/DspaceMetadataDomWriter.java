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

import au.edu.apsr.mtk.base.AmdSec;
import au.edu.apsr.mtk.base.Constants;
import au.edu.apsr.mtk.base.Div;
import au.edu.apsr.mtk.base.DmdSec;
import au.edu.apsr.mtk.base.FLocat;
import au.edu.apsr.mtk.base.File;
import au.edu.apsr.mtk.base.FileGrp;
import au.edu.apsr.mtk.base.FileSec;
import au.edu.apsr.mtk.base.Fptr;
import au.edu.apsr.mtk.base.METS;
import au.edu.apsr.mtk.base.METSException;
import au.edu.apsr.mtk.base.METSWrapper;
import au.edu.apsr.mtk.base.MdSec;
import au.edu.apsr.mtk.base.MdWrap;
import au.edu.apsr.mtk.base.SourceMD;
import au.edu.apsr.mtk.base.StructMap;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositMetadata;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.assembler.dspace.mets.MetsMdType.DC;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.MetsMdType.OTHER;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCTERMS_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCT_ABSTRACT;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DCT_HASVERSION;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_CONTRIBUTOR;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.DC_DESCRIPTION;
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
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.NS_TO_PREFIX_MAP;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XSI_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XSI_NS_PREFIX;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DspaceMetadataDomWriter {

    static final String METS_ID = "DSPACE-METS-SWORD";

    static final String METS_OBJ_ID = "DSPACE-METS-SWORD-OBJ";

    static final String METS_DSPACE_LABEL = "DSpace SWORD Item";

    static final String METS_DSPACE_PROFILE = "DSpace METS SIP Profile 1.0";

    static final String CONTENT_USE = "CONTENT";

    static final String LOCTYPE_URL = "URL";

    /**
     * Package-private for unit testing
     */
    Document metsDocument;

    private DocumentBuilderFactory dbf;

    private METS mets;

    @Autowired
    DspaceMetadataDomWriter(DocumentBuilderFactory dbf) {
        try {
            this.dbf = dbf;
            this.metsDocument = dbf.newDocumentBuilder().newDocument();
            Element root = metsDocument.createElementNS(Constants.NS_METS, Constants.ELEMENT_METS);
            metsDocument.appendChild(root);
            this.mets = new METS(metsDocument);
            this.mets.setID(mintId());
            this.mets.setProfile(METS_DSPACE_PROFILE);
            this.mets.setLabel(METS_DSPACE_LABEL);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    void write(OutputStream out) {
        METSWrapper wrapper = null;
        try {
            wrapper = new METSWrapper(metsDocument);
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        wrapper.write(out);
    }

    void addSubmission(DepositSubmission submission) {
        try {
            if (getFileGrpByUse(CONTENT_USE) == null || getFileGrpByUse(CONTENT_USE).getFiles().isEmpty()) {
                throw new IllegalStateException("No <fileGrp USE=\"" + CONTENT_USE + "\"> element was found, or was" +
                        " empty.  Resources must be added before submissions.  Has addResource(Resource) been called?");
            }
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        try {
            mapStructMap(submission, mapDmdSec(submission), mets.getFileSec());
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    StructMap mapStructMap(DepositSubmission submission, Collection<DmdSec> dmdSec, FileSec fileSec) {
        StructMap structMap = null;
        try {
            structMap = this.mets.newStructMap();
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        structMap.setID(mintId());
        structMap.setLabel("DSpace CONTENT bundle structure");

        Div itemDiv = null;
        try {
            itemDiv = structMap.newDiv();
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        itemDiv.setID(mintId());
        itemDiv.setLabel("DSpace Item Div");
        itemDiv.setDmdID(dmdSec.stream().map(MdSec::getID).collect(Collectors.joining(" ")));

        Div finalItemDiv = itemDiv;
        try {
            fileSec.getFileGrpByUse(CONTENT_USE)
                    .stream()
                    .flatMap(fileGrp -> {
                        try {
                            return fileGrp.getFiles().stream();
                        } catch (METSException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    })
                    .forEach(f -> {
                        Fptr filePtr = null;
                        try {
                            filePtr = finalItemDiv.newFptr();
                        } catch (METSException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                        filePtr.setID(mintId());
                        filePtr.setFileID(f.getID());
                        finalItemDiv.addFptr(filePtr);
                    });
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        this.mets.addStructMap(structMap);
        structMap.addDiv(itemDiv);
        return structMap;
    }

    Collection<DmdSec> mapDmdSec(DepositSubmission submission) throws METSException {
        List<DmdSec> result = new ArrayList<>();
        Element dcRecord = createDublinCoreMetadata(submission);
        DmdSec dcDmdSec = getDmdSec(null);   // creates a new DmdSec

        try {
            MdWrap dcMdWrap = dcDmdSec.newMdWrap();
            dcMdWrap.setID(mintId());
            dcMdWrap.setMDType(DC.getType());
            dcMdWrap.setXmlData(dcRecord);
            dcDmdSec.setMdWrap(dcMdWrap);
            dcDmdSec.setGroupID(mintId());
            dcDmdSec.setMdWrap(dcMdWrap);

        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        mets.addDmdSec(dcDmdSec);
        result.add(dcDmdSec);

        if (submission.getMetadata().getArticleMetadata().getEmbargoLiftDate() != null) {
            Element dimRecord = createDimMetadataForEmbargo(submission);
            DmdSec dimDmdSec = getDmdSec(null);  // creates a new DmdSec

            try {
                MdWrap dimMdWrap = dimDmdSec.newMdWrap();
                dimMdWrap.setID(mintId());
                dimMdWrap.setMDType(OTHER.getType());
                dimMdWrap.setOtherMDType("DIM");
                dimMdWrap.setXmlData(dimRecord);
                dimDmdSec.setMdWrap(dimMdWrap);
                dimDmdSec.setGroupID(mintId());
                dimDmdSec.setMdWrap(dimMdWrap);
            } catch (METSException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            mets.addDmdSec(dimDmdSec);
            result.add(dimDmdSec);
        }

        return result;
    }

    private Element createDimMetadataForEmbargo(DepositSubmission submission) {
        Document dimDocument = newDocument();
        Element dimRoot = newRootElement(dimDocument, DIM_NS, asQname(DIM_NS, DIM));
        dimDocument.appendChild(dimRoot);

        /*
        local.embargo.terms: the date upon which the embargo will expire in the format of ‘yyyy-MM-dd’
        local.embargo.lift: the date upon which the embargo will expire in the format of ‘yyyy-MM-dd’
        dc.description: Submission published under an embargo, which will last until yyyy-MM-dd
        dc.description.provenance: Submission published under an embargo, which will last until yyyy-MM-dd
         */


        ZonedDateTime embargoLiftDate = submission.getMetadata().getArticleMetadata().getEmbargoLiftDate();
        if (embargoLiftDate == null) {
            throw new NullPointerException("Embargo lift date should not be null.");
        }
        String formattedDate = embargoLiftDate.format(DateTimeFormatter.ISO_LOCAL_DATE);


        // <dim:field mdschema="local" element="embargo" qualifier="terms">
        Element localEmbargoTerms = dimDocument.createElementNS(DIM_NS, asQname(DIM_NS, DIM_FIELD));
        localEmbargoTerms.setAttribute(DIM_MDSCHEMA, DIM_MDSCHEMA_LOCAL);
        localEmbargoTerms.setAttribute(DIM_ELEMENT, DIM_EMBARGO);
        localEmbargoTerms.setAttribute(DIM_QUALIFIER, DIM_EMBARGO_TERMS);
        localEmbargoTerms.setTextContent(formattedDate);

        // <dim:field mdschema="local" element="embargo" qualifier="lift">
        Element localEmbargoLift = dimDocument.createElementNS(DIM_NS, asQname(DIM_NS, DIM_FIELD));
        localEmbargoLift.setAttribute(DIM_MDSCHEMA, DIM_MDSCHEMA_LOCAL);
        localEmbargoLift.setAttribute(DIM_ELEMENT, DIM_EMBARGO);
        localEmbargoLift.setAttribute(DIM_QUALIFIER, DIM_EMBARGO_LIFT);
        localEmbargoLift.setTextContent(formattedDate);

        // <dim:field mdschema="dc" element="description" qualifier="provenance">
        Element dcDescProv = dimDocument.createElementNS(DIM_NS, asQname(DIM_NS, DIM_FIELD));
        dcDescProv.setAttribute(DIM_MDSCHEMA, DIM_MDSCHEMA_DC);
        dcDescProv.setAttribute(DIM_ELEMENT, DIM_DESCRIPTION);
        dcDescProv.setAttribute(DIM_QUALIFIER, DIM_PROVENANCE);
        dcDescProv.setTextContent(String.format("Submission published under an embargo, which will last until %s",
                formattedDate));

        dimRoot.appendChild(localEmbargoLift);
        dimRoot.appendChild(localEmbargoTerms);
        dimRoot.appendChild(dcDescProv);

        return dimRoot;
    }

    /**
     * Creates the Dublin Core metadata from the submission.  Includes:
     * <ul>
     *     <li>dc:contributor for each Person associated with the Manuscript</li>
     *     <li>dc:title for the Manuscript</li>
     *     <li>dcterms:hasVersion for the DOI of the Article</li>
     *     <li>dcterms:abstract for the Manuscript</li>
     *     <li>dc:description with the embargo lift date, if an embargo is on the Article</li>
     * </ul>
     * The returned Element will have a name {@code qualifieddc}, which has "special" meaning to DSpace.
     * <p>
     * Package-private for unit testing
     * </p>
     * @param submission
     * @return
     */
    Element createDublinCoreMetadata(DepositSubmission submission) {
        Document dcDocument = newDocument();

        // Root <record> element
        Element record = newRootElement(dcDocument, DCTERMS_NS, "qualifieddc");

        dcDocument.appendChild(record);

        // Attach a <dc:contributor> for each Person associated with the submission to the Manuscript metadata
        DepositMetadata nimsMd = submission.getMetadata();
        DepositMetadata.Manuscript manuscriptMd = nimsMd.getManuscriptMetadata();
        DepositMetadata.Article articleMd = nimsMd.getArticleMetadata();

        nimsMd.getPersons().forEach(p -> {
            Element contributor = dcDocument.createElementNS(DC_NS, asQname(DC_NS, DC_CONTRIBUTOR));
            if (p.getFirstName() != null && p.getLastName() != null) {
                if (p.getMiddleName() != null) {
                    contributor.setTextContent(String.format("%s %s %s", p.getFirstName(), p.getMiddleName(), p
                            .getLastName()));
                } else {
                    contributor.setTextContent(String.format("%s %s", p.getFirstName(), p.getLastName()));
                }
            }

            record.appendChild(contributor);
        });

        // Attach a <dc:title> for the Manuscript title
        if (manuscriptMd.getTitle() != null) {
            Element titleElement = dcDocument.createElementNS(DC_NS, asQname(DC_NS, DC_TITLE));
            titleElement.setTextContent(manuscriptMd.getTitle());
            record.appendChild(titleElement);
        } else {
            throw new RuntimeException("No title found in the NIHMS manuscript metadata!");
        }

        // Attach a <dcterms:hasVersion> pointing to the published Article DOI
        if (articleMd.getDoi() != null) {
            Comment c = dcDocument.createComment("This DOI points to the published version of the manuscript, available after any embargo period has been satisfied.");
            record.appendChild(c);
            Element hasVersion = dcDocument.createElementNS(DCTERMS_NS, asQname(DCTERMS_NS, DCT_HASVERSION));
            hasVersion.setTextContent(articleMd.getDoi().toString());
            record.appendChild(hasVersion);
        }

        // Attach a <dcterms:abstract> for the manuscript, if one was provided
        if (manuscriptMd.getMsAbstract() != null) {
            Element msAbstractElement = dcDocument.createElementNS(DCTERMS_NS, asQname(DCTERMS_NS, DCT_ABSTRACT));
            msAbstractElement.setTextContent(manuscriptMd.getMsAbstract());
            record.appendChild(msAbstractElement);
        }

        // TODO: Journal metadata
        // ...

        // TODO: Article metadata
        // <dc:identifier> DOI for the published article
        // <dc:available> date available if there is an embargo on the published article

        // Add a description of the embargo, if one is present
        // "Submission published under an embargo, which will last until yyyy-MM-dd"
        if (articleMd.getEmbargoLiftDate() != null) {
            Element dcEmbargoDesc = dcDocument.createElementNS(DC_NS, asQname(DC_NS, DC_DESCRIPTION));
            dcEmbargoDesc.setTextContent(String.format("Submission published under an embargo, which will last until %s",
                    articleMd.getEmbargoLiftDate().format(DateTimeFormatter.ISO_LOCAL_DATE)));
            record.appendChild(dcEmbargoDesc);
        }

        return record;
    }

    /**
     * Add a {@link PackageStream.Resource resource} to the DOM.  This creates a METS {@code File} with a {@code FLocat}
     * for the resource.  The resource's primary checksum, size, mime type, and name are included in the METS DOM.
     * The {@code FLocat} will be a URL type, using the resource name as the location.
     *
     * @param resource the package resource to be represented in the DOM
     */
    void addResource(PackageStream.Resource resource) {
        File resourceFile = null;
        try {
            resourceFile = createFile(CONTENT_USE);
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (resource.checksum() != null) {
            resourceFile.setChecksum(resource.checksum().asHex());
            resourceFile.setChecksumType(resource.checksum().algorithm().name());
        }

        if (resource.sizeBytes() > -1) {
            resourceFile.setSize(resource.sizeBytes());
        }

        if (resource.mimeType() != null && resource.mimeType().trim().length() > 0) {
            resourceFile.setMIMEType(resource.mimeType());
        }

        FLocat locat = null;
        try {
            locat = resourceFile.newFLocat();
            resourceFile.addFLocat(locat);
        } catch (METSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        locat.setID(mintId());
        locat.setHref(resource.name());
        locat.setLocType(LOCTYPE_URL);
    }

    /**
     * Creates a new element in the supplied document, using the supplied namespace and qualified name.  This method
     * adds {@code xmlns} attributes for each namespace->prefix mappling in {@link XMLConstants#NS_TO_PREFIX_MAP}
     * <p>
     * Package-private for unit testing
     * </p>
     *
     * @param doc
     * @param namespace
     * @param qualifiedName
     * @return
     */
    Element newRootElement(Document doc, String namespace, String qualifiedName) {
        Element root = doc.createElementNS(namespace, qualifiedName);
        root.setAttribute("xmlns:" + XSI_NS_PREFIX, XSI_NS);
        NS_TO_PREFIX_MAP.keySet().stream().collect(Collectors.toMap(NS_TO_PREFIX_MAP::get, (key) -> key)).entrySet()
                .stream().filter((entry) -> {
            // filter out the namespace prefix supplied by the qualifiedName parameter, as the writer will add that
            // prefix in automatically
            if (qualifiedName.contains(":")) {
                String prefix = qualifiedName.substring(0, qualifiedName.indexOf(":"));
                if (prefix.equals(entry.getKey())) {
                    return false;
                }
            }
            return true;
        }).forEach((entry) -> root.setAttribute("xmlns:" + entry.getKey(), entry.getValue()));
        return root;
    }

    /**
     * Creates a new {@link Document} from the {@link #dbf DocumentBuilderFactory}
     *
     * @return a new {@link Document}
     */
    private Document newDocument() {
        Document dcDocument = null;
        try {
            DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
            dcDocument = documentBuilder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return dcDocument;
    }

    private File createFile(String use) throws METSException {
        FileGrp fileGrp = getFileGrpByUse(use);
        File file = fileGrp.newFile();
        fileGrp.addFile(file);
        file.setID(mintId());
        return file;
    }

    /**
     * Obtains the {@code <fileGrp>} element with a {@code USE} equal to the supplied {@code use} value.  If the element
     * does not exist, it is created and assigned an identifier.
     *
     * @param use the content use for the {@code FileGrp}
     * @return the {@code FileGrp} with a {@code USE} equal to {@code use}
     */
    private FileGrp getFileGrpByUse(String use) throws METSException {
        List<FileGrp> fileGroups = getFileSec().getFileGrpByUse(use);
        if (fileGroups == null || fileGroups.isEmpty()) {
            return createFileGrp(use);
        }

        return fileGroups.get(0);
    }

    /**
     * Creates the {@code <fileGrp>} element with a {@code USE} equal to the supplied {@code use} value, and assigns it
     * an identifier.
     *
     * @param use the content use for the {@code FileGrp}
     * @return the {@code FileGrp} with a {@code USE} equal to {@code use}
     */
    private FileGrp createFileGrp(String use) throws METSException {
        FileSec fileSec = getFileSec();
        FileGrp fileGrp = fileSec.newFileGrp();
        fileSec.addFileGrp(fileGrp);
        fileGrp.setID(mintId());
        fileGrp.setUse(use);
        return fileGrp;
    }

    /**
     * Obtains the only {@code <fileSec>} element from the METS document.  If the element does not exist, it is created
     * and assigned an identifier.
     *
     * @return the {@code FileSec} for the current METS document
     */
    private FileSec getFileSec() throws METSException {
        FileSec fileSec = mets.getFileSec();
        if (fileSec == null) {
            return createFileSec();
        }

        return fileSec;
    }

    /**
     * Creates a new {@code <fileSec>} element from the METS document and assigns it an identifier.
     *
     * @return the newly created {@code FileSec} for the current METS document
     */
    private FileSec createFileSec() throws METSException {
        FileSec fs = mets.newFileSec();
        mets.setFileSec(fs);
        fs.setID(mintId());
        return fs;
    }

    private SourceMD getSourceMd(String id) throws METSException {
        if (id == null) {
            return createSourceMd();
        }

        Optional<AmdSec> amdSec = mets
                .getAmdSecs()
                .stream()
                .filter(candidateAmdSec -> candidateAmdSec.getSourceMD(id) != null)
                .findAny();

        if (amdSec.isPresent()) {
            return amdSec.get().getSourceMD(id);
        }

        throw new RuntimeException("SourceMD with id '" + id + "' not found.");
    }

    private SourceMD createSourceMd() throws METSException {
        AmdSec amdSec = getAmdSec();
        SourceMD sourceMD = amdSec.newSourceMD();
        sourceMD.setID(mintId());
        amdSec.addSourceMD(sourceMD);
        return sourceMD;
    }

    private AmdSec getAmdSec() throws METSException {
        if (mets.getAmdSecs() == null || mets.getAmdSecs().isEmpty()) {
            return createAmdSec();
        }

        return mets.getAmdSecs().get(0);
    }

    private AmdSec createAmdSec() throws METSException {
        AmdSec as = mets.newAmdSec();
        mets.addAmdSec(as);
        as.setID(mintId());
        return as;
    }

    /**
     * Obtains the specified {@code <dmdSec>}, or creates a new {@code <dmdSec>} if {@code id} is {@code null}.
     *
     * @param id the identifier of the {@code <dmdSec>} to retrieve, or {@code null} to create a new {@code <dmdSec>}
     * @return the {@code <dmdSec>}
     * @throws RuntimeException if the {@code <dmdSec>} specified by {@code id} does not exist
     */
    private DmdSec getDmdSec(String id) throws METSException {
        if (id == null) {
            return createDmdSec();
        }

        DmdSec dmdSec = null;
        if ((dmdSec = mets.getDmdSec(id)) == null) {
            throw new RuntimeException("DmdSec with id '" + id + "' not found.");
        }

        return dmdSec;
    }

    /**
     * Creates a new {@code <dmdSec>} element, gives it an identifier, and returns.
     *
     * @return a new {@code <dmdSec>} with an auto-generated id
     */
    private DmdSec createDmdSec() throws METSException {
        DmdSec ds = mets.newDmdSec();
        ds.setID(mintId());
        return ds;
    }

    /**
     * Mints a unique, opaque, string identifier, suitable for identifying and linking between elements in a METS
     * document.
     *
     * @return an identifier
     */
    private static String mintId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns the qualified form of {@code elementName} after mapping the {@code namespace} to a prefix.
     * if {@code elementName} is already in a qualified form (e.g. contains a {@code :}), it is return unmodified.
     *
     * @param namespace the namespace for {@code elementName}
     * @param elementName the element name, which may already be qualified
     * @return the qualified name for {@code elementName}
     * @throws IllegalStateException if a {@code namespace} for which there is no prefix mapping is encountered
     */
    private static String asQname(String namespace, String elementName) {
        if (elementName.contains(":")) {
            return elementName;
        }
        if (!NS_TO_PREFIX_MAP.containsKey(namespace)) {
            throw new IllegalStateException("Missing prefix mapping for namespace '" + namespace + "'");
        }
        return String.format("%s:%s", NS_TO_PREFIX_MAP.get(namespace), elementName);
    }
}
