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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;
import org.dataconservancy.nihms.model.NihmsMetadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * XML serialization of our NihmsMetadata to conform with the bulk submission dtd
 *
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsMetadataSerializer implements StreamingSerializer{

    private NihmsMetadata metadata;

    NihmsMetadataSerializer(NihmsMetadata metadata){
        this.metadata = metadata;
    }

    public InputStream serialize() {
        XStream xstream = new XStream(new DomDriver("UTF-8", new XmlFriendlyNameCoder("_-", "_")));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        xstream.registerConverter(new MetadataConverter());
        xstream.alias("nihms-submit", NihmsMetadata.class);
        xstream.toXML(metadata, os);

        byte[] bytes = os.toByteArray();

        try (InputStream is = new ByteArrayInputStream(bytes)) {
            os.close();
            return is;
        } catch (IOException ioe) {
            throw new RuntimeException("Could not create Input Stream, or close Output Stream", ioe);
        }

    }

    private class MetadataConverter implements Converter {
        public boolean canConvert(Class clazz) {
            return NihmsMetadata.class == clazz;
        }

        public void marshal(Object value, HierarchicalStreamWriter writer,
                            MarshallingContext context) {
            NihmsMetadata metadata = (NihmsMetadata) value;

            //process manuscript element (except, strangely, for title, which we do after journal)
            NihmsMetadata.Manuscript manuscript = metadata.getManuscriptMetadata();
            writer.startNode("manuscript");
            if(manuscript.getNihmsId() != null) {
                writer.addAttribute("id", manuscript.getNihmsId());
            }

            //primitive types
            writer.addAttribute("publisher_pdf", booleanConvert(manuscript.isPublisherPdf()));
            writer.addAttribute("show_publisher_pdf", booleanConvert(manuscript.isShowPublisherPdf()));
            writer.addAttribute("embargo", String.valueOf(manuscript.getRelativeEmbargoPeriodMonths()));

            writer.endNode(); //end manuscript

            //process journal
            NihmsMetadata.Journal journal = metadata.getJournalMetadata();
            writer.startNode("journal-meta");
            if (journal.getJournalId() != null) {
                writer.startNode("journal-id");
                if (journal.getJournalType() != null) {
                    writer.addAttribute("journal-id-type", journal.getJournalType());
                }
                writer.setValue(journal.getJournalId());
                writer.endNode();
            }

            if (journal.getIssn() != null) {
                writer.startNode("issn");
                if (journal.getPubType() != null) {
                    writer.addAttribute("pub-type", journal.getPubType().toString());
                }
                writer.setValue(journal.getIssn());
                writer.endNode();
            }
            if (journal.getJournalTitle() != null) {
                writer.startNode("journal-title");
                writer.setValue(journal.getJournalTitle());
                writer.endNode();
            }
            writer.endNode(); //end journal-meta

            //now process full manuscript title
            if (manuscript.getTitle() != null) {
                writer.startNode("title");
                writer.setValue(manuscript.getTitle());
                writer.endNode();
            }

            //process contacts
            List<NihmsMetadata.Person> persons = metadata.getPersons();
            if (persons.size()>0) {
                writer.startNode("contacts");
                for (NihmsMetadata.Person person : persons){
                    writer.startNode("person");
                    if (person.getFirstName() != null) {
                        writer.addAttribute("fname",person.getFirstName());
                    }
                    if (person.getMiddleName() != null) {
                        writer.addAttribute("mname",person.getMiddleName());
                    }
                    if (person.getLastName() != null) {
                        writer.addAttribute("lname",person.getLastName());
                    }
                    if (person.getEmail() != null) {
                        writer.addAttribute("email",person.getEmail());
                    }
                    //primitive types
                    writer.addAttribute("pi", booleanConvert(person.isPi()));
                    writer.addAttribute("corrpi", booleanConvert(person.isCorrespondingPi()));
                    writer.addAttribute("author", booleanConvert(person.isAuthor()));
                    writer.endNode(); // end person
                }
                writer.endNode(); //end contacts
            }
        }

        public Object  unmarshal(HierarchicalStreamReader reader,
                                 UnmarshallingContext context) {
            return null;
        }
    }

    /**
     * Method to convert boolean into yes or no
     * @param  b the boolean to convert
     * @return yes if true, no if false
     */
    String booleanConvert(boolean b){
        return(b?"yes":"no");
    }

}
