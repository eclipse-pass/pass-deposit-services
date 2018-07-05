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

package org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative;


import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositManifest;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jim Martino (jrm@jhu.edu)
 */
public class NihmsManifestSerializerTest {

    @Test
    public void testManifestSerialization(){
        DepositManifest manifest = new DepositManifest();

        DepositFile file1 = new DepositFile();
        file1.setLabel("File One Label");
        file1.setName("File One name");
        file1.setType(DepositFileType.figure);

        DepositFile file2 = new DepositFile();
        file2.setLabel("File Two Label");
        file2.setName("File Two name");
        file2.setType(DepositFileType.bulksub_meta_xml);

        DepositFile file3 = new DepositFile();
        file3.setLabel("File Three Label");
        file3.setName("File Three name");
        file3.setType(DepositFileType.table);

        DepositFile file4 = new DepositFile();
        //label not always required
        file4.setName("File Four name");
        file4.setType(DepositFileType.manuscript);

        List<DepositFile> files = new ArrayList<>();
        files.add(file1);
        files.add(file2);
        files.add(file3);
        files.add(file4);

        manifest.setFiles(files);

        NihmsManifestSerializer underTest = new NihmsManifestSerializer(manifest);

        InputStream is = underTest.serialize();

        String expected = "figure" + "\t" + "File One Label" + "\t" +"File One name" + "\n" +
                "bulksub_meta_xml"+ "\t" + "File Two Label" + "\t" +"File Two name" + "\n" +
                "table" + "\t" + "File Three Label" + "\t" + "File Three name" + "\n" +
                "manuscript" + "\t\t" + "File Four name"+ "\n";

        String actual = "";

        try {
          actual = IOUtils.toString(is, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertEquals(expected, actual);

    }

    @Test
    public void labelMakerTest() {

        NihmsManifestSerializer underTest = new NihmsManifestSerializer(null);
        NihmsManifestSerializer.DepositFileLabelMaker labelMaker = underTest.new DepositFileLabelMaker();

        String label = labelMaker.getTypeUniqueLabel(DepositFileType.figure, null);
        assertEquals("figure-1", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.figure, null);
        assertEquals("figure-2", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.figure, "   ");
        assertEquals("figure-3", label);

        label = labelMaker.getTypeUniqueLabel(DepositFileType.supplement, "figure-1");
        assertEquals("figure-1", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.supplement, null);
        assertEquals("supplement-1", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.supplement, "");
        assertEquals("supplement-2", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.supplement, null);
        assertEquals("supplement-3", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.manuscript, "Moo Cows in the Pasture");
        assertEquals("Moo Cows in the Pasture", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.figure, "Spotted Cows");
        assertEquals("Spotted Cows", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.figure, "Spotted Cows");
        assertEquals("Spotted Cows-1", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.figure, "Spotted Cows     ");
        assertEquals("Spotted Cows-2", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.figure, "Spotted Cows-2");
        assertEquals("Spotted Cows-2-1", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.table, "table");
        assertEquals("table", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.table, "table-1");
        assertEquals("table-1", label );

        label = labelMaker.getTypeUniqueLabel(DepositFileType.table, "");
        assertEquals("table-2", label );


    }
    
}
    
