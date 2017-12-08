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

package org.dataconservancy.nihms.builder.fs;

import org.dataconservancy.nihms.model.NihmsMetadata;
import org.dataconservancy.nihms.model.NihmsSubmission;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class FilesystemModelBuilderTest {

    private NihmsSubmission submission;
    private Properties expectedProperties = new Properties();
    private  FilesystemModelBuilder underTest = new FilesystemModelBuilder();

    @Before
    public void setup() throws Exception{
        String testPropertiesFile = "FilesystemModelBuilderTest.properties";
        URL resourceFileUrl = FilesystemModelBuilderTest.class.getClassLoader().getResource(testPropertiesFile);
        InputStream is = FilesystemModelBuilderTest.class.getClassLoader().getResourceAsStream(testPropertiesFile);
        expectedProperties.load(is);
        is.close();

        //check that we have at least one property so that we know we have processed the file
        assertNotNull(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_SUBMISSION_ID));

        submission = underTest.build(resourceFileUrl.getPath());
    }

    @Test
    public void testElementValues(){
        //Submission Elements
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_SUBMISSION_ID),
                submission.getId());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_SUBMISSION_NAME),
                submission.getName());

        //File Elements
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_FILE_LABEL),
                submission.getFiles().get(0).getLabel());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_FILE_LOCATION),
                submission.getFiles().get(0).getLocation());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_FILE_NAME),
                submission.getFiles().get(0).getName());

        //Person elements
        assertEquals(Boolean.parseBoolean(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_AUTHOR)),
                submission.getMetadata().getPersons().get(0).isAuthor());
        assertEquals(Boolean.parseBoolean(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_CORRESPONDINGPI)),
                submission.getMetadata().getPersons().get(0).isCorrespondingPi());
        assertEquals(Boolean.parseBoolean(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_PI)),
                submission.getMetadata().getPersons().get(0).isPi());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_EMAIL),
                submission.getMetadata().getPersons().get(0).getEmail());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_FIRSTNAME),
                submission.getMetadata().getPersons().get(0).getFirstName());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_MIDDLENAME),
                submission.getMetadata().getPersons().get(0).getMiddleName());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_LASTNAME),
                submission.getMetadata().getPersons().get(0).getLastName());

        //Journal elements
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_JOURNAL_ID),
                submission.getMetadata().getJournalMetadata().getJournalId());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_JOURNAL_ISSN),
                submission.getMetadata().getJournalMetadata().getIssn());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_JOURNAL_TITLE),
                submission.getMetadata().getJournalMetadata().getJournalTitle());

        //Manuscript elements
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_ID),
                submission.getMetadata().getManuscriptMetadata().getNihmsId());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PUBMEDID),
                submission.getMetadata().getManuscriptMetadata().getPubmedId());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PUBMEDCENTRALID),
                submission.getMetadata().getManuscriptMetadata().getPubmedCentralId());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_URL),
                submission.getMetadata().getManuscriptMetadata().getManuscriptUrl().toString());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_DOI),
                submission.getMetadata().getManuscriptMetadata().getDoi().toString());
        assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_TITLE),
                submission.getMetadata().getManuscriptMetadata().getTitle());
    }

}