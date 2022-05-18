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

package org.dataconservancy.pass.deposit.builder.fedora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static submissions.SubmissionResourceUtil.lookupStream;

import java.io.InputStream;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import org.dataconservancy.pass.deposit.builder.fs.FcrepoModelBuilder;
import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.deposit.messaging.config.spring.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.spring.DrainQueueConfig;
import org.dataconservancy.pass.deposit.model.DepositMetadata;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.model.JournalPublicationType;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = DepositConfig.class)
@ComponentScan("org.dataconservancy.pass.deposit")
@Import(DrainQueueConfig.class)
@DirtiesContext
public class FcrepoModelBuilderIT {

    private static final String EXPECTED_JOURNAL_TITLE = "Food & Function";

    private static final Map<String, DepositMetadata.IssnPubType> EXPECTED_ISSNS =
        new HashMap<String, DepositMetadata.IssnPubType>() {
            {
                put("2042-650X", new DepositMetadata.IssnPubType("2042-650X", JournalPublicationType.OPUB));
                put("2042-6496", new DepositMetadata.IssnPubType("2042-6496", JournalPublicationType.PPUB));
            }
        };

    private static final String EXPECTED_DOI = "10.1039/c7fo01251a";

    private static final String EXPECTED_EMBARGO_END_DATE = "2018-06-30";

    private static final int EXPECTED_SUBMITER_COUNT = 1;

    private static final int EXPECTED_PI_COUNT = 1;

    private static final int EXPECTED_CO_PI_COUNT = 2;

    private static final int EXPECTED_AUTHOR_COUNT = 6;

    private static final String EXPECTED_NLMTA = "Food Funct";

    private DepositSubmission submission;
    private FcrepoModelBuilder underTest = new FcrepoModelBuilder();
    private static final URI SAMPLE_SUBMISSION_RESOURCE = URI.create("fake:submission1");
    private HashMap<URI, PassEntity> entities = new HashMap<>();
    private PassJsonFedoraAdapter adapter = new PassJsonFedoraAdapter();
    private Submission submissionEntity = null;

    @Before
    public void setup() throws Exception {
        // Upload sample data to Fedora repository to get its Submission URI.
        URI submissionUri;
        try (InputStream is = lookupStream(SAMPLE_SUBMISSION_RESOURCE)) {
            submissionUri = adapter.jsonToFcrepo(is, entities).getId();
        }

        // Find the Submission entity that was uploaded
        for (URI key : entities.keySet()) {
            PassEntity entity = entities.get(key);
            if (entity.getId() == submissionUri) {
                submissionEntity = (Submission) entity;
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
        assertNotNull(submission.getSubmissionMeta());

        // Cannot compare ID strings, as they change when uploading to a Fedora server.
        Publication publication = (Publication) entities.get(submissionEntity.getPublication());
        assertEquals(EXPECTED_DOI, submission.getMetadata().getArticleMetadata().getDoi().toString());

        assertNotNull(submission.getFiles());
        assertEquals(8, submission.getFiles().size());

        // Confirm that some values were set correctly from the Submission metadata
        DepositMetadata.Journal journalMetadata = submission.getMetadata().getJournalMetadata();
        assertEquals(EXPECTED_JOURNAL_TITLE, journalMetadata.getJournalTitle());

        EXPECTED_ISSNS.values().forEach(expectedIssnPubType -> {
            journalMetadata.getIssnPubTypes().values().stream()
                           .filter(candidate ->
                                       candidate.equals(expectedIssnPubType))
                           .findAny().orElseThrow(() ->
                                                      new RuntimeException(
                                                          "Missing expected IssnPubType " + expectedIssnPubType));
        });
        assertEquals(EXPECTED_ISSNS.size(), journalMetadata.getIssnPubTypes().size());

        assertEquals(EXPECTED_NLMTA, journalMetadata.getJournalId());

        DepositMetadata.Manuscript manuscriptMetadata = submission.getMetadata().getManuscriptMetadata();
        assertNull(manuscriptMetadata.getManuscriptUrl());

//        assertTrue(submission.getMetadata().getArticleMetadata().getUnderEmbargo());
        assertEquals(EXPECTED_EMBARGO_END_DATE, submission.getMetadata().getArticleMetadata().getEmbargoLiftDate()
                                                          .format(DateTimeFormatter.ofPattern("uuuu-MM-dd")));

        List<DepositMetadata.Person> persons = submission.getMetadata().getPersons();
        assertEquals(EXPECTED_SUBMITER_COUNT, persons.stream()
                                                     .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.submitter)
                                                     .count());
        assertEquals(EXPECTED_PI_COUNT, persons.stream()
                                               .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.pi).count());
        assertEquals(EXPECTED_CO_PI_COUNT, persons.stream()
                                                  .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.copi)
                                                  .count());
        assertEquals(EXPECTED_AUTHOR_COUNT, persons.stream()
                                                   .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.author)
                                                   .count());

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Tania Marchbank")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Nikki Mandir")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Denis Calnan")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Robert A. Goodlad")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Theo Podas")));

        assertTrue(persons.stream()
                          .filter(person -> person.getType() == DepositMetadata.PERSON_TYPE.author)
                          .anyMatch(author ->
                                        author.getName().equals("Raymond J. Playford")));

        // Read something out of the submission metadata
        assertTrue(submission.getSubmissionMeta().has("agreements"));
        JsonObject agreement = submission.getSubmissionMeta().getAsJsonObject("agreements");
        assertTrue(agreement.has("JScholarship"));
    }

    @After
    public void tearDown() {
        // Clean up the server
        adapter.deleteFromFcrepo(entities);
    }

}