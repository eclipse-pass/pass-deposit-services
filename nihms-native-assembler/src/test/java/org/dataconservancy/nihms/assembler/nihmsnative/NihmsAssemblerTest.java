/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.nihms.assembler.nihmsnative;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NihmsAssemblerTest {

    /**
     * Insures that that {@link NihmsAssembler#sanitize(String)} filters out non alphanumeric characters, and non
     * latin-1 characters.
     */
    @Test
    public void testSanitize() {
        assertEquals("foo", NihmsAssembler.sanitize("foo"));
        assertEquals("foo", NihmsAssembler.sanitize("f.o.o"));
        assertEquals("foo", NihmsAssembler.sanitize("foo" + '\u00F6'));
        assertEquals("foo", NihmsAssembler.sanitize("../foo"));
        assertEquals("foo", NihmsAssembler.sanitize("f o o"));
    }
}