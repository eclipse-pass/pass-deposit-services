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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.dataconservancy.nihms.model.NihmsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to serialize a NihmsMetadata object to XML, and deserialize XML to NihmsMetadata
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsMetadataConverter implements Converter {

    private static final String N_SUBMIT = "nihms-submit"; //
    private static final String N_MANUSCRIPT = "manuscript";
    private static final String N_ID = "id";
    private static final String N_PUB_PDF = "publisher_pdf";
    private static final String N_SHOW_PUB_PDF = "show_publisher_pdf";
    private static final String N_EMBARGO = "embargo";
    private static final String N_PUB_MED_ID = "pmid";
    private static final String N_PUB_MED_CENTRAL_ID = "pmcid";
    private static final String N_MANUSCRIPT_URL = "href";
    private static final String N_DOI = "doi";
    private static final String N_JOURNAL ="journal-meta";
    private static final String N_JOURNAL_ID = "journal-id";
    private static final String N_JOURNAL_ID_TYPE = "journal-id-type";
    private static final String N_ISSN = "issn";
    private static final String N_PUB_TYPE = "pub-type";
    private static final String N_JOURNAL_TITLE = "journal-title";
    private static final String N_MANUSCRIPT_TITLE = "title";
    private static final String N_CONTACTS = "contacts";
    private static final String N_PERSON = "person";
    private static final String N_FIRST_NAME = "fname";
    private static final String N_MIDDLE_NAME = "mname";
    private static final String N_LAST_NAME = "lname";
    private static final String N_EMAIL = "email";
    private static final String N_PI = "pi";
    private static final String N_CORRESPONDING_PI = "corrpi";
    private static final String N_AUTHOR = "author";

    private static final Logger LOG = LoggerFactory.getLogger(NihmsMetadataConverter.class);

    public boolean canConvert(Class clazz) {
        return NihmsMetadata.class == clazz;
    }

    public void marshal(Object value, HierarchicalStreamWriter writer,
                        MarshallingContext context) {
        NihmsMetadata metadata = (NihmsMetadata) value;

        //process manuscript element (except, strangely, for title, which we do after journal)
        NihmsMetadata.Manuscript manuscript = metadata.getManuscriptMetadata();
        writer.startNode(N_MANUSCRIPT);
        if (manuscript.getNihmsId() != null) {
            writer.addAttribute(N_ID, manuscript.getNihmsId());
        }

        //primitive types
        writer.addAttribute(N_PUB_PDF, booleanConvert(manuscript.isPublisherPdf()));
        writer.addAttribute(N_SHOW_PUB_PDF, booleanConvert(manuscript.isShowPublisherPdf()));
        writer.addAttribute(N_EMBARGO, String.valueOf(manuscript.getRelativeEmbargoPeriodMonths()));

        if (manuscript.getPubmedId() != null) {
            writer.addAttribute(N_PUB_MED_ID, manuscript.getPubmedId());
        }

        if (manuscript.getPubmedCentralId() != null) {
            writer.addAttribute(N_PUB_MED_CENTRAL_ID, manuscript.getPubmedCentralId());
        }
        if (manuscript.getManuscriptUrl() != null) {
            writer.addAttribute(N_MANUSCRIPT_URL, manuscript.getManuscriptUrl().toString());
        }
        if (manuscript.getDoi() != null) {
            writer.addAttribute(N_DOI, manuscript.getDoi().toString());
        }
        writer.endNode(); //end manuscript

        //process journal
        NihmsMetadata.Journal journal = metadata.getJournalMetadata();
        writer.startNode(N_JOURNAL);
        if (journal.getJournalId() != null) {
            writer.startNode(N_JOURNAL_ID);
            if (journal.getJournalType() != null) {
                writer.addAttribute(N_JOURNAL_ID_TYPE, journal.getJournalType());
            }
            writer.setValue(journal.getJournalId());
            writer.endNode();
        }

        if (journal.getIssn() != null) {
            writer.startNode(N_ISSN);
            if (journal.getPubType() != null) {
                writer.addAttribute(N_PUB_TYPE, journal.getPubType().toString());
            }
            writer.setValue(journal.getIssn());
            writer.endNode();
        }
        if (journal.getJournalTitle() != null) {
            writer.startNode(N_JOURNAL_TITLE);
            writer.setValue(journal.getJournalTitle());
            writer.endNode();
        }
        writer.endNode(); //end journal-meta

        //now process full manuscript title
        if (manuscript.getTitle() != null) {
            writer.startNode(N_MANUSCRIPT_TITLE);
            writer.setValue(manuscript.getTitle());
            writer.endNode();
        }

        //process contacts
        List<NihmsMetadata.Person> persons = metadata.getPersons();
        if (persons.size() > 0) {
            writer.startNode(N_CONTACTS);
            for (NihmsMetadata.Person person : persons) {
                writer.startNode(N_PERSON);
                if (person.getFirstName() != null) {
                    writer.addAttribute(N_FIRST_NAME, person.getFirstName());
                }
                if (person.getMiddleName() != null) {
                    writer.addAttribute(N_MIDDLE_NAME, person.getMiddleName());
                }
                if (person.getLastName() != null) {
                    writer.addAttribute(N_LAST_NAME, person.getLastName());
                }
                if (person.getEmail() != null) {
                    writer.addAttribute(N_EMAIL, person.getEmail());
                }
                //primitive types
                writer.addAttribute(N_PI, booleanConvert(person.isPi()));
                writer.addAttribute(N_CORRESPONDING_PI, booleanConvert(person.isCorrespondingPi()));
                writer.addAttribute(N_AUTHOR, booleanConvert(person.isAuthor()));
                writer.endNode(); // end person
            }
            writer.endNode(); //end contacts
        }
    }

    public Object unmarshal(HierarchicalStreamReader reader,
                            UnmarshallingContext context) {
        NihmsMetadata metadata = new NihmsMetadata();
        NihmsMetadata.Manuscript manuscriptMetadata = new NihmsMetadata.Manuscript();
        NihmsMetadata.Journal journalMetadata = new NihmsMetadata.Journal();
        List<NihmsMetadata.Person> contacts = new ArrayList<>();

        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String name = reader.getNodeName();
            switch (name) {
                case N_MANUSCRIPT:
                    if (reader.getAttribute(N_ID) != null) {
                        manuscriptMetadata.setNihmsId(reader.getAttribute(N_ID));
                    }
                    if (reader.getAttribute(N_PUB_PDF) != null) {
                        manuscriptMetadata.setPublisherPdf(stringConvert(reader.getAttribute(N_PUB_PDF)));
                    }
                    if (reader.getAttribute(N_SHOW_PUB_PDF) != null) {
                        manuscriptMetadata.setShowPublisherPdf(stringConvert(reader.getAttribute(N_SHOW_PUB_PDF)));
                    }
                    if (reader.getAttribute(N_EMBARGO) != null) {
                        manuscriptMetadata.setRelativeEmbargoPeriodMonths(Integer.parseInt(reader.getAttribute(N_EMBARGO)));
                    }
                    if (reader.getAttribute(N_PUB_MED_ID) != null) {
                        manuscriptMetadata.setPubmedId(reader.getAttribute(N_PUB_MED_ID));
                    }
                    if (reader.getAttribute(N_PUB_MED_CENTRAL_ID) != null) {
                        manuscriptMetadata.setPubmedCentralId(reader.getAttribute(N_PUB_MED_CENTRAL_ID));
                    }
                    if (reader.getAttribute(N_MANUSCRIPT_URL) != null) {
                        String manuscriptURL = reader.getAttribute(N_MANUSCRIPT_URL);
                        try {
                            manuscriptMetadata.setManuscriptUrl(new URL(manuscriptURL));
                        } catch (MalformedURLException mue) {
                            LOG.error("Unable to create URL for " + manuscriptURL, mue.getMessage());
                        }
                    }
                    if (reader.getAttribute(N_DOI) != null) {
                        manuscriptMetadata.setDoi(URI.create(reader.getAttribute(N_DOI)));
                    }
                    break;
                case N_JOURNAL:
                    while (reader.hasMoreChildren()) {
                        reader.moveDown();
                        name = reader.getNodeName();
                        switch (name) {
                            case N_JOURNAL_ID:
                                journalMetadata.setJournalId(reader.getValue());
                                //journalType is hard-coded in the journal class and cannot be set,
                                // so journal-id-type does not need to be read.  ("nlm-ta")
                                break;
                            case N_ISSN:
                                journalMetadata.setIssn(reader.getValue());
                                String pubType = reader.getAttribute(N_PUB_TYPE);
                                if (pubType != null) {
                                    if (pubType.equals("epub")) {
                                        journalMetadata.setPubType(NihmsMetadata.JOURNAL_PUBLICATION_TYPE.epub);
                                    } else if (pubType.equals("ppub")) {
                                        journalMetadata.setPubType(NihmsMetadata.JOURNAL_PUBLICATION_TYPE.ppub);
                                    }
                                }
                                break;
                            case N_JOURNAL_TITLE:
                                journalMetadata.setJournalTitle(reader.getValue());
                                break;
                        }
                        reader.moveUp();
                    }
                    break;
                case N_MANUSCRIPT_TITLE:
                    manuscriptMetadata.setTitle(reader.getValue());
                    break;
                case N_CONTACTS:
                    while (reader.hasMoreChildren()) {
                        reader.moveDown();
                        name = reader.getNodeName();
                        if (name.equals(N_PERSON)) {
                            NihmsMetadata.Person person = new NihmsMetadata.Person();
                            if (reader.getAttribute(N_FIRST_NAME) != null) {
                                person.setFirstName(reader.getAttribute(N_FIRST_NAME));
                            }
                            if (reader.getAttribute(N_MIDDLE_NAME) != null) {
                                person.setMiddleName(reader.getAttribute(N_MIDDLE_NAME));
                            }
                            if (reader.getAttribute(N_LAST_NAME) != null) {
                                person.setLastName(reader.getAttribute(N_LAST_NAME));
                            }
                            if (reader.getAttribute(N_EMAIL) != null) {
                                person.setEmail(reader.getAttribute(N_EMAIL));
                            }
                            if (reader.getAttribute(N_PI) != null) {
                                person.setPi(stringConvert(reader.getAttribute(N_PI)));
                            }
                            if (reader.getAttribute(N_CORRESPONDING_PI) != null) {
                                person.setCorrespondingPi(stringConvert(reader.getAttribute(N_CORRESPONDING_PI)));
                            }
                            if (reader.getAttribute(N_AUTHOR) != null) {
                                person.setAuthor(stringConvert(reader.getAttribute(N_AUTHOR)));
                            }
                            contacts.add(person);
                        }
                        reader.moveUp();
                    }
                    break;
            }
            reader.moveUp();
        }

        metadata.setManuscriptMetadata(manuscriptMetadata);
        metadata.setJournalMetadata(journalMetadata);
        metadata.setPersons(contacts);

        return metadata;
    }

    /**
     * Method to convert boolean into yes or no
     * @param  b the boolean to convert
     * @return yes if true, no if false
     */
    private String booleanConvert(boolean b) {
        return(b?"yes":"no");
    }

    /**
     * Method to convert String yes or no into boolean
     * @param s the string - yes or no
     * @return true if yes, false otherwise
     */
    private boolean stringConvert(String s) {
        return(s.equals("yes"));
    }
}
