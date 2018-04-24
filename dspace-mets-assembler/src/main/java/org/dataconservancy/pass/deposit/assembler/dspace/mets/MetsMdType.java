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

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public enum MetsMdType {

    MARC("MARC", "any form of MARC record"),
    MODS("MODS", "metadata in the Library of Congress MODS format"),
    EAD("EAD", "Encoded Archival Description finding aid"),
    DC("DC", "Dublin Core"),
    NISOIMG("NISOIMG", "NISO Technical Metadata for Digital Still Images"),
    LCAV("LC-AV", "technical metadata specified in the Library of Congress A/V prototyping project"),
    VRA("VRA", "Visual Resources Association Core"),
    TEIHDR("TEIHDR", "Text Encoding Initiative Header"),
    DDI("DDI", "Data Documentation Initiative"),
    FGDC("FGDC", "Federal Geographic Data Committee metadata"),
    LOM("LOM", "Learning Object Model"),
    PREMIS("PREMIS", "PREservation Metadata: Implementation Strategies"),
    PREMIS_OBJECT("PREMIS:OBJECT", "PREMIS Object entity"),
    PREMIS_AGENT("PREMIS:AGENT", "PREMIS Agent entity"),
    PREMIS_RIGHTS("PREMIS:RIGHTS", "PREMIS Rights entity"),
    PREMIS_EVENT("PREMIS:EVENT", "PREMIS Event entity"),
    TEXTMD("TEXTMD", "textMD Technical metadata for text"),
    METSRIGHTS("METSRIGHTS", "Rights Declaration Schema"),
    ISO_19115_2003_NAP("ISO 19115:2003 NAP", "North American Profile of ISO 19115:2003 descriptive metadata"),
    EACCPF("EAC-CPF", "Encoded Archival Context - Corporate Bodies, Persons, and Families"),
    LIDO("LIDO", "Lightweight Information Describing Objects"),
    OTHER("OTHER", "metadata in a format not specified above");

    private String type;
    private String desc;

    MetsMdType(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

}
