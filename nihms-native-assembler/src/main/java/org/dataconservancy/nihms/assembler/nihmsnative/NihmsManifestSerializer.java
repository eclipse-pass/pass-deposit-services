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
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositManifest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is a serializer for NihmsManifest which produces output conforming with the
 * NIHMS Bulk Submission Specifications for Publishers document. For each file in the manifest's file list,
 * we have a line
 *
 * {file_type}<tab>{label}<tab>{file_name}
 *
 * where the file type is one of
 *
 * “bulksub_meta_xml”, “manuscript”, “supplement”, “figure”, or “table”
 *
 * @author Jim Martino (jrm@jhu.edu)
 */

public class NihmsManifestSerializer implements StreamingSerializer{

    private DepositManifest manifest;

    public NihmsManifestSerializer(DepositManifest manifest) {
        this.manifest = manifest;
    }


    public InputStream serialize(){
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(os);

        DepositFileLabelMaker labelMaker = new DepositFileLabelMaker();
        for (DepositFile file : manifest.getFiles() ){
            writer.write(file.getType().toString());
            writer.append("\t");
            String label = labelMaker.getTypeUniqueLabel(file.getType(), file.getLabel());
            if (label != null) {
                writer.write(label);
            }
            writer.append("\t");
            String name = NihmsZippedPackageStream.getNonCollidingFilename(file.getName(), file.getType());
            writer.write(name);
            writer.append("\n");
        }

        // FIXME: Hack to include the bulk_meta.xml in the manifest if it wasn't included
        if (manifest.getFiles().stream().noneMatch(df -> df.getType() == DepositFileType.bulksub_meta_xml)) {
            includeBulkMetadataInManifest(writer, labelMaker);
        }

        writer.close();

        byte[] bytes = os.toByteArray();

        try (InputStream is = new ByteArrayInputStream(bytes)) {
            os.close();
            return is;
        } catch (IOException ioe) {
            throw new RuntimeException("Could not create Input Stream, or close Output Stream", ioe);
        }
    }

    protected static void includeBulkMetadataInManifest(PrintWriter writer, DepositFileLabelMaker labelMaker) {
        writer.write(DepositFileType.bulksub_meta_xml.name());
        writer.append("\t");
        writer.write(labelMaker.getTypeUniqueLabel(DepositFileType.bulksub_meta_xml, "Submission Metadata"));
        writer.append("\t");
        writer.write(NihmsZippedPackageStream.METADATA_ENTRY_NAME);
    }

    /**
     * A utility inner class for generating unique labels for manifest entries for {@code DepositFile}s.
     *
     * From page 1 of the NIHMS Bulk Submission Specification for Funding Agencies, July 2017
     *
     *  "{label} is a label to differentiate between files of one {file_type} in the system.
     *   This field is required for figure, table, and supplement file types.
     *  {label} is used to identify files sent,such as 2a, 2b, and so on.
     *  In the case of supplement files, the string supplied here will be used as text for a hyperlink in the PMC manuscript.

     * If the label is not required, we make sure that any supplied label has not been used for a file of that type yet.
     *
     * @author jrm@jhu.edu
     */

    class DepositFileLabelMaker {
        /**
         * The label types required by the NIHMS Bulk Submission Specifications for Funding Agencies, July 2017
         */
        private final DepositFileType[] requiredLabelTypes = {
                DepositFileType.figure,
                DepositFileType.table,
                DepositFileType.supplement
        };

        private final Set<DepositFileType> requiredTypes = new HashSet<>(Arrays.asList(requiredLabelTypes));

        private Map<DepositFileType, Set<String>> usedFileLabels = createLabelMap();

        /**
         * An initialization method to populate a Map which tracks used labels for any file type
         *
         * @return the label Map
         */
        private Map<DepositFileType, Set<String>> createLabelMap() {
            Map<DepositFileType, Set<String>> labelMap = new HashMap<>();
            for (DepositFileType fileType : Arrays.asList(DepositFileType.values())) {
                labelMap.put(fileType, new HashSet<>());
            }
            return labelMap;
        }

        /**
         * Return a unique label for a {@code DepositFile}. If the label is not required, we make sure that any
         * supplied label has not been used for a file of that type yet.
         *
         * @param type the {@code DepositFileType} of the {@code DepositFile} requesting a label
         * @param description the user-supplied description of the file
         * @return the type-unique file label if supplied or required
         */
        String getTypeUniqueLabel(DepositFileType type, String description) {

            String label;
            boolean missing = false;

            //first see if we have any content in the supplied description/label
            if (description == null || description.replaceAll("\\s", "").length() == 0) {
                description = "";
            }

            //tabs are used to separate fields in the manifest, so we can't have them in our string
            label = description.replaceAll("\t", " ").trim();

            //if the label is content-less, we can return it if not required, but must construct one if required
            if (label.length() == 0) {
                if (requiredTypes.contains(type)) {
                    label = type.toString();//we require a label for these files, let's build one
                    missing = true;
                } else {
                    return "";
                }
            }

            //we have a string as a candidate. if it is required and the initial label was empty,
            //we start with <type>-1 as a first try
            //otherwise, just use the supplied label.
            String firstTry = missing ? label + "-1" : label;
            if (!usedFileLabels.get(type).contains(firstTry)) {
                usedFileLabels.get(type).add(firstTry);
                return firstTry;
            }

            //uh-oh, our first try is already used, let's generate a free one
            int i = missing ? 2 : 1;//optimization :-)
            while (usedFileLabels.get(type).contains(label + "-" + Integer.toString(i))) {
                i++;
            }
            label = label + "-" + Integer.toString(i);
            usedFileLabels.get(type).add(label);
            return label;
        }
    }

}
