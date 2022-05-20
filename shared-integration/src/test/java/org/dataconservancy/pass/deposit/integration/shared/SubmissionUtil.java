/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.integration.shared;

import static java.util.stream.Collectors.toSet;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.File;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionUtil {

    private SubmissionUtil() {
    }

    public static Collection<URI> getDepositUris(Submission submission, PassClient passClient) {
        return getIncomingUris(submission, passClient, Deposit.class);
    }

    public static Collection<URI> getFileUris(Submission submission, PassClient passClient) {
        return getIncomingUris(submission, passClient, File.class);
    }

    private static Collection<URI> getIncomingUris(Submission submission, PassClient passClient,
                                                   Class<? extends PassEntity> incomingResourceClass) {
        Map<String, Collection<URI>> incoming = passClient.getIncoming(submission.getId());
        if (!incoming.containsKey("submission")) {
            return Collections.emptySet();
        }

        return incoming.get("submission").stream().filter(uri -> {
            try {
                passClient.readResource(uri, incomingResourceClass);
                return true;
            } catch (Exception e) {
                return false;
            }
        }).collect(toSet());
    }
}
