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
import org.apache.commons.io.output.NullOutputStream;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Spec;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.ResourceBuilderFactory;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.dataconservancy.deposit.util.function.FunctionUtil.performSilently;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsPackageStreamTest {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPackageStreamTest.class);

    private static List<DepositFileResource> custodialContent;

    private static Map<String, Object> packageOptions;

    private StreamingSerializer manifestSerializer = () -> performSilently(() -> IOUtils.toInputStream("This is the manifest.", "UTF-8"));

    private StreamingSerializer metadataSerializer = () -> performSilently(() -> IOUtils.toInputStream("This is the metadata", "UTF-8"));

    private MetadataBuilder mb;
    private PackageStream.Metadata metadata;
    private ResourceBuilderFactory rbf;
    private ResourceBuilder rb;

    @BeforeClass
    public static void setUpCustodialContentAndPackageOptions() throws Exception {
        String manuscriptLocation = NihmsPackageStreamTest.class.getPackage().getName().replace(".", "/") + "/manuscript.txt";
        String figureLocation = NihmsPackageStreamTest.class.getPackage().getName().replace(".", "/") + "/figure.jpg";

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
                put(Spec.KEY, NihmsAssembler.SPEC_NIHMS_NATIVE_2017_07);
                put(Archive.KEY, Archive.OPTS.TAR);
                put(Compression.KEY, Compression.OPTS.GZIP);
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        mb = mock(MetadataBuilder.class);
        metadata = mock(PackageStream.Metadata.class);
        rbf = mock(ResourceBuilderFactory.class);
        rb = mock(ResourceBuilder.class);
        when(rbf.newInstance()).thenReturn(rb);
        when(mb.build()).thenReturn(metadata);
    }

    /**
     * when commons-io creates an inputstream from a string, it cannot be re-read.
     *
     * @throws IOException
     */
    @Test
    public void rereadIOutilsStringInputStream() throws IOException {
        final String expected = "This is the manifest.";
        InputStream in = IOUtils.toInputStream(expected, "UTF-8");

        assertEquals(expected, IOUtils.toString(in, "UTF-8"));
        assertEquals("", IOUtils.toString(in, "UTF-8"));
    }

    @Test
    public void assembleSimplePackage() throws Exception {
        NihmsZippedPackageStream underTest = new NihmsZippedPackageStream(mock(DepositSubmission.class),
                custodialContent, mb, rbf, packageOptions);
        underTest.setManifestSerializer(manifestSerializer);
        underTest.setMetadataSerializer(metadataSerializer);
        when(mb.name(anyString())).thenReturn(mb);
        PackageStream.Resource pr = mock(PackageStream.Resource.class);
        when(pr.name()).thenReturn(custodialContent.get(0).getFilename());
        when(pr.name()).thenReturn(custodialContent.get(1).getFilename());
        when(rb.build()).thenReturn(pr);

        final InputStream packageStream = underTest.open();
        assertNotNull(packageStream);
        IOUtils.copy(packageStream, new NullOutputStream());
        packageStream.close();
    }

    @Test
    public void writeSimplePackage() throws Exception {
        final String expectedFilename = "testpackage.tar.gz";

        when(mb.name(anyString())).thenReturn(mb);
        PackageStream.Resource pr = mock(PackageStream.Resource.class);
        when(pr.name()).thenReturn(custodialContent.get(0).getFilename());
        when(pr.name()).thenReturn(custodialContent.get(1).getFilename());

        when(rb.build()).thenReturn(pr);

        NihmsZippedPackageStream underTest = new NihmsZippedPackageStream(mock(DepositSubmission.class), custodialContent, mb, rbf, packageOptions);
        underTest.setManifestSerializer(manifestSerializer);
        underTest.setMetadataSerializer(metadataSerializer);

        File tmpFile = new File(System.getProperty("java.io.tmpdir"), expectedFilename);
        LOG.debug("Writing package file {}", tmpFile);

        try (InputStream input = underTest.open();
             FileOutputStream output = new FileOutputStream(tmpFile)) {
            IOUtils.copy(input, output);
        }

        assertTrue(tmpFile.length() > 0);
    }

    @Test
    public void nonCollidingFilename() throws Exception {
        String nameIn, nameOut;

        nameIn = "test.txt";
        nameOut = NihmsZippedPackageStream.getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertTrue("Non-colliding name was changed.", nameIn.contentEquals(nameOut));

        nameIn = "manifest.txt";
        nameOut = NihmsZippedPackageStream.getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertFalse("Colliding manifest name was not changed.", nameIn.contentEquals(nameOut));

        nameIn = "bulk_meta.xml";
        nameOut = NihmsZippedPackageStream.getNonCollidingFilename(nameIn, DepositFileType.supplement);
        assertFalse("Colliding metadata name was not changed.", nameIn.contentEquals(nameOut));

        nameIn = "bulk_meta.xml";
        nameOut = NihmsZippedPackageStream.getNonCollidingFilename(nameIn, DepositFileType.bulksub_meta_xml);
        assertTrue("Actual metadata name was changed.", nameIn.contentEquals(nameOut));
    }
}