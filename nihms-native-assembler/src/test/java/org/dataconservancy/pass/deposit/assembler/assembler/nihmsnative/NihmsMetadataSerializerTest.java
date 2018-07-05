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

package org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative;

import org.dataconservancy.pass.deposit.model.DepositMetadata;
import org.dataconservancy.pass.deposit.model.JournalPublicationType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xmlunit.validation.Languages;
import org.xmlunit.validation.ValidationResult;
import org.xmlunit.validation.Validator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * This is a test for the metadata serializer. for now we just validate against the bulk submission dtd
 *
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsMetadataSerializerTest {

    private static NihmsMetadataSerializer underTest;
    private static DepositMetadata metadata= new DepositMetadata();

    @BeforeClass
    public static void setup() throws Exception {
        //set up metadata snd its fields;
        DepositMetadata.Journal journal = new DepositMetadata.Journal();
        DepositMetadata.Manuscript manuscript = new DepositMetadata.Manuscript();
        DepositMetadata.Article article = new DepositMetadata.Article();
        List<DepositMetadata.Person> personList = new ArrayList<>();

        //populate journal metadata
        journal.setJournalId("FJ001");
        journal.setJournalTitle("Dairy Cow Monthly");
        journal.setIssnPubTypes(new HashMap<String, DepositMetadata.IssnPubType>() {
            {
                put("1234-5678", new DepositMetadata.IssnPubType("1234-5678", JournalPublicationType.EPUB));
            }
        });

        //populate manuscript metadata
        manuscript.setManuscriptUrl(new URL("http://farm.com/Cows"));
        manuscript.setNihmsId("00001");
        manuscript.setPublisherPdf(true);
        manuscript.setShowPublisherPdf(false);
        manuscript.setTitle("Manuscript Title");

        // populate article metadata
        article.setDoi(URI.create("10.1234/smh0000001"));

        //populate persons
        DepositMetadata.Person person1 = new DepositMetadata.Person();
        person1.setType(DepositMetadata.PERSON_TYPE.author);
        person1.setEmail("person@farm.com");
        person1.setFirstName("Bessie");
        person1.setLastName("Cow");
        person1.setMiddleName("The");
        personList.add(person1);

        // Enter the first person twice, as both an author and a PI
        DepositMetadata.Person person1a = new DepositMetadata.Person(person1);
        person1.setType(DepositMetadata.PERSON_TYPE.pi);
        personList.add(person1);

        DepositMetadata.Person person2 = new DepositMetadata.Person();
        person2.setType(DepositMetadata.PERSON_TYPE.submitter);
        person2.setEmail("person@farm.com");
        person2.setFirstName("Elsie");
        person2.setLastName("Cow");
        person2.setMiddleName("The");
        personList.add(person2);

        DepositMetadata.Person person3 = new DepositMetadata.Person();
        person3.setType(DepositMetadata.PERSON_TYPE.author);
        person3.setEmail("person@farm.com");
        person3.setFirstName("Mark");
        person3.setLastName("Cow");
        person3.setMiddleName("The");
        personList.add(person3);

        DepositMetadata.Person person4 = new DepositMetadata.Person();
        person4.setType(DepositMetadata.PERSON_TYPE.copi);
        person4.setEmail("person@farm.com");
        person4.setFirstName("John");
        person4.setLastName("Cow");
        person4.setMiddleName("The");
        personList.add(person4);

        metadata.setJournalMetadata(journal);
        metadata.setManuscriptMetadata(manuscript);
        metadata.setPersons(personList);
        metadata.setArticleMetadata(article);

        underTest = new NihmsMetadataSerializer(metadata);
    }


    @Test
    public void testSerializedMetadataValidity() throws Exception {
        InputStream is = underTest.serialize();
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();

        File targetFile = new File("MetadataSerializerTest.xml");

        OutputStream os = new FileOutputStream(targetFile);

        os.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n".getBytes());
        os.write("<!DOCTYPE nihms-submit SYSTEM \"bulksubmission.dtd\">\n".getBytes());
        os.write(buffer);
        os.close();

        Validator v = Validator.forLanguage(Languages.XML_DTD_NS_URI);
        StreamSource dtd = new StreamSource(getClass().getResourceAsStream("bulksubmission.dtd"));
        dtd.setSystemId(getClass().getResource("bulksubmission.dtd").toURI().toString());
        v.setSchemaSource(dtd);
        StreamSource s = new StreamSource("MetadataSerializerTest.xml");
        ValidationResult r = v.validateInstance(s);
        assertTrue(r.isValid());
    }


    @Test
    // DOI must be in "raw" format (no leading http://domain/ or https://domain/)
    public void testSerializedMetadataDoi() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        String path = "10.1234/smh0000001";
        InputStream is;
        Node node;
        String doi;

        metadata.getArticleMetadata().setDoi(URI.create(path));
        is = underTest.serialize();
        node = builder.parse(is).getDocumentElement().getFirstChild().getNextSibling();
        doi = node.getAttributes().getNamedItem("doi").getTextContent();
        is.close();
        assertTrue("Valid DOI was modified during export.", doi.contentEquals(path));

        metadata.getArticleMetadata().setDoi(URI.create("http://dx.doi.org/" + path));
        is = underTest.serialize();
        node = builder.parse(is).getDocumentElement().getFirstChild().getNextSibling();
        doi = node.getAttributes().getNamedItem("doi").getTextContent();
        is.close();
        assertTrue("http:// prefix and/or domain not stripped from DOI during export.", doi.contentEquals(path));
    }

}