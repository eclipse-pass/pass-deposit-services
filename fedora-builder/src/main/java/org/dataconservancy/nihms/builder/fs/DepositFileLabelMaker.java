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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A utility class for generating unique labels for manifest entries for {@code DepositFile}s.
 *
 * We are supporting Labels only for all file types which are required to have them.
 *
 * From page 1 the NIHMS Bulk Submission Speccification for Funding Agencies, July 2017
 *
 *  "{label} is a label to differentiate between files of one {file_type} in the system.
 *   This field is required for figure, table, and supplement file types.
 *  {label} is used to identify files sent,such as 2a, 2b, and so on.
 *  In the case of supplemental files, the string supplied here will be used as text for a hyperlink in the PMC manuscript.
 *
 * @author jrm@jhu.edu
 */

public class DepositFileLabelMaker {

    //these are the types for which we will supply labels
    private static final DepositFileType[] labeledTypes = new DepositFileType[] {
            DepositFileType.figure,
            DepositFileType.supplemental,
            DepositFileType.table
    };

    private static final Set<DepositFileType> types = new HashSet<>(Arrays.asList(labeledTypes));

    private static Map<DepositFileType, Set<String>> usedFileLabels = createLabelMap();

    private static  Map<DepositFileType, Set<String>> createLabelMap() {
        Map<DepositFileType, Set<String>> labelMap = new HashMap<>();
        for (DepositFileType fileType : types) {
            labelMap.put(fileType, new HashSet<>());
        }
        return labelMap;
    }

    /**
     * Return a unique label for a {@code DepositFile}. If the type of file is not to be labeled, return an empty string.
     *
     * @param type the {@code DepositFileType} of the {@code DepositFile} requesting a label
     * @param description the user-supplied description of the file
     * @return the type-unique file label if the type is to be labeled, an empty string otherwise.
     */
    public String label(DepositFileType type, String description) {

        if (!types.contains(type)) {
            return "";
        }

        //we need a label. do we have a usable description? if not, label stem will be the type string
        boolean generic = description == null || description.replaceAll("\\s", "").length() == 0;
        String label = generic ? type.toString(): description;
        label = label.replaceAll("\t", " ").trim();//tabs have semantic meaning in manifest file

        String firstTry = generic ? label + "-1" : label;
        if (!usedFileLabels.get(type).contains(firstTry)) {
            usedFileLabels.get(type).add(firstTry);
            return firstTry;
        }

        //our first try is already used, let's generate a free one
        int i = generic ? 2 : 1;//optimization :-)
        while (usedFileLabels.get(type).contains(label + "-" + Integer.toString(i))) {
            i++;
        }
        label = label + "-" + Integer.toString(i);
        usedFileLabels.get(type).add(label);
        return label;
    }
}