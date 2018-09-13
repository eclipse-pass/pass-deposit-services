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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class XMLConstants {

    /**
     * Dublin Core Namespace
     */
    static final String DC_NS = "http://purl.org/dc/elements/1.1/";

    static final String DC_NS_PREFIX = "dc";

    /**
     * DC Terms Namespace
     */
    static final String DCTERMS_NS = "http://purl.org/dc/terms/";

    static final String DCTERMS_NS_PREFIX = "dcterms";

    /**
     * DSpace Internal Metadata Namespace (dim)
     */
    static final String DIM_NS = "http://www.dspace.org/xmlns/dspace/dim";

    static final String DIM_NS_PREFIX = "dim";

    /**
     * XLink Namespace
     */
    static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    static final String XLINK_PREFIX = "xlink";

    /**
     * METS Namespace
     */
    static final String METS_NS = "http://www.loc.gov/METS/";

    static final Map<String, String> NS_TO_PREFIX_MAP = new HashMap<String, String>() {
        {
            put(DCTERMS_NS, DCTERMS_NS_PREFIX);
            put(DC_NS, DC_NS_PREFIX);
            put(DIM_NS, DIM_NS_PREFIX);
            put(XLINK_NS, XLINK_PREFIX);
        }
    };

    /**
     * XML Schema Instance Namespace
     */
    static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    static final String XSI_NS_PREFIX = "xsi";

    static final String XSI_TYPE = XSI_NS_PREFIX + ":type";

    /*
     * See http://dublincore.org/documents/dces/
     */

    static final String DC_TITLE = "title";

    static final String DC_CONTRIBUTOR = "contributor";

    static final String DC_COVERAGE = "coverage";

    static final String DC_CREATOR = "creator";

    static final String DC_DATE = "date";

    static final String DC_FORMAT = "format";

    static final String DC_LANGUAGE = "language";

    static final String DC_RELATION = "relation";

    static final String DC_SOURCE = "source";

    static final String DC_TYPE = "type";

    static final String DC_PUBLISHER = "publisher";

    static final String DC_DESCRIPTION = "description";

    static final String DC_ABSTRACT = "abstract";

    static final String DC_IDENTIFIER = "identifier";

    static final String DC_SUBJECT = "subject";

    static final String DC_RIGHTS = "rights";

    static final String DC_CITATION = "citation";


    /*
     * See http://dublincore.org/documents/dcmi-terms/
     */

    static final String DCT_ABSTRACT = "abstract";

    static final String DCT_ACCESSRIGHTS = "accessRights";

    static final String DCT_ACCRUALMETHOD = "accrualMethod";

    static final String DCT_ACCRUALPERIODICITY = "accrualPeriodicity";

    static final String DCT_ACCRUALPOLICY = "accrualPolicy";

    static final String DCT_ALT = "alternative";

    static final String DCT_AUDIENCE = "audience";

    static final String DCT_AVAILABLE = "available";

    static final String DCT_BIBLIOCITATION = "bibliographicCitation";

    static final String DCT_CONFORMSTO = "conformsTo";

    static final String DCT_CONTRIBUTOR = "contributor";

    static final String DCT_COVERAGE = "coverage";

    static final String DCT_CREATED = "created";

    static final String DCT_CREATOR = "creator";

    static final String DCT_DATE = "date";

    static final String DCT_DATEACCEPTED = "dateAccepted";

    static final String DCT_DATECOPYRIGHTED = "dateCopyrighted";

    static final String DCT_DATESUBMITTED = "dateSubmitted";

    static final String DCT_DESCRIPTION = "description";

    static final String DCT_EDUCATIONLEVEL = "educationLevel";

    static final String DCT_EXTENT = "extent";

    static final String DCT_FORMAT = "format";

    static final String DCT_HASFORMAT = "hasFormat";

    static final String DCT_HASPART = "hasPart";

    static final String DCT_HASVERSION = "hasVersion";

    static final String DCT_IDENTIFIER = "identifier";

    static final String DCT_INSTMETHOD = "instructionalMethod";

    static final String DCT_ISFORMATOF = "isFormatOf";

    static final String DCT_ISPARTOF = "isPartOf";

    static final String DCT_ISREFBY = "isReferencedBy";

    static final String DCT_ISREPLBY = "isReplacedBy";

    static final String DCT_ISREQBY = "isRequiredBy";

    static final String DCT_ISSUED = "issued";

    static final String DCT_ISVERSIONOF = "isVersionOf";

    static final String DCT_LANGUAGE = "language";

    static final String DCT_LICENSE = "license";

    static final String DCT_MEDIATOR = "mediator";

    static final String DCT_MEDIUM = "medium";

    static final String DCT_MODIFIED = "modified";

    static final String DCT_PROV = "provenance";

    static final String DCT_PUBLISHER = "publisher";

    static final String DCT_REFERENCES = "references";

    static final String DCT_RELATION = "relation";

    static final String DCT_REPLACES = "replaces";

    static final String DCT_REQUIRES = "requires";

    static final String DCT_RIGHTS = "rights";

    static final String DCT_RIGHTSHOLDER = "rightsHolder";

    static final String DCT_SOURCE = "source";

    static final String DCT_SPATIAL = "spatial";

    static final String DCT_SUBJECT = "subject";

    static final String DCT_TABLEOFCONTENTS = "tableOfContents";

    static final String DCT_TEMPORAL = "temporal";

    static final String DCT_TITLE = "title";

    static final String DCT_TYPE = "type";

    static final String DCT_VALID = "valid";

    static final String DCT_PERIOD = "Period";

    static final String DCT_URI = "URI";

    static final String DCT_W3CDTF = "W3CDTF";

    static final String DCT_IMT = "IMT";

    /*
     * DSpace Internal Metadata
     */

    static final String DIM_FIELD = "field";

    static final String DIM_MDSCHEMA = "mdschema";

    static final String DIM_ELEMENT = "element";

    static final String DIM_QUALIFIER = "qualifier";

    static final String DIM_MDSCHEMA_DC = "dc";

    static final String DIM_MDSCHEMA_LOCAL = "local";

    static final String DIM = "dim";

    static final String DIM_EMBARGO = "embargo";

    static final String DIM_EMBARGO_LIFT = "lift";

    static final String DIM_EMBARGO_TERMS = "terms";

    static final String DIM_PROVENANCE = "provenance";

    static final String DIM_DESCRIPTION = "description";

    /*
     * METS
     */

    static final String METS_PROFILE = "PROFILE";

    static final String METS_LABEL = "LABEL";

    static final String METS_ID = "ID";

    static final String METS_USE = "USE";

    static final String METS_CONTENT = "CONTENT";

    static final String METS_CHECKSUM = "CHECKSUM";

    static final String METS_CHECKSUM_TYPE = "CHECKSUMTYPE";

    static final String METS_CHECKSUM_TYPE_MD5 = "MD5";

    static final String METS_MIMETYPE = "MIMETYPE";

    static final String METS_SIZE = "SIZE";

    static final String METS_LOCTYPE = "LOCTYPE";

    static final String METS_LOCTYPE_URL = "URL";

    static final String METS_FILESEC = "fileSec";

    static final String METS_FILEGRP = "fileGrp";

    static final String METS_FILE = "file";

    static final String METS_FLOCAT = "FLocat";

    static final String METS_DMDSEC = "dmdSec";

    static final String METS_MDWRAP = "mdWrap";

    static final String METS_XMLDATA = "xmlData";

    static final String METS_GROUPID = "GROUPID";

    static final String METS_MDTYPE = "MDTYPE";

    static final String METS_MDTYPE_DC = "DC";

    static final String METS_MDTYPE_OTHER = "OTHER";

    static final String METS_OTHERMDTYPE = "OTHERMDTYPE";

    static final String METS_OTHERMDTYPE_TYPE = "DIM";

    static final String METS_STRUCTMAP = "structMap";

    static final String METS_DIV = "div";

    static final String METS_DMDID = "DMDID";

    static final String METS_FPTR = "fptr";

    static final String METS_FILEID = "FILEID";

    /*
     * XLink
     */

    static final String XLINK_HREF = "href";

}
