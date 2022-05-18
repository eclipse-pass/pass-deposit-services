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
package org.dataconservancy.pass.deposit.model;

import java.util.Arrays;

/**
 * Journal type: electronic or print
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public enum JournalPublicationType {

    PPUB("Print"),

    /**
     * No longer represented in the metadata blob.  Replaced by {@link #OPUB} with the advent of the JHU Global schema.
     * NIHMS metadata serialization still uses this publication type when serializing metadata.
     */
    EPUB("Electronic"),

    /**
     * New with JHU Global schema.
     * NIHMS metadata serialization will translate OPUB type descriptions to EPUB when serializing metadata.
     */
    OPUB("Online");

    private String typeDescription;

    JournalPublicationType(String typeDescription) {
        this.typeDescription = typeDescription;
    }

    public String getTypeDescription() {
        return typeDescription;
    }

    /**
     * Parse an {@code Extension} from the string form as it would be used in a file name.  Note that this form should
     * be all lower-case, with no preceding or succeeding periods.
     *
     * @param typeDescription the string form of a journal publication type
     * @return the corresponding {@code JournalPublicationType}
     * @throws IllegalArgumentException if the {@code typeDescription} does not correspond to an existing {@code
     * JournalPublicationType}
     */
    public static JournalPublicationType parseTypeDescription(String typeDescription) {
        return Arrays.stream(values())
                     .filter(candidatePubType -> candidatePubType.getTypeDescription().equals(typeDescription))
                     .findAny()
                     .orElseThrow(() -> new IllegalArgumentException(
                         "No JournalPublicationType exists for '" + typeDescription + "'"));
    }

    @Override
    public String toString() {
        return "JournalPublicationType{" + "typeDescription='" + typeDescription + '\'' + "} " + super.toString();
    }
}
