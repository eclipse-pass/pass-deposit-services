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
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;
import org.dataconservancy.nihms.model.NihmsMetadata;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmlunit.validation.Languages;
import org.xmlunit.validation.ValidationResult;
import org.xmlunit.validation.Validator;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * This is a test for the metadata serializer. for now we just validate against the bulk submission dtd
 *
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsMetadataSerializerTest {

    private static NihmsMetadataSerializer underTest;
    private static NihmsMetadata metadata= new NihmsMetadata();

    @BeforeClass
    public static void setup() throws Exception {
        //set up metadata snd its fields;
        NihmsMetadata.Journal journal = new NihmsMetadata.Journal();
        NihmsMetadata.Manuscript manuscript = new NihmsMetadata.Manuscript();
        List<NihmsMetadata.Person> personList = new ArrayList<>();

        //populate journal metadata
        journal.setIssn("1234-5678");
        journal.setJournalId("FJ001");
        journal.setJournalTitle("Dairy Cow Monthly");
        journal.setPubType(NihmsMetadata.JOURNAL_PUBLICATION_TYPE.epub);

        //populate manuscript metadata
        manuscript.setDoi(URI.create("doi:10.1234/smh0000001"));
        manuscript.setManuscriptUrl(new URL("http://google.com"));
        manuscript.setNihmsId("00001");
        manuscript.setPublisherPdf(true);
        manuscript.setPubmedCentralId("PMC00001");
        manuscript.setPubmedId("00002");
        manuscript.setRelativeEmbargoPeriodMonths(0);
        manuscript.setShowPublisherPdf(false);
        manuscript.setTitle("Manuscript Title");

        //populate persons
        NihmsMetadata.Person person1 = new NihmsMetadata.Person();
        person1.setAuthor(true);
        person1.setCorrespondingPi(false);
        person1.setEmail("person@farm.com");
        person1.setFirstName("Bessie");
        person1.setLastName("Beef");
        person1.setMiddleName("A");
        person1.setPi(true);
        personList.add(person1);

        NihmsMetadata.Person person2 = new NihmsMetadata.Person();
        person2.setAuthor(false);
        person2.setCorrespondingPi(true);
        person2.setEmail("person@farm.com");
        person2.setFirstName("Elsie");
        person2.setLastName("Cow");
        person2.setMiddleName("B");
        person2.setPi(false);
        personList.add(person2);

        NihmsMetadata.Person person3 = new NihmsMetadata.Person();
        person3.setAuthor(false);
        person3.setCorrespondingPi(false);
        person3.setEmail("person@farm.com");
        person3.setFirstName("Mark");
        person3.setLastName("Bovine");
        person3.setMiddleName("C");
        person3.setPi(false);
        personList.add(person3);

        NihmsMetadata.Person person4 = new NihmsMetadata.Person();
        person4.setAuthor(false);
        person4.setCorrespondingPi(false);
        person4.setEmail("person@farm.com");
        person4.setFirstName("John");
        person4.setLastName("Bull");
        person4.setMiddleName("D");
        person4.setPi(true);
        personList.add(person4);

        metadata.setJournalMetadata(journal);
        metadata.setManuscriptMetadata(manuscript);
        metadata.setPersons(personList);

        underTest = new NihmsMetadataSerializer(metadata);
    }


    @Test
    public void testSerializedMetadataValidity() throws Exception {
        InputStream is = underTest.serialize();
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();

        File targetFile = File.createTempFile("MetadataSerializerTest-",".xml");

        OutputStream os = new FileOutputStream(targetFile);

        //need to add these to make validator happy
        os.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n".getBytes());
        os.write("<!DOCTYPE nihms-submit SYSTEM \"bulksubmission.dtd\">\n".getBytes());
        os.write(buffer);
        os.close();

        Validator v = Validator.forLanguage(Languages.XML_DTD_NS_URI);
        StreamSource dtd = new StreamSource(getClass().getResourceAsStream("bulksubmission.dtd"));
        dtd.setSystemId(getClass().getResource("bulksubmission.dtd").toURI().toString());
        v.setSchemaSource(dtd);
        StreamSource s = new StreamSource(targetFile);
        ValidationResult r = v.validateInstance(s);
        assertTrue(r.isValid());

    }

    @Test
    public void testUnmarshalMarshalIsIdentity() throws Exception {
        XStream xstream = new XStream(new DomDriver("UTF-8", new XmlFriendlyNameCoder("_-", "_")));
        xstream.registerConverter(new NihmsMetadataConverter());
        XStream.setupDefaultSecurity(xstream);
        xstream.alias("nihms-submit",NihmsMetadata.class);
        xstream.allowTypesByWildcard(new String[] {
            "org.dataconservancy.nihms.model.**"
        });

        InputStream is = underTest.serialize();
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();

        File targetFile =  File.createTempFile("MetadataRoundtripTest-",".xml");

        OutputStream os = new FileOutputStream(targetFile);
        os.write(buffer);
        os.close();

        NihmsMetadata roundtrippedMetadata = (NihmsMetadata) xstream.fromXML(new FileInputStream(targetFile));

        //manuscript metadata
        NihmsMetadata.Manuscript expectedManuscript = metadata.getManuscriptMetadata();
        NihmsMetadata.Manuscript actualManuscript = roundtrippedMetadata.getManuscriptMetadata();

        assertEquals(expectedManuscript.getTitle(), actualManuscript.getTitle());
        assertEquals(expectedManuscript.getRelativeEmbargoPeriodMonths(), actualManuscript.getRelativeEmbargoPeriodMonths());
        assertEquals(expectedManuscript.getDoi(), actualManuscript.getDoi());
        assertEquals(expectedManuscript.getPubmedId(), actualManuscript.getPubmedId());
        assertEquals(expectedManuscript.getPubmedCentralId(), actualManuscript.getPubmedCentralId());
        assertEquals(expectedManuscript.getManuscriptUrl(),actualManuscript.getManuscriptUrl());
        assertEquals(expectedManuscript.getNihmsId(), actualManuscript.getNihmsId());
        assertEquals(expectedManuscript.getRelativeEmbargoPeriodMonths(), actualManuscript.getRelativeEmbargoPeriodMonths());

        //journal metadata
        NihmsMetadata.Journal expectedJournal = metadata.getJournalMetadata();
        NihmsMetadata.Journal actualJournal = roundtrippedMetadata.getJournalMetadata();

        assertEquals(expectedJournal.getIssn(), actualJournal.getIssn());
        assertEquals(expectedJournal.getJournalId(), actualJournal.getJournalId());
        assertEquals(expectedJournal.getJournalTitle(), actualJournal.getJournalTitle());
        assertEquals(expectedJournal.getJournalType(), actualJournal.getJournalType());
        assertEquals(expectedJournal.getPubType(), actualJournal.getPubType());

        List<NihmsMetadata.Person> expectedPersons = metadata.getPersons();
        List<NihmsMetadata.Person> actualPersons = roundtrippedMetadata.getPersons();

        //person metadata
        assertEquals(expectedPersons.size(), actualPersons.size());

        for (int i=0; i < expectedPersons.size(); i++) {
            NihmsMetadata.Person expectedPerson = expectedPersons.get(i);
            NihmsMetadata.Person actualPerson = actualPersons.get(i);

            assertEquals(expectedPerson.getEmail(), actualPerson.getEmail());
            assertEquals(expectedPerson.getFirstName(), actualPerson.getFirstName());
            assertEquals(expectedPerson.getMiddleName(), actualPerson.getMiddleName());
            assertEquals(expectedPerson.getLastName(), actualPerson.getLastName());
            assertEquals(expectedPerson.isAuthor(), actualPerson.isAuthor());
            assertEquals(expectedPerson.isCorrespondingPi(), actualPerson.isCorrespondingPi());
            assertEquals(expectedPerson.isPi(), actualPerson.isPi());
        }
    }
}