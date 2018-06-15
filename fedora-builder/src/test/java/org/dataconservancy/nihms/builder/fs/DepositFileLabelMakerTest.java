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

package org.dataconservancy.nihms.builder.fs;

import org.dataconservancy.nihms.model.DepositFileType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DepositFileLabelMakerTest {

    @Test
    public void labelTest() {
        DepositFileLabelMaker underTest = new DepositFileLabelMaker();

        String label = underTest.label(DepositFileType.figure, null);
        assertEquals("figure-1", label);

        label = underTest.label(DepositFileType.figure, null);
        assertEquals("figure-2", label);

        label = underTest.label(DepositFileType.figure, "   ");
        assertEquals("figure-3", label);

        label = underTest.label(DepositFileType.supplemental, "figure-1");
        assertEquals("figure-1", label);

        label = underTest.label(DepositFileType.manuscript, "Moo Cows in the Pasture");
        assertEquals("", label);

        label = underTest.label(DepositFileType.figure, "Spotted Cows");
        assertEquals("Spotted Cows", label);

        label = underTest.label(DepositFileType.figure, "Spotted Cows");
        assertEquals("Spotted Cows-1", label);

        label = underTest.label(DepositFileType.figure, "Spotted Cows     ");
        assertEquals("Spotted Cows-2", label);

        label = underTest.label(DepositFileType.figure, "Spotted Cows-2");
        assertEquals("Spotted Cows-2-1", label);

        label = underTest.label(DepositFileType.table, "table");
        assertEquals("table", label);

        label = underTest.label(DepositFileType.table, "table-1");
        assertEquals("table-1", label);

        label = underTest.label(DepositFileType.table, "");
        assertEquals("table-2", label);

    }
}
