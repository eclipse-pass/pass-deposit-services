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
package org.dataconservancy.pass.deposit;

import static org.dataconservancy.pass.model.Submission.SubmissionStatus.SUBMITTED;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.nihms.integration.BaseIT;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassClientDefault;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.User;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class IndexSmokeIT extends BaseIT {

    private static final Logger LOG = LoggerFactory.getLogger(IndexSmokeIT.class);

    private final String PASS_FEDORA_USER = "pass.fedora.user";

    private final String PASS_FEDORA_PASSWORD = "pass.fedora.password";

    private final String PASS_FEDORA_BASEURL = "pass.fedora.baseurl";

    private final String PASS_ES_URL = "pass.elasticsearch.url";

    private PassClient passClient;

    @Before
    public void setUp() throws Exception {
        assertTrue("Missing expected system property " + PASS_FEDORA_USER,
                   System.getProperties().containsKey(PASS_FEDORA_USER));
        assertTrue("Missing expected system property " + PASS_FEDORA_PASSWORD,
                   System.getProperties().containsKey(PASS_FEDORA_PASSWORD));
        assertTrue("Missing expected system property " + PASS_FEDORA_BASEURL,
                   System.getProperties().containsKey(PASS_FEDORA_BASEURL));
        assertTrue("Missing expected system property " + PASS_ES_URL,
                   System.getProperties().containsKey(PASS_ES_URL));

        passClient = new PassClientDefault();
    }

    @Test
    public void smokeTestIndex() throws Exception {
        // put some objects in pass and query the index for their presence

        User user = new User();
        user.setAffiliation(Collections.singleton("School of Hard Knocks"));
        user.setFirstName("Mike");
        user.setLastName("Tyson");
        user.setDisplayName("Mike Tyson");
        user.setEmail("lights_out@gmail.com");
        user.setUsername("mtyson1");

        LOG.debug(">>>> Creating user {}", user);
        URI userUri = passClient.createResource(user);

        LOG.debug(">>>> Waiting for user {} to appear in index", userUri);

        Condition<URI> userCondition = new Condition<>(
            () -> passClient.findByAttribute(User.class, "@id", userUri),
            "Poll index for User.");

        assertTrue(userCondition.awaitAndVerify((uri) -> uri.getPath().equals(userUri.getPath())));

        Repository nih = new Repository();
        nih.setName("NIHMS");
        nih.setDescription("NIHMS Repository");

        LOG.debug(">>>> Creating Repository {}", nih);
        URI repoNihUri = passClient.createResource(nih);

        Repository js = new Repository();
        js.setName("JScholarship");
        js.setDescription("Johns Hopkins DSpace Repository");

        LOG.debug(">>>> Creating Repository {}", js);
        URI repoJsUri = passClient.createResource(js);

        LOG.debug(">>>> Waiting for repo {} to appear in index", repoNihUri);
        Condition<URI> repoCondition = new Condition<>(
            () -> passClient.findByAttribute(Repository.class, "@id", repoNihUri),
            "Poll index for repo");

        assertTrue(repoCondition.awaitAndVerify((uri) -> uri.getPath().equals(repoNihUri.getPath())));

        LOG.debug(">>>> Waiting for repo {} to appear in index", repoJsUri);
        repoCondition = new Condition<>(() -> passClient.findByAttribute(Repository.class, "@id", repoJsUri),
                                        "Poll index for repo");

        assertTrue(repoCondition.awaitAndVerify((uri) -> uri.getPath().equals(repoJsUri.getPath())));

        Submission submission = new Submission();
        submission.setSource(Submission.Source.PASS);
        submission.setSubmitted(Boolean.TRUE);
        submission.setSubmitter(userUri);
        submission.setSubmissionStatus(SUBMITTED);
        submission.setMetadata("{ \"key\": \"value\" }");
        submission.setRepositories(Arrays.asList(repoNihUri, repoJsUri));

        LOG.debug(">>>> Creating Submission {}", submission);
        URI submissionUri = passClient.createResource(submission);

        Condition<URI> submissionCondition = new Condition<>(() -> passClient.findByAttribute(Submission.class, "@id"
            , submissionUri), "Poll index for Submission.");

        LOG.debug(">>>> Waiting for submission {} to appear in index", submissionUri);

        assertTrue(submissionCondition.awaitAndVerify((uri) -> uri.getPath().equals(submissionUri.getPath())));
    }
}
