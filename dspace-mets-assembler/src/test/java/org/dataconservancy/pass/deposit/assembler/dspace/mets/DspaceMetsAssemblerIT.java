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
import org.apache.commons.io.input.DigestObserver;
import org.apache.commons.io.input.ObservableInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.junit.Test;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.DepositTestUtil.asList;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_CHECKSUM;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_CHECKSUM_TYPE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_FILE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_MIMETYPE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_NS;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.METS_SIZE;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XLINK_HREF;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.XMLConstants.XLINK_NS;
import static org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler.sanitizeFilename;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Creates a package, then extracts it.  Performs some basic tests on the extracted package.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DspaceMetsAssemblerIT extends BaseDspaceMetsAssemblerIT {

    /**
     * Insures the locations of the files in the package are as expected, and that the mets.xml is present and correctly
     * links the files.
     *
     * @throws Exception
     */
    @Test
    public void testPackageStructure() throws Exception {
        verifyPackageStructure(metsDoc, extractedPackageDir, custodialResources);
    }

    /**
     * Insures that the checksums for the METS file elements are correct.
     *
     * @throws Exception
     */
    @Test
    public void testPackageIntegrity() throws Exception {
        Map<String, PackageStream.Checksum> expectedChecksums = new HashMap<>();

        Map<String, PackageStream.Checksum> actualChecksums = new HashMap<>();

        asList(metsDoc.getElementsByTagNameNS(METS_NS, METS_FILE))
                .forEach(fileElement -> {
                    // expected checksums for each package resource
                    String algo = fileElement.getAttribute(METS_CHECKSUM_TYPE);
                    String checksumValue = fileElement.getAttribute(METS_CHECKSUM);
                    String fileName = ((Element) fileElement.getFirstChild())
                            .getAttributeNS(XLINK_NS, XLINK_HREF).substring("data/".length());
                    PackageStream.Checksum checksum = mock(PackageStream.Checksum.class);
                    PackageStream.Algo packageStreamAlgo = PackageStream.Algo.valueOf(algo);
                    when(checksum.algorithm()).thenReturn(packageStreamAlgo);
                    when(checksum.asHex()).thenReturn(checksumValue);

                    expectedChecksums.put(fileName, checksum);

                    // calculate checksums for each package resource
                    try (InputStream resourceIn = Files.newInputStream(extractedPackageDir.toPath().resolve("data/" + fileName));
                         ObservableInputStream obsIn = new ObservableInputStream(resourceIn)) {
                        ResourceBuilder builder = rbf.newInstance();
                        DigestObserver digestObserver = new DigestObserver(builder, packageStreamAlgo);
                        obsIn.add(digestObserver);
                        IOUtils.copy(obsIn, new NullOutputStream());
                        actualChecksums.put(fileName, builder.build().checksum());
                    } catch (IOException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                });


        assertEquals(expectedChecksums.size(), actualChecksums.size());
        expectedChecksums.forEach((filename, checksum) -> {
            assertTrue(actualChecksums.containsKey(filename));
            assertEquals(checksum.algorithm(), actualChecksums.get(filename).algorithm());
            assertEquals(checksum.asHex(), actualChecksums.get(filename).asHex());
        });
    }

    /**
     * Insures that there is a mimetype present and correct size attribute present on METS file elements
     * @throws Exception
     */
    @Test
    public void testFileMetadata() throws Exception {

        // Custodial resources have their file names sanitized before being written out to a package
        // If we are to locate the sanitized file name on the filesystem, we have to repeat the sanitization function
        // on each filename in order to check for its existence
        Map<String, DepositFile> sanitizedCustodialResourcesMap = custodialResourcesMap.entrySet().stream()
                .collect(Collectors.toMap(entry -> sanitizeFilename(entry.getKey()), Map.Entry::getValue));

        asList(metsDoc.getElementsByTagNameNS(METS_NS, METS_FILE))
                .forEach(fileElement -> {
                    assertNotNull(fileElement.getAttribute(METS_MIMETYPE));
                    String sanitizedFileName = ((Element) fileElement.getFirstChild())
                            .getAttributeNS(XLINK_NS, XLINK_HREF).substring("data/".length());
                    File file = new File(extractedPackageDir,"data/" + sanitizedFileName);
                    assertTrue("File metadata references non-existent file: " + sanitizedFileName,
                            file.exists());
                    assertEquals((Long) file.length(), Long.valueOf(fileElement.getAttribute(METS_SIZE)));
                });
    }

}
