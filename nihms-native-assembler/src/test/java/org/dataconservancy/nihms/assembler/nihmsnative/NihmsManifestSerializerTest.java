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


import org.apache.commons.io.IOUtils;
import org.dataconservancy.nihms.model.NihmsFile;
import org.dataconservancy.nihms.model.NihmsFileType;
import org.dataconservancy.nihms.model.NihmsManifest;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class NihmsManifestSerializerTest {

    @Test
    public void testManifestSerialization(){
        NihmsManifest manifest = new NihmsManifest();

        NihmsFile file1 = new NihmsFile();
        file1.setLabel("File One Label");
        file1.setName("File One name");
        file1.setType(NihmsFileType.figure);

        NihmsFile file2 = new NihmsFile();
        file2.setLabel("File Two Label");
        file2.setName("File Two name");
        file2.setType(NihmsFileType.bulksub_meta_xml);

        NihmsFile file3 = new NihmsFile();
        file3.setLabel("File Three Label");
        file3.setName("File Three name");
        file3.setType(NihmsFileType.table);

        List<NihmsFile> files = new ArrayList<>();
        files.add(file1);
        files.add(file2);
        files.add(file3);

        manifest.setFiles(files);

        NihmsManifestSerializer underTest = new NihmsManifestSerializer(manifest);

        InputStream is = underTest.serialize();

        //tab delimited lines
        String expected = "figure	File One Label	File One name" + "\n" +
                "bulksub_meta_xml	File Two Label	File Two name" + "\n" +
                "table	File Three Label	File Three name" + "\n";

        String actual = "";

        try {
          actual = IOUtils.toString(is, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertEquals(expected, actual);

    }
}
