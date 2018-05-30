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
package org.dataconservancy.pass.deposit.messaging.support.swordv2;

import org.apache.abdera.model.Category;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.pass.deposit.messaging.support.Constants.SWORD.SWORD_STATE;

/**
 * Utility methods for parsing Atom documents
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomUtil {

    private static final Logger LOG = LoggerFactory.getLogger(AtomUtil.class);

    private AtomUtil() {

    }

    /**
     * Parses the supplied Atom document for a {@link SwordDspaceDepositStatus SWORD deposit status}.
     *
     * @param statementDoc the Atom document representing a SWORD v2 Statement
     * @return the SWORD deposit status, or {@code null} if none is found
     */
    public static SwordDspaceDepositStatus parseAtomStatement(Document<Feed> statementDoc) {
        Category category = statementDoc.getRoot().getCategories(SWORD_STATE).stream()
                .findFirst().orElse(null);

        if (category != null) {
            SwordDspaceDepositStatus dspaceDepositStatus = null;
            try {
                return SwordDspaceDepositStatus.parseUri(category.getTerm());
            } catch (IllegalArgumentException e) {
                // An unknown term value
                LOG.warn("Unknown term value for Atom <feed>/<category> scheme " + SWORD_STATE + ": " +
                        category.getTerm(), e);
                return null;
            }
        }

        return null;
    }
}
