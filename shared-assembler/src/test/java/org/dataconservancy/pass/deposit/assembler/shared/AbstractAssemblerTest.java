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

package org.dataconservancy.pass.deposit.assembler.shared;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AbstractAssemblerTest {

    /**
     * Insures that that {@link AbstractAssembler#sanitizeFilename(String)} filters out non alphanumeric characters,
     * and non
     * latin-1 characters.
     */
    @Test
    public void testSanitize() {
        assertEquals("foo", AbstractAssembler.sanitizeFilename("foo"));
        assertEquals("f.o.o", AbstractAssembler.sanitizeFilename("f.o.o"));
        assertEquals("foo%C3%B6", AbstractAssembler.sanitizeFilename("foo" + '\u00F6'));
        assertEquals("..%2Ffoo", AbstractAssembler.sanitizeFilename("../foo"));
        assertEquals("f%20o%20o", AbstractAssembler.sanitizeFilename("f o o"));
        assertEquals("foo-", AbstractAssembler.sanitizeFilename("foo-"));
        assertEquals("fo-o", AbstractAssembler.sanitizeFilename("fo-o"));
        assertEquals("f_oo", AbstractAssembler.sanitizeFilename("f_oo"));
        assertEquals("_foo_", AbstractAssembler.sanitizeFilename("_foo_"));
    }
}