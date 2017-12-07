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

import org.apache.commons.io.IOUtils;
import org.dataconservancy.nihms.model.NihmsMetadata;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NihmsMetadataSerializerTest {

    @Test
    public void metadataSerializerTest() throws MalformedURLException {

        //set up metadata snd its fields;
        NihmsMetadata metadata = new NihmsMetadata();

        //NihmsMetadata.Article article = new NihmsMetadata.Article();
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
        manuscript.setManuscriptUrl(new URL("http://farm.com/Cows"));
        manuscript.setNihmsId("00001");
        manuscript.setPublisherPdf(true);
        //manuscript.setPubmedCentralId("PMC00001");
        manuscript.setPubmedId("00001");
        manuscript.setRelativeEmbargoPeriodMonths(0);
        manuscript.setShowPublisherPdf(false);
        manuscript.setTitle("Manuscript Title");

        //populate persons
        NihmsMetadata.Person person1 = new NihmsMetadata.Person();
        person1.setAuthor(true);
        person1.setCorrespondingPi(false);
        person1.setEmail("person@farm.com");
        person1.setFirstName("Bessie");
        person1.setLastName("Cow");
        person1.setMiddleName("The");
        person1.setPi(true);
        personList.add(person1);

        NihmsMetadata.Person person2 = new NihmsMetadata.Person();
        person2.setAuthor(false);
        person2.setCorrespondingPi(true);
        person2.setEmail("person@farm.com");
        person2.setFirstName("Elsie");
        person2.setLastName("Cow");
        person2.setMiddleName("The");
        person2.setPi(false);
        personList.add(person2);

        NihmsMetadata.Person person3 = new NihmsMetadata.Person();
        person3.setAuthor(false);
        person3.setCorrespondingPi(false);
        person3.setEmail("person@farm.com");
        person3.setFirstName("Mark");
        person3.setLastName("Cow");
        person3.setMiddleName("The");
        person3.setPi(false);
        personList.add(person3);

        NihmsMetadata.Person person4 = new NihmsMetadata.Person();
        person4.setAuthor(false);
        person4.setCorrespondingPi(false);
        person4.setEmail("person@farm.com");
        person4.setFirstName("John");
        person4.setLastName("Cow");
        person4.setMiddleName("The");
        person4.setPi(true);
        personList.add(person4);

        metadata.setJournalMetadata(journal);
        metadata.setManuscriptMetadata(manuscript);
        metadata.setPersons(personList);


        NihmsMetadataSerializer underTest = new NihmsMetadataSerializer(metadata);

        InputStream is = underTest.serialize();

        //System.out.println(IOUtils.toString(is, "UTF-8"));

        try {
            System.out.println(IOUtils.toString(is, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //assertEquals(expected, actual);

    }



}
