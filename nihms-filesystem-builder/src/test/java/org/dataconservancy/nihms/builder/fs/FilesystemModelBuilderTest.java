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

import org.dataconservancy.nihms.model.NihmsSubmission;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.Properties;

public class FilesystemModelBuilderTest {

    private NihmsSubmission submission;
    private Properties expectedProperties = new Properties();
    private String testPropertiesFile = "FilesystemModelBuilderTest.properties";
    private  FilesystemModelBuilder underTest = new FilesystemModelBuilder();

    @Before
    public void setup() throws Exception{
        URL resourceFileUrl = FilesystemModelBuilderTest.class.getClassLoader().getResource(testPropertiesFile);
        InputStream is = FilesystemModelBuilderTest.class.getClassLoader().getResourceAsStream(testPropertiesFile);
        expectedProperties.load(is);
        is.close();

        submission = underTest.build(resourceFileUrl.getPath());

        //check that we have at least one property so that we know we have processed the file
        Assert.assertNotNull(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_SUBMISSION_ID));
    }

    @Test
    public void testElementValues(){
        //Submission id
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_SUBMISSION_ID),
                submission.getId());

        //File Elements
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_FILE_LABEL),
                submission.getFiles().get(0).getLabel());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_FILE_LOCATION),
                submission.getFiles().get(0).getLocation());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_FILE_NAME),
                submission.getFiles().get(0).getName());

        //Person elements
        Assert.assertEquals(Boolean.parseBoolean(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_AUTHOR)),
                submission.getMetadata().getPersons().get(0).isAuthor());
        Assert.assertEquals(Boolean.parseBoolean(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_CORRESPONDINGPI)),
                submission.getMetadata().getPersons().get(0).isCorrespondingPi());
        Assert.assertEquals(Boolean.parseBoolean(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_PI)),
                submission.getMetadata().getPersons().get(0).isPi());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_EMAIL),
                submission.getMetadata().getPersons().get(0).getEmail());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_FIRSTNAME),
                submission.getMetadata().getPersons().get(0).getFirstName());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_MIDDLENAME),
                submission.getMetadata().getPersons().get(0).getMiddleName());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_PERSON_LASTNAME),
                submission.getMetadata().getPersons().get(0).getLastName());

        //Journal elements
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_JOURNAL_ID),
                submission.getMetadata().getJournalMetadata().getJournalId());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_JOURNAL_ISSN),
                submission.getMetadata().getJournalMetadata().getIssn());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_JOURNAL_TITLE),
                submission.getMetadata().getJournalMetadata().getJournalTitle());

        //Manuscript elements
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_ID),
                submission.getMetadata().getManuscriptMetadata().getNihmsId());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PUBMEDID),
                submission.getMetadata().getManuscriptMetadata().getPubmedId());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PUBMEDCENTRALID),
                submission.getMetadata().getManuscriptMetadata().getPubmedCentralId());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_URL),
                submission.getMetadata().getManuscriptMetadata().getManuscriptUrl().toString());
        Assert.assertEquals(expectedProperties.getProperty(NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_DOI),
                submission.getMetadata().getManuscriptMetadata().getDoi().toString());
    }

}