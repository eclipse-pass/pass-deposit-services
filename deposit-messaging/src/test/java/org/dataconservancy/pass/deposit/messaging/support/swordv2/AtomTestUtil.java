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

import java.io.InputStream;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.stax.FOMParserFactory;

/**
 * Utility methods for unit tests relating to Atom feeds.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomTestUtil {

    private AtomTestUtil() {
    }

    /**
     * Relies on implementation classes present in Abdera to resolve a classpath resource as an Atom feed.
     *
     * @param feedResource a classpath resource referencing an Atom feed
     * @return the parsed feed
     */
    public static Document<Feed> parseFeed(InputStream feedResource) {
        return new FOMParserFactory().getParser().parse(feedResource);
    }

}
