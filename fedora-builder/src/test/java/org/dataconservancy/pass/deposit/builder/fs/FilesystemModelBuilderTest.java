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

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static submissions.SubmissionResourceUtil.lookupStream;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositMetadata;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.model.JournalPublicationType;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Before;
import org.junit.Test;
import submissions.SubmissionResourceUtil;

public class FilesystemModelBuilderTest {

    private static final String EXPECTED_TITLE = "Food & Function";

    private static final String EXPECTED_PUB_DATE = "2018-09-12";

    private static final DateTime EXPECTED_SUBMITTED_DATE =
        DateTime.parse("2017-06-02T00:00:00.000Z",
                       DateTimeFormat.forPattern("YYYY-M-d'T'H':'m':'s'.'SSSZ").withZoneUTC());

    private static final Map<String, JournalPublicationType> EXPECTED_ISSN_PUBTYPES =
        new HashMap<String, JournalPublicationType>() {
            {
                put("2042-6496", JournalPublicationType.PPUB);
                put("2042-650X", JournalPublicationType.OPUB);
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

    private static final URI SAMPLE_SUBMISSION_NO_ISSN = URI.create("fake:submission15");

    private static final URI SAMPLE_SUBMISSION_INCOMPLETE_ISSN = URI.create("fake:submission16");

    private SubmissionResourceUtil submissionUtil;

    @Before
    public void setup() throws Exception {
        //submissionUtil = new SubmissionResourceUtil();

        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_RESOURCE), emptyMap());
    }

    @Test
    public void testElementValues() throws Exception {
        // Load the PassEntity version of the sample data file
        Submission submissionEntity = null;
        HashMap<URI, PassEntity> entities = new HashMap<>();

        try (InputStream is = lookupStream(SAMPLE_SUBMISSION_RESOURCE)) {
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
        assertEquals(EXPECTED_SUBMITTED_DATE, submission.getSubmissionDate());

        assertNotNull(submission.getFiles());
        assertEquals(8, submission.getFiles().size());
        // One of the uris is expected to contain an encoded space
        assertTrue(submission.getFiles().stream().anyMatch(f -> f.getLocation().contains("%20")));

        // Confirm that some values were set correctly from the Submission metadata
        DepositMetadata.Journal journalMetadata = submission.getMetadata().getJournalMetadata();
        assertEquals(EXPECTED_TITLE, journalMetadata.getJournalTitle());
        assertEquals(EXPECTED_PUB_DATE, journalMetadata.getPublicationDate());
        assertEquals(2, journalMetadata.getIssnPubTypes().size());
        journalMetadata.getIssnPubTypes().values().forEach(pubType -> {
            assertTrue(EXPECTED_ISSN_PUBTYPES.containsKey(pubType.issn));
            assertEquals(EXPECTED_ISSN_PUBTYPES.get(pubType.issn), pubType.pubType);
        });

        assertEquals(EXPECTED_NLMTA, journalMetadata.getJournalId());

        DepositMetadata.Manuscript manuscriptMetadata = submission.getMetadata().getManuscriptMetadata();
        assertNull(manuscriptMetadata.getManuscriptUrl());

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

    @Test
    public void buildWithNoIssnInfo() throws Exception {
        // Create submission data with no ISSNs: there shouldn't be any ISSNs in the submission
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_NO_ISSN), emptyMap());
        assertNotNull(submission);
        assertEquals(Collections.emptyMap(), submission.getMetadata().getJournalMetadata().getIssnPubTypes());
    }

    @Test
    public void buildWithIncompleteIssnInfo() throws InvalidModel {
        // Create submission data with incomplete ISSNs: there should be one ISSN in the submission for 2042-650X
        submission = underTest.build(lookupStream(SAMPLE_SUBMISSION_INCOMPLETE_ISSN), emptyMap());
        assertNotNull(submission);

        assertEquals(1, submission.getMetadata().getJournalMetadata().getIssnPubTypes().size());
        assertEquals(JournalPublicationType.OPUB,
                     submission.getMetadata().getJournalMetadata().getIssnPubTypes().get("2042-650X").pubType);
    }

    /**
     * Insures that an ISSN must be present with its publication type in order to be parsed into {@link
     * DepositMetadata.Journal}.
     * <p>
     * An ISSN with an ISSN number but without a publication type is not parsed into the Journal metadata.  Likewise,
     * an ISSN with a publication type but without an ISSN number is not parsed into Journal metadata.
     * </p>
     *
     * @throws InvalidModel shouldn't happen
     */
    @Test
    public void processMetadataIncompleteIssn() throws InvalidModel {
        /* Testing an "issns" array like:
        "issns": [
            {
              "pubType": "Print"
            },
            {
              "issn": "2042-650X"
            }
          ]
         */
        String issns = "{\"issns\": [ { \"pubType\": \"Print\" }, { \"issn\": \"2042-650X\" } ]}";
        DepositMetadata depositMetadata = new DepositMetadata();

        underTest.processMetadata(depositMetadata, issns);
        assertTrue(depositMetadata.getJournalMetadata().getIssnPubTypes().isEmpty());
    }

    /**
     * Insures that an ISSN must be present with its publication type in order to be parsed into {@link
     * DepositMetadata.Journal}.
     * <p>
     * An ISSN with an ISSN number but without a publication type is not parsed into the Journal metadata.  Likewise,
     * an ISSN with a publication type but without an ISSN number is not parsed into Journal metadata.
     * </p>
     *
     * @throws InvalidModel shouldn't happen
     */
    @Test
    public void processMetadataMixedIssn() throws InvalidModel {
        /* Testing an "issns" array like:
        "issns": [
            {
              "pubType": "Print"
            },
            {
              "issn": "2042-650X", "pubType": "Online"
            }
          ]
         */
        String issns = "{ \"issns\": [ { \"pubType\": \"Print\" }, { \"issn\": \"2042-650X\", \"pubType\": \"Online\"" +
                       " } ] }";
        DepositMetadata depositMetadata = new DepositMetadata();

        underTest.processMetadata(depositMetadata, issns);
        assertEquals(1, depositMetadata.getJournalMetadata().getIssnPubTypes().size());
        assertEquals(JournalPublicationType.parseTypeDescription("Online"),
                     depositMetadata.getJournalMetadata().getIssnPubTypes().get("2042-650X").pubType);
    }

    /**
     * Insures that an ISSN must be present with its publication type in order to be parsed into {@link
     * DepositMetadata.Journal}.
     * <p>
     * An ISSN with an ISSN number but without a publication type is not parsed into the Journal metadata.  Likewise,
     * an ISSN with a publication type but without an ISSN number is not parsed into Journal metadata.
     * </p>
     *
     * @throws InvalidModel shouldn't happen
     */
    @Test
    public void processMetadataMixedIssn2() throws InvalidModel {
        /* Testing an "issns" array like:
        "issns": [
            {
              "issn": "2042-650X"
            },
            {
              "issn": "2042-650X", "pubType": "Online"
            }
          ]
         */
        String issns = "{ \"issns\": [ { \"pubType\": \"Print\" }, { \"issn\": \"2042-650X\", \"pubType\": \"Online\"" +
                       " } ] }";
        DepositMetadata depositMetadata = new DepositMetadata();

        underTest.processMetadata(depositMetadata, issns);
        assertEquals(1, depositMetadata.getJournalMetadata().getIssnPubTypes().size());
        assertEquals(JournalPublicationType.parseTypeDescription("Online"),
                     depositMetadata.getJournalMetadata().getIssnPubTypes().get("2042-650X").pubType);
    }

    /**
     * Insures that parsing an empty array of ISSNs doesn't cause an exception.
     *
     * @throws InvalidModel shouldn't happen
     */
    @Test
    public void emptyIssns() throws InvalidModel {
        String issns = "{ \"title\": \"foo\", \"issns\": [ ] }";
        DepositMetadata depositMetadata = new DepositMetadata();

        underTest.processMetadata(depositMetadata, issns);
        assertTrue(depositMetadata.getJournalMetadata().getIssnPubTypes().isEmpty());

        // insure that *something* parsed
        assertEquals("foo", depositMetadata.getArticleMetadata().getTitle());
    }

    /**
     * Insure that processing of an unknown publication type does not halt processing.  The ISSN with the unknown
     * publication type should be ignored.
     *
     * @throws InvalidModel shouldn't happen
     */
    @Test
    public void unknownPublicationType() throws InvalidModel {
        // Insure the "Unknown" type is actually unknown
        String UNKNOWN_TYPE = "Unknown";
        try {
            JournalPublicationType.parseTypeDescription(UNKNOWN_TYPE);
            fail("Must use a publication type that is not known to " + JournalPublicationType.class);
        } catch (Exception e) {
            // expected
        }
        String issns =
            "{ \"title\": \"foo\", \"issns\": [ { \"issn\": \"2042-650X\", \"pubType\": \"" + UNKNOWN_TYPE + "\" } ] }";
        DepositMetadata depositMetadata = new DepositMetadata();

        underTest.processMetadata(depositMetadata, issns);
        assertTrue(depositMetadata.getJournalMetadata().getIssnPubTypes().isEmpty());

        // insure that *something* parsed
        assertEquals("foo", depositMetadata.getArticleMetadata().getTitle());
    }
}