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
package org.dataconservancy.pass.deposit.messaging.service;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertNotNull;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionTestUtil {
    public static Collection<URI> getDepositUris(Submission submission, PassClient passClient) {
        Map<String, Collection<URI>> incoming = passClient.getIncoming(submission.getId());
        return incoming.get("submission").stream().filter(uri -> {
            try {
                passClient.readResource(uri, Deposit.class);
                return true;
            } catch (Exception e) {
                return false;
            }
        }).collect(toSet());
    }

    public static InputStream getSubmissionResources(String resource) {
        InputStream is = EmptySubmissionIT.class.getResourceAsStream(resource);
        assertNotNull("Unable to resolve classpath resource " + resource, is);
        return is;
    }
}
