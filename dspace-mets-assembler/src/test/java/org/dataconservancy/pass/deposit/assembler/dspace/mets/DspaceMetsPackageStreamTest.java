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
import org.dataconservancy.nihms.assembler.MetadataBuilder;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderImpl;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DspaceMetsPackageStreamTest {

    private MetadataBuilder mb = mock(MetadataBuilder.class);

    private ResourceBuilderFactory rbf = mock(ResourceBuilderFactory.class);

    private DspaceMetadataDomWriter metsWriter = mock(DspaceMetadataDomWriter.class);

    private List<Resource> custodialContent = Arrays.asList(
            new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/manuscript.txt"),
            new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/figure.jpg"));


    @Before
    public void setUp() throws Exception {
        when(rbf.newInstance()).thenReturn(new ResourceBuilderImpl());
    }

    @Test
    public void testStream() throws Exception {
        // Construct a package stream using mocks and two example files
        DspaceMetsZippedPackageStream underTest =
                new DspaceMetsZippedPackageStream(mock(DepositSubmission.class), custodialContent, mb, rbf, metsWriter);

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