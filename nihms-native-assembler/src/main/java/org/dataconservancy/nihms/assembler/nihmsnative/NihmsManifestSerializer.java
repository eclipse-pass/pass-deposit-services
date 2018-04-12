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
import org.dataconservancy.nihms.model.DepositManifest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

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

    NihmsManifestSerializer(DepositManifest manifest){
        this.manifest = manifest;
    }

    public InputStream serialize(){
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(os);

        for (DepositFile file : manifest.getFiles() ){
            writer.write(file.getType().toString());
            writer.append("\t");
            if(file.getLabel() != null) {
                writer.write(file.getLabel());
            }
            writer.append("\t");
            writer.write(file.getName());
            writer.append("\n");
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

}
