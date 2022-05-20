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
package org.dataconservancy.pass.deposit.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class JournalPublicationTypeTest {

    @Test
    public void parseOnline() {
        assertEquals(JournalPublicationType.OPUB, JournalPublicationType.parseTypeDescription("Online"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseOnlineLowerCase() {
        JournalPublicationType.parseTypeDescription("online");
    }

    @Test
    public void parsePrint() {
        assertEquals(JournalPublicationType.PPUB, JournalPublicationType.parseTypeDescription("Print"));
    }

    @Test
    public void parseElectronic() {
        assertEquals(JournalPublicationType.EPUB, JournalPublicationType.parseTypeDescription("Electronic"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUnknown() {
        JournalPublicationType.parseTypeDescription("asdf");
    }
}