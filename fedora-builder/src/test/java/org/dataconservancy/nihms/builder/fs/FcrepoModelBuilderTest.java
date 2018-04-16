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

import org.dataconservancy.nihms.model.DepositSubmission;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;

public class FcrepoModelBuilderTest {

    private DepositSubmission submission;
    private FcrepoModelBuilder underTest = new FcrepoModelBuilder();
    private String sampleDataFile = "SampleSubmissionData.json";

    @Before
    public void setup() throws Exception {
        // Upload sample data to Fedora repository to get its URI.
        final PassJsonFedoraAdapter reader = new PassJsonFedoraAdapter();
        URL sampleDataUrl = FcrepoModelBuilderTest.class.getClassLoader().getResource(sampleDataFile);
        InputStream is = new FileInputStream(sampleDataUrl.getPath());
        final URI submissionUri = reader.jsonToFcrepo(is);
        is.close();
        submission = underTest.build(submissionUri.toString());
    }

    @Test
    public void testElementValues() {
        // Load the PassEntity version of the sample data file
        Submission submissionEntity = null;
        try {
            URL sampleDataUrl = FilesystemModelBuilderTest.class.getClassLoader().getResource(sampleDataFile);
            final InputStream is = new FileInputStream(sampleDataUrl.getPath());
            final PassJsonFedoraAdapter reader = new PassJsonFedoraAdapter();
            final HashMap<URI, PassEntity> entities = new HashMap<>();
            submissionEntity = reader.jsonToPass(is, entities);
            is.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail("Could not load sample data file");
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not close the sample data file");
        }

        // Until the data model is finalized, just check that some basic things are in order
        assertNotNull(submission.getManifest());
        assertNotNull(submission.getMetadata());
        assertNotNull(submission.getMetadata().getManuscriptMetadata());
        assertNotNull(submission.getMetadata().getJournalMetadata());
        assertNotNull(submission.getMetadata().getArticleMetadata());
        assertNotNull(submission.getFiles());
        assertNotNull(submission.getMetadata().getPersons());

        // Cannot compare ID strings, as they change when uploading to a Fedora server.
        assertEquals(submission.getMetadata().getManuscriptMetadata().getTitle(), submissionEntity.getTitle());
        assertEquals(submission.getMetadata().getArticleMetadata().getDoi().toString(), submissionEntity.getDoi());
    }

}