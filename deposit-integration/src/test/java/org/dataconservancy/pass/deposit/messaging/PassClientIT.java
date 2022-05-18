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

package org.dataconservancy.pass.deposit.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.messaging.config.spring.DrainQueueConfig;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(DrainQueueConfig.class)
@DirtiesContext
public class PassClientIT {

    @Autowired
    private PassClient underTest;

    @Test
    public void createDeposit() throws Exception {
        Submission s = new Submission();
        URI submissionUri = underTest.createResource(s);

        Repository r = new Repository();
        r.setName("Repository Name");
        URI repoUri = underTest.createResource(r);

        assertNotNull("Expected a non-null Submission uri", submissionUri);
        assertNotNull("Expected a non-null Repository uri", repoUri);

        Deposit d = new Deposit();
        d.setRepository(repoUri);
        d.setSubmission(submissionUri);
        URI depositUri = underTest.createResource(d);

        assertNotNull("Expected a non-null Deposit uri", depositUri);

        d = underTest.readResource(depositUri, Deposit.class);
        assertNotNull("Unable to retrieve Deposit at " + depositUri, d);

        assertEquals(repoUri, d.getRepository());
    }
}
