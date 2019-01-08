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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Spec;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.MetadataBuilderImpl;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderImpl;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.dataconservancy.pass.deposit.assembler.dspace.mets.DspaceMetsAssembler.APPLICATION_ZIP;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.DspaceMetsAssembler.SPEC_DSPACE_METS;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DspaceMetsPackageStreamTest {

    private MetadataBuilder mb = new MetadataBuilderImpl();

    private ResourceBuilderFactory rbf = mock(ResourceBuilderFactory.class);

    private DspaceMetadataDomWriterFactory metsWriterFactory = mock(DspaceMetadataDomWriterFactory.class);

    private DspaceMetadataDomWriter metsWriter = mock(DspaceMetadataDomWriter.class);

    private List<DepositFileResource> custodialContent;

    private Map<String, Object> packageOptions;

    @Before
    public void setUp() throws Exception {
        when(rbf.newInstance()).thenReturn(new ResourceBuilderImpl());
        when(metsWriterFactory.newInstance()).thenReturn(metsWriter);

        mb.spec(SPEC_DSPACE_METS);
        mb.archive(Archive.OPTS.ZIP);
        mb.archived(true);
        mb.compressed(true);
        mb.compression(Compression.OPTS.ZIP);
        mb.mimeType(APPLICATION_ZIP);

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

        packageOptions = new HashMap<String, Object>() {
            {
                // TODO: what about checksums, what happens when no checksums are specified?
                put(Archive.KEY, Archive.OPTS.ZIP);
                put(Spec.KEY, SPEC_DSPACE_METS);
            }
        };
    }

    @Test
    public void testStream() throws Exception {
        // Construct a package stream using mocks and two example files
        DspaceMetsZippedPackageStream underTest =
                new DspaceMetsZippedPackageStream(
                        mock(DepositSubmission.class), custodialContent, mb, rbf, metsWriterFactory, packageOptions);

        // Open and write the package stream to /dev/null, asserting that some bytes were written
        assertTrue("Expected bytes written to be greater than 0!",
                IOUtils.copy(underTest.open(), new NullOutputStream()) > 0);

        // One PackageStream.Resource should be created for each file of custodial content
        verify(rbf, times(custodialContent.size())).newInstance();

        // Each PackageStream.Resource should be added to the METS writer
        verify(metsWriter, times(custodialContent.size())).addResource(any());

        // And the METS.xml should be written out to the package
        verify(metsWriter).write(any());
    }

}