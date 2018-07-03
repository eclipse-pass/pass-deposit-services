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

package org.dataconservancy.pass.deposit.assembler.dspace.mets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.pass.deposit.assembler.shared.DefaultResourceBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.MetadataBuilderImpl;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.dataconservancy.pass.deposit.DepositTestUtil.composeSubmission;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DspaceMetsPackageStreamIT {

    private static final Logger LOG = LoggerFactory.getLogger(DspaceMetsPackageStreamIT.class);

    private File tempDir;

    private MetadataBuilder mb = new MetadataBuilderImpl();

    private ResourceBuilderFactory rbf = new DefaultResourceBuilderFactory();

    private DspaceMetadataDomWriterFactory metsWriter = new DspaceMetadataDomWriterFactory(
            DocumentBuilderFactory.newInstance());

    private List<DepositFileResource> custodialContent;

    @Before
    public void setUp() throws Exception {
        tempDir = File.createTempFile("DspaceMetsPackageStreamIT", ".dir");
        FileUtils.deleteQuietly(tempDir);
        tempDir = new File(tempDir.getParent(), tempDir.getName());
        assertTrue("Unable to create directory '" + tempDir + "'", tempDir.mkdirs());
//        tempDir.deleteOnExit();

        String manuscriptLocation = this.getClass().getPackage().getName().replace(".", "/") + "/manuscript.txt";
        String figureLocation = this.getClass().getPackage().getName().replace(".", "/") + "/figure.jpg";

        DepositFile manuscript = new DepositFile();
        manuscript.setName("manuscript.txt");
        manuscript.setType(DepositFileType.manuscript);
        manuscript.setLabel("Manuscript");
        manuscript.setLocation(manuscriptLocation);

        DepositFile figure = new DepositFile();
        figure.setName("figure.jpg");
        figure.setType(DepositFileType.figure);
        figure.setLabel("Figure");
        figure.setLocation(figureLocation);

        custodialContent = Arrays.asList(
                new DepositFileResource(manuscript, new ClassPathResource(manuscriptLocation)),
                new DepositFileResource(figure, new ClassPathResource(figureLocation)));
    }

    @Test
    public void testStream() throws Exception {

        DepositSubmission submission = composeSubmission();

        // Assert there are files on the submission and that they have a type
        assertTrue(submission.getFiles().size() > 0);
        assertTrue(submission.getFiles().stream().allMatch(df -> df.getType() != null));
        assertEquals(1, submission.getFiles().stream()
                .filter(df -> df.getType() == DepositFileType.manuscript).count());

        submission.setName(this.getClass().getName() + "_testStream");

        // Construct a package stream using mocks and two example files
        DspaceMetsZippedPackageStream underTest =
                new DspaceMetsZippedPackageStream(submission, custodialContent, mb, rbf, metsWriter);

        File outFile = new File(tempDir, "testStream.tar.gz");
        FileOutputStream out = new FileOutputStream(outFile);
        // Open and write the package stream, asserting that some bytes were written
        assertTrue("Expected bytes written to be greater than 0!",
                IOUtils.copy(underTest.open(), out) > 0);

        LOG.debug("Wrote test package out to {}", outFile);

        // One PackageStream.Resource should be created for each file of custodial content
//        verify(rbf, times(custodialContent.size())).newInstance();

        // Each PackageStream.Resource should be added to the METS writer
//        verify(metsWriter, times(custodialContent.size())).addResource(any());

        // And the METS.xml should be written out to the package
//        verify(metsWriter).write(any());
    }
}
