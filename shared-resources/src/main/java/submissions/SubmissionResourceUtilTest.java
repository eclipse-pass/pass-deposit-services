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
package submissions;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionResourceUtilTest {

    @Test
    public void testSubmissionUriCount() throws Exception {
        assertTrue(SubmissionResourceUtil.submissionUris().size() >= 7);
    }

    @Test
    public void testResolveToUri() throws Exception {
        SubmissionResourceUtil.submissionUris().forEach(uri -> assertNotNull(SubmissionResourceUtil.lookupUri(uri)));
    }

    @Test
    public void testResolveToStream() throws Exception {
        SubmissionResourceUtil.submissionUris().forEach(uri -> assertNotNull(SubmissionResourceUtil.lookupStream(uri)));
    }
}