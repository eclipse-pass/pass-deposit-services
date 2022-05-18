/*
 * Copyright 2020 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.messaging.config.repository;

import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.HINT_TUPLE_SEPARATOR;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.HINT_URL_SEPARATOR;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SwordV2BindingTest {

    private SwordV2Binding underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new SwordV2Binding();
    }

    @Test
    public void hintsToPropertyString() {
        String covid = "https://jscholarship.library.jhu.edu/handle/1774.2/58585";
        String nobel = "https://jscholarship.library.jhu.edu/handle/1774.2/33532";
        Map<String, String> hints = new HashMap<String, String>() {
            {
                put("covid", covid);
                put("nobel", nobel);
            }
        };

        String expected = "covid" + HINT_URL_SEPARATOR + covid + HINT_TUPLE_SEPARATOR +
                          "nobel" + HINT_URL_SEPARATOR + nobel;

        assertEquals(expected, underTest.hintsToPropertyString(hints));
        assertEquals(2, underTest.hintsToPropertyString(hints).split(" ").length);
    }

    @Test
    public void hintsToPropertyStringTrailingOrLeadingSpace() {
        String leading = " https://jscholarship.library.jhu.edu/handle/1774.2/58585";
        String trailing = "https://jscholarship.library.jhu.edu/handle/1774.2/33532 ";
        Map<String, String> hints = new HashMap<String, String>() {
            {
                put("covid", leading);
                put("nobel", trailing);
            }
        };

        String expected = "covid" + HINT_URL_SEPARATOR + leading.trim() + HINT_TUPLE_SEPARATOR +
                          "nobel" + HINT_URL_SEPARATOR + trailing.trim();

        assertEquals(expected, underTest.hintsToPropertyString(hints));
        assertEquals(2, underTest.hintsToPropertyString(hints).split(" ").length);
    }

    @Test
    public void hintsToPropertyStringEncodedSpace() {
        String encodedSpace = "https://jscholarship.library.jhu.edu/handle/1774.2/58585?moo=%20cow%20";
        Map<String, String> hints = new HashMap<String, String>() {
            {
                put("covid", encodedSpace);
            }
        };

        String expected = "covid" + HINT_URL_SEPARATOR + encodedSpace;

        assertEquals(expected, underTest.hintsToPropertyString(hints));
        assertEquals(1, underTest.hintsToPropertyString(hints).split(" ").length);
    }

    @Test
    public void hintsToPropertyStringNullMap() {
        assertEquals("", underTest.hintsToPropertyString(null));
    }

    @Test
    public void hintsToPropertyStringEmptyMap() {
        assertEquals("", underTest.hintsToPropertyString(Collections.emptyMap()));
    }

}