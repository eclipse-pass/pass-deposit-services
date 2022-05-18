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
package org.dataconservancy.pass.deposit.messaging.status;

import java.net.URI;
import java.util.stream.Stream;

import org.dataconservancy.pass.support.messaging.constants.Constants;

/**
 * Possible states of a deposit to DSpace via SWORD.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public enum SwordDspaceDepositStatus {

    /**
     * Item has been accessioned by the archive, and is in the archive's custody
     */
    SWORD_STATE_ARCHIVED(Constants.SWORD.SWORD_STATE_ARCHIVED),

    /**
     * Item has been withdrawn by the archive, and is no longer in the archive's custody
     */
    SWORD_STATE_WITHDRAWN(Constants.SWORD.SWORD_STATE_WD),

    /**
     * Item is in the process of being archived, and is <em>not</em> in the archive's custody
     */
    SWORD_STATE_INPROGRESS(Constants.SWORD.SWORD_STATE_INPROGRESS),

    /**
     * Item is under review, prior to being archived, and is <em>not</em> in the archive's custody
     */
    SWORD_STATE_INREVIEW(Constants.SWORD.SWORD_STATE_INREVIEW);

    private URI uri;

    SwordDspaceDepositStatus(String uri) {
        this.uri = URI.create(uri);
    }

    /**
     * The URI representing the deposit status
     *
     * @return the uri
     */
    public URI asUri() {
        return uri;
    }

    /**
     * Parse a uri representing a deposit status
     *
     * @param uri a URI representing a deposit status
     * @return a {@code SwordDspaceDepositStatus}
     * @throws IllegalArgumentException if the {@code uri} does not identify a valid status
     */
    public static SwordDspaceDepositStatus parseUri(String uri) {
        return Stream.of(values()).filter(value -> value.asUri().toString().equals(uri)).findAny()
                     .orElseThrow(() -> new IllegalArgumentException("Unknown deposit status '" + uri + "'"));
    }
}
