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

import static org.dataconservancy.pass.support.messaging.constants.Constants.SWORD.SWORD_STATE;

import java.net.URI;

import org.apache.abdera.model.Category;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Parses the supplied Atom document for a Category for {@code http://purl.org/net/sword/terms/state}, and returns
     * the term value (a URI representing the status of the SWORD deposit).
     *
     * @param statementDoc the Atom document representing a SWORD v2 Statement
     * @return the SWORD deposit status, or {@code null} if none is found
     * @throws IllegalArgumentException if the term value cannot be parsed as a URI
     */
    public static URI parseSwordState(Document<Feed> statementDoc) {
        Category category = statementDoc.getRoot().getCategories(SWORD_STATE).stream()
                                        .findFirst().orElse(null);

        if (category != null) {
            try {
                return URI.create(category.getTerm());
            } catch (IllegalArgumentException e) {
                // An unknown term value, which is exceptional
                LOG.error("Unable to resolve the term value for Atom <feed>/<category> scheme " + SWORD_STATE + " " +
                          "as a URI: " + category.getTerm());
                throw e;
            }
        }

        return null;
    }
}
