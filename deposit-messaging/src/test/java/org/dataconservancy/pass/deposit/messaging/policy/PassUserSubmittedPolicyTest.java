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
package org.dataconservancy.pass.deposit.messaging.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Ignore("TODO: Update to latest logic")
public class PassUserSubmittedPolicyTest {

    private PassUserSubmittedPolicy underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new PassUserSubmittedPolicy();
    }

    @Test
    public void nullSubmission() throws Exception {
        assertFalse(underTest.test(null));
    }

    @Test
    public void passSourceNotSubmitted() throws Exception {
        Submission s = new Submission();
        s.setSubmitted(false);
        s.setSource(Submission.Source.PASS);

        assertFalse(underTest.test(s));
    }

    @Test
    public void passSourceSubmitted() throws Exception {
        Submission s = new Submission();
        s.setSubmitted(true);
        s.setSource(Submission.Source.PASS);

        assertTrue(underTest.test(s));
    }

    @Test
    public void otherSourceSubmitted() throws Exception {
        Submission s = new Submission();
        s.setSubmitted(true);
        s.setSource(Submission.Source.OTHER);

        assertFalse(underTest.test(s));
    }

    @Test
    public void otherSourceNotSubmitted() throws Exception {
        Submission s = new Submission();
        s.setSubmitted(false);
        s.setSource(Submission.Source.OTHER);

        assertFalse(underTest.test(s));
    }

    @Test
    public void nullSourceNotSubmitted() throws Exception {
        Submission s = new Submission();
        s.setSubmitted(false);
        s.setSource(null);

        assertFalse(underTest.test(s));
    }

    @Test
    public void nullSourceSubmitted() throws Exception {
        Submission s = new Submission();
        s.setSubmitted(true);
        s.setSource(null);

        assertFalse(underTest.test(s));
    }
}
