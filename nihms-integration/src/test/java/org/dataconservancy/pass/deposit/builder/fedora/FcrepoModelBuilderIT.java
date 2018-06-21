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

package org.dataconservancy.pass.deposit.builder.fedora;

import org.dataconservancy.nihms.builder.fs.FcrepoModelBuilder;
import org.dataconservancy.nihms.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.nihms.model.DepositMetadata;
import org.dataconservancy.nihms.model.DepositSubmission;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;

public class FcrepoModelBuilderIT {

    private static final String EXPECTED_JOURNAL_TITLE = "The Analyst";

    private static final String EXPECTED_ISSN = "0003-2654,1364-5528";

    private static final String EXPECTED_DOI = "10.1039/c7an01617d";

    private static final String EXPECTED_EMBARGO_END_DATE = "2018-06-30";

    private static final int EXPECTED_SUBMITER_COUNT = 1;

    private static final int EXPECTED_PI_COUNT = 1;

    private static final int EXPECTED_CO_PI_COUNT = 1;

    private static final int EXPECTED_AUTHOR_COUNT = 5;

    private DepositSubmission submission;
    private FcrepoModelBuilder underTest = new FcrepoModelBuilder();
    private static final String SAMPLE_SUBMISSION_RESOURCE = "SampleSubmissionData.json";
    private HashMap<URI, PassEntity> entities = new HashMap<>();
    private PassJsonFedoraAdapter adapter = new PassJsonFedoraAdapter();
    private Submission submissionEntity = null;

    @Before
    public void setup() throws Exception {
        // Upload sample data to Fedora repository to get its Submission URI.
        URL sampleDataUrl = this.getClass().getResource(SAMPLE_SUBMISSION_RESOURCE);
        InputStream is = new FileInputStream(sampleDataUrl.getPath());
        URI submissionUri = adapter.jsonToFcrepo(is, entities);
        is.close();

        // Find the Submission entity that was uploaded
        for (URI key : entities.keySet()) {
            PassEntity entity = entities.get(key);
            if (entity.getId() == submissionUri) {
                submissionEntity = (Submission)entity;
                break;
            }
        }

        submission = underTest.build(submissionUri.toString());
    }

    @Test
    public void testElementValues() {
        assertNotNull("Could not find Submission entity", submissionEntity);

        // Check that some basic things are in order
        assertNotNull(submission.getManifest());
        assertNotNull(submission.getMetadata());
        assertNotNull(submission.getMetadata().getManuscriptMetadata());
        assertNotNull(submission.getMetadata().getJournalMetadata());
        assertNotNull(submission.getMetadata().getArticleMetadata());
        assertNotNull(submission.getMetadata().getPersons());

        // Cannot compare ID strings, as they change when uploading to a Fedora server.
        Publication publication = (Publication)entities.get(submissionEntity.getPublication());
        assertEquals(EXPECTED_DOI, submission.getMetadata().getArticleMetadata().getDoi().toString());

        assertNotNull(submission.getFiles());
        assertEquals(2, submission.getFiles().size());

        // Confirm that some values were set correctly from the Submission metadata
        DepositMetadata.Journal journalMetadata = submission.getMetadata().getJournalMetadata();
        assertEquals(EXPECTED_JOURNAL_TITLE, journalMetadata.getJournalTitle());
        assertEquals(EXPECTED_ISSN, journalMetadata.getIssn());

        DepositMetadata.Manuscript manuscriptMetadata = submission.getMetadata().getManuscriptMetadata();
        assertNull(manuscriptMetadata.getManuscriptUrl());

//        assertTrue(submission.getMetadata().getArticleMetadata().getUnderEmbargo());
        assertEquals(EXPECTED_EMBARGO_END_DATE, submission.getMetadata().getArticleMetadata().getEmbargoLiftDate()
                .format(DateTimeFormatter.ofPattern("uuuu-MM-dd")));

        List<DepositMetadata.Person> persons = submission.getMetadata().getPersons();
        int authors = 0;
        int pis = 0;
        int copis = 0;
        int submitters = 0;
        for (DepositMetadata.Person person : persons) {
            switch (person.getType()) {
                case author:
                    authors++;
                    break;
                case pi:
                    pis++;
                    break;
                case copi:
                    copis++;
                    break;
                case submitter:
                    submitters++;
                    break;
            }
        }

        assertEquals(EXPECTED_SUBMITER_COUNT, submitters);
        assertEquals(EXPECTED_PI_COUNT, pis);
        assertEquals(EXPECTED_CO_PI_COUNT, copis);
        assertEquals(EXPECTED_AUTHOR_COUNT, authors);

        assertTrue(persons.stream()
                .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                .anyMatch(author ->
                author.getName().equals("Lei Zhang")));

        assertTrue(persons.stream()
                .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                .anyMatch(author ->
                        author.getName().equals("KaiJin Tian")));

        assertTrue(persons.stream()
                .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                .anyMatch(author ->
                        author.getName().equals("YongPing Dong")));

        assertTrue(persons.stream()
                .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                .anyMatch(author ->
                        author.getName().equals("HouCheng Ding")));

        assertTrue(persons.stream()
                .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                .anyMatch(author ->
                        author.getName().equals( "ChengMing Wang")));

    }

    @After
    public void tearDown() {
        // Clean up the server
        adapter.deleteFromFcrepo(entities);
    }

}