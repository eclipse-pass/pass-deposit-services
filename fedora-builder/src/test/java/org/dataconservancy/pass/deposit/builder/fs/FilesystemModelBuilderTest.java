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

package org.dataconservancy.pass.deposit.builder.fs;

import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositMetadata;
import org.dataconservancy.pass.deposit.model.DepositSubmission;

import org.dataconservancy.pass.deposit.model.JournalPublicationType;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Test;
import submissions.SharedResourceUtil;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static submissions.SharedResourceUtil.lookupStream;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FilesystemModelBuilderTest {

    private static final String EXPECTED_TITLE = "Food & Function";

    private static final Map<String, JournalPublicationType> EXPECTED_ISSN_PUBTYPES =
            new HashMap<String, JournalPublicationType>() {
                {
                    put("2042-6496", JournalPublicationType.PPUB);
                    put("2042-650X", JournalPublicationType.EPUB);
                }
            };

    private static final String EXPECTED_DOI = "10.1039/c7fo01251a";

    private static final int EXPECTED_SUBMITER_COUNT = 1;

    private static final int EXPECTED_PI_COUNT = 1;

    private static final int EXPECTED_CO_PI_COUNT = 2;

    private static final int EXPECTED_AUTHOR_COUNT = 6;

    private static final String EXPECTED_NLMTA = "Food Funct";

    private DepositSubmission submission;

    private FilesystemModelBuilder underTest = new FilesystemModelBuilder();

    private static final URI SAMPLE_SUBMISSION_RESOURCE = URI.create("fake:submission1");

    private static final URI SAMPLE_SUBMISSION_RESOURCE_NULL_FIELDS = URI.create("fake:submission7");

    private static final URI SAMPLE_SUBMISSION_RESOURCE_MISSING_FIELDS = URI.create("fake:submission5");

    private static final URI SAMPLE_SUBMISSION_RESOURCE_NULL_DOI = URI.create("fake:submission8");

    private static final URI SAMPLE_SUBMISSION_RESOURCE_MISSING_DOI = URI.create("fake:submission6");

    private static final URI SAMPLE_SUBMISSION_RESOURCE_UNTRIMMED_DOI = URI.create("fake:submission9");

    private static final URI SAMPLE_SUBMISSION_RESOURCE_TABLE_AND_FIGURE = URI.create("fake:submission4");

    private SharedResourceUtil submissionUtil;

    @Before
    public void setup() throws Exception{
        submissionUtil = new SharedResourceUtil();

        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE), emptyMap());
    }

    @Test
    public void testElementValues() throws Exception {
        // Load the PassEntity version of the sample data file
        Submission submissionEntity = null;
        HashMap<URI, PassEntity> entities = new HashMap<>();

        try (InputStream is = lookupStream(SAMPLE_SUBMISSION_RESOURCE)){
            PassJsonFedoraAdapter reader = new PassJsonFedoraAdapter();
            submissionEntity = reader.jsonToPass(is, entities);
        }

        // Check that some basic things are in order
        assertNotNull(submission.getManifest());
        assertNotNull(submission.getMetadata());
        assertNotNull(submission.getMetadata().getManuscriptMetadata());
        assertNotNull(submission.getMetadata().getJournalMetadata());
        assertNotNull(submission.getMetadata().getArticleMetadata());
        assertNotNull(submission.getMetadata().getPersons());

        assertEquals(submission.getId(), submissionEntity.getId().toString());
        assertEquals(EXPECTED_DOI, submission.getMetadata().getArticleMetadata().getDoi().toString());

        assertNotNull(submission.getFiles());
        assertEquals(8, submission.getFiles().size());
        // One of the uris is expected to contain an encoded space
        assertTrue(submission.getFiles().stream().anyMatch(f -> f.getLocation().contains("%20")));

        // Confirm that some values were set correctly from the Submission metadata
        DepositMetadata.Journal journalMetadata = submission.getMetadata().getJournalMetadata();
        assertEquals(EXPECTED_TITLE, journalMetadata.getJournalTitle());
        journalMetadata.getIssnPubTypes().values().forEach(pubType -> {
            assertTrue(EXPECTED_ISSN_PUBTYPES.containsKey(pubType.issn));
            assertEquals(EXPECTED_ISSN_PUBTYPES.get(pubType.issn), pubType.pubType);
        });

        assertEquals(EXPECTED_NLMTA, journalMetadata.getJournalId());

        DepositMetadata.Manuscript manuscriptMetadata = submission.getMetadata().getManuscriptMetadata();
        assertNull(manuscriptMetadata.getManuscriptUrl());

        List<DepositMetadata.Person> persons = submission.getMetadata().getPersons();
        assertEquals(EXPECTED_SUBMITER_COUNT,persons.stream()
                .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.submitter).count());
        assertEquals(EXPECTED_PI_COUNT,persons.stream()
                .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.pi).count());
        assertEquals(EXPECTED_CO_PI_COUNT,persons.stream()
                .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.copi).count());
        assertEquals(EXPECTED_AUTHOR_COUNT,persons.stream()
                .filter(p -> p.getType() == DepositMetadata.PERSON_TYPE.author).count());
    }

    @Test
    public void buildWithNullValues() throws Exception {
        // Create submission data from sample data file with null values
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE_NULL_FIELDS), emptyMap());

        assertNotNull(submission);
        assertNull(submission.getMetadata().getManuscriptMetadata().getTitle());
        assertNull(submission.getMetadata().getManuscriptMetadata().getMsAbstract());
    }

    @Test
    public void buildWithMissingValues() throws Exception {
        // Create submission data from sample data file with missing fields
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE_MISSING_FIELDS), emptyMap());

        assertNotNull(submission);
        assertNull(submission.getMetadata().getManuscriptMetadata().getTitle());
        assertNull(submission.getMetadata().getManuscriptMetadata().getMsAbstract());
    }

    @Test
    public void buildWithNullDoi() throws Exception {
        // Create submission data from sample data file with null doi
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE_NULL_DOI), emptyMap());

        assertNotNull(submission);
        assertNull(submission.getMetadata().getArticleMetadata().getDoi());
    }

    @Test
    public void buildWithMissingDoi() throws Exception {
        // Create submission data from sample data file with missing doi
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE_MISSING_DOI), emptyMap());

        assertNotNull(submission);
        assertNull(submission.getMetadata().getArticleMetadata().getDoi());
    }

    @Test
    public void buildWithUntrimmedDoi() throws Exception {
        // Create submission data from sample data file with whitespace around the doi
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE_UNTRIMMED_DOI), emptyMap());

        assertNotNull(submission);
        URI doi = submission.getMetadata().getArticleMetadata().getDoi();
        assertNotNull(doi);
        assertFalse(doi.toString().startsWith(" "));
        assertFalse(doi.toString().endsWith(" "));
    }

    @Test
    public void buildWithTableAndFigure() throws Exception {
        // Create submission data from sample data file with table and figure files
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE_TABLE_AND_FIGURE), emptyMap());

        assertNotNull(submission);
        assertNotNull(submission.getFiles());
        assertEquals(4, submission.getFiles().size());
        List<DepositFileType> types = new ArrayList<>();
        for (DepositFile file : submission.getFiles()) {
            types.add(file.getType());
        }

        assertTrue(types.contains(DepositFileType.figure));
        assertTrue(types.contains(DepositFileType.supplement));
        assertTrue(types.contains(DepositFileType.table));
        assertTrue(types.contains(DepositFileType.manuscript));
    }
}