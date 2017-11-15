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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsPackageStreamTest {

    private static final Logger LOG = LoggerFactory.getLogger(NihmsPackageStreamTest.class);

    @Test
    public void rereadIOutilsStringInputStream() throws Exception {
        final String expected = "This is the manifest.";
        InputStream in = IOUtils.toInputStream(expected);

        assertEquals(expected, IOUtils.toString(in));
        assertEquals(expected, IOUtils.toString(in));


    }

    @Test
    public void assembleSimplePackage() throws Exception {
        NihmsPackageStream stream = new NihmsPackageStream(
                () -> IOUtils.toInputStream("This is the manifest."),
                () -> IOUtils.toInputStream("This is the metadata"),
                Arrays.asList(
                        new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/manuscript.txt"),
                        new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/figure.jpg")),
                new SimpleMetadataImpl("testpackage.tar.gz"));

        final InputStream packageStream = stream.open();
        assertNotNull(packageStream);
        packageStream.close();
    }

    @Test
    public void writeSimplePackage() throws Exception {
        final String expectedFilename = "testpackage.tar.gz";
        NihmsPackageStream stream = new NihmsPackageStream(
                () -> IOUtils.toInputStream("This is the manifest."),
                () -> IOUtils.toInputStream("This is the metadata"),
                Arrays.asList(
                        new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/manuscript.txt"),
                        new ClassPathResource(this.getClass().getPackage().getName().replace(".", "/") + "/figure.jpg")),
                new SimpleMetadataImpl(expectedFilename));


        File tmpFile = new File(System.getProperty("java.io.tmpdir"), stream.metadata().name());
        LOG.debug("Writing package file {}", tmpFile);

        try (FileOutputStream output = new FileOutputStream(tmpFile)) {
            IOUtils.copy(stream.open(), output);
        }

        assertEquals(expectedFilename, tmpFile.getName()); // duh
        assertTrue(tmpFile.length() > 0);
    }
}