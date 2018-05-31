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
import java.util.HashMap;

public class FcrepoModelBuilderIT {

    private DepositSubmission submission;
    private FcrepoModelBuilder underTest = new FcrepoModelBuilder();
    private String SAMPLE_SUBMISSION_RESOURCE = "SampleSubmissionData.json";
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
        assertEquals(submission.getMetadata().getArticleMetadata().getDoi().toString(), publication.getDoi());

        assertNotNull(submission.getFiles());
        assertEquals(2, submission.getFiles().size());

        // Confirm that some values were set correctly from the Submission metadata
        DepositMetadata.Journal journalMetadata = submission.getMetadata().getJournalMetadata();
        assertEquals("Food Funct.", journalMetadata.getJournalTitle());
        assertEquals("TD452689", journalMetadata.getJournalId());
        assertEquals("2042-6496,2042-650X", journalMetadata.getIssn());

        DepositMetadata.Manuscript manuscriptMetadata = submission.getMetadata().getManuscriptMetadata();
        assertEquals("10.1039/c7fo01251a", manuscriptMetadata.getManuscriptUrl().toString());
    }

    @After
    public void tearDown() {
        // Clean up the server
        adapter.deleteFromFcrepo(entities);
    }

}