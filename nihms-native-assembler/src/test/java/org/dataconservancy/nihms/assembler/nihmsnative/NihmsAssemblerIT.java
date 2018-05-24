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
package org.dataconservancy.nihms.assembler.nihmsnative;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositMetadata.Person;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.BaseAssemblerIT;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.DepositTestUtil.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Creates a package, then extracts it.  Performs some basic tests on the extracted package.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsAssemblerIT extends BaseAssemblerIT {

    private File manifest;

    private File metadata;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        manifest = new File(extractedPackageDir, "manifest.txt");
        metadata = new File(extractedPackageDir, "bulk_meta.xml");
    }

    @Test
    public void testSimple() throws Exception {
        assertTrue(extractedPackageDir.exists());
    }

    @Override
    protected AbstractAssembler assemblerUnderTest() {
        return new NihmsAssembler(mbf, rbf);
    }

    /**
     * Insures that each custodial resource is included in the package, and that the required metadata files are there
     *
     * @throws Exception
     */
    @Test
    public void testBasicStructure() throws Exception {
        assertTrue("Missing NIHMS package manifest (expected: " + manifest + ")", manifest.exists());
        assertTrue("Missing NIHMS bulk metadata (expected: " + metadata + ")", metadata.exists());

        // Each custodial resource is present in the package
        custodialResources.forEach(custodialResource -> {
            assertTrue(extractedPackageDir.toPath().resolve(custodialResource.getFilename()).toFile().exists());
        });

        // Each file in the package is accounted for as a custodial resource or as a metadata file
        Map<String, File> packageFiles = Arrays.stream(extractedPackageDir.listFiles())
                .collect(Collectors.toMap((File::getName), Function.identity()));

        custodialResourcesMap.keySet().stream()
                .forEach(fileName -> {
                    assertTrue(packageFiles.containsKey(fileName));
                });

        assertTrue(packageFiles.keySet().contains(manifest.getName()));
        assertTrue(packageFiles.keySet().contains(metadata.getName()));
    }

    /**
     * Insures the manifest structure is sound, and that each file in the manifest references a custodial resource.
     * Insures there is one line in the manifest per custodial resource.
     *
     * @throws Exception
     */
    @Test
    public void testPackageManifest() throws Exception {
        int lineCount = 0;
        LineIterator lines = FileUtils.lineIterator(manifest);
        while (lines.hasNext()) {
            new ManifestLine(manifest, lines.nextLine(), lineCount++).assertAll();
        }
        assertEquals("Expected one line per custodial resource plus metadata file in NIHMS manifest file " + manifest,
                custodialResources.size() + 1, lineCount);
    }

    @Test
    public void testPackageMetadata() throws Exception {
        Document metaDom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(metadata);
        assertNotNull(metaDom);

        // root element is <nihms-submit>
        Element root = metaDom.getDocumentElement();
        assertEquals("nihms-submit", root.getTagName());

        // required <title> element is present with the manuscript title as the value
        Element title = asList(root.getElementsByTagName("title")).get(0);
        assertEquals(submission.getMetadata().getManuscriptMetadata().getTitle(), title.getTextContent());

        // Insure each <person> element corresponds to a Person in the Submission metadata

        List<Element> personElements = asList(root.getElementsByTagName("person"));

        // Collect persons from the metadata
        List<Person> asPersons = personElements.stream().map(element -> {
            Person asPerson = new Person();
            asPerson.setFirstName(element.getAttribute("fname"));
            asPerson.setLastName(element.getAttribute("lname"));
            asPerson.setAuthor("yes".equals(element.getAttribute("author")));
            asPerson.setCorrespondingPi("yes".equals(element.getAttribute("corrpi")));
            asPerson.setPi("yes".equals(element.getAttribute("pi")));
            return asPerson;
        }).collect(Collectors.toList());

        // Assert that each person present in the Submission is present in the metadata
        assertEquals(submission.getMetadata().getPersons().size(), personElements.size());
        submission.getMetadata().getPersons().forEach(p -> {
            assertTrue(asPersons.stream().anyMatch(candidate ->
                    candidate.getFirstName().equals(p.getFirstName()) &&
                    candidate.getLastName().equals(p.getLastName()) &&
                    candidate.isAuthor() == p.isAuthor() &&
                    candidate.isPi() == p.isPi() &&
                    candidate.isCorrespondingPi() == p.isCorrespondingPi()));
        });

        // Assert that the DOI is present in the metadata
        Element ms = (Element) asList(root.getElementsByTagName("manuscript")).get(0);
        assertEquals(submission.getMetadata().getArticleMetadata().getDoi().toString(), ms.getAttribute("doi"));
    }

    private static boolean isNullOrEmpty(String s) {
        if (s == null || s.trim().length() == 0) {
            return true;
        }

        return false;
    }

    private static class ManifestLine {
        private static final String ERR = "File %s, line %s is missing %s";
        private File manifestFile;
        private String line;
        private int lineNo;

        private ManifestLine(File manifestFile, String line, int lineNo) {
            this.manifestFile = manifestFile;
            this.line = line;
            this.lineNo = lineNo;
        }

        void assertAll() {
            assertTypeIsPresent();
            assertLabelIsPresent();
            assertFileIsPresent();
            assertNameIsValid();
        }

        void assertTypeIsPresent() {
            String[] parts = line.split("\t");

            try {
                assertFalse(String.format(ERR, manifestFile, lineNo, "a file type"),
                        isNullOrEmpty(parts[0]));
            } catch (ArrayIndexOutOfBoundsException e) {
                fail(String.format(ERR, manifestFile, lineNo, "a file type"));
            }
        }

        void assertLabelIsPresent() {
            String[] parts = line.split("\t");

            try {
                assertFalse(String.format(ERR, manifestFile, lineNo, "a file label"),
                        isNullOrEmpty(parts[1]));
            } catch (ArrayIndexOutOfBoundsException e) {
                fail(String.format(ERR, manifestFile, lineNo, "a file label"));
            }
        }

        void assertFileIsPresent() {
            String[] parts = line.split("\t");

            try {
                assertFalse(String.format(ERR, manifestFile, lineNo, "a file name"),
                        isNullOrEmpty(parts[2]));
            } catch (ArrayIndexOutOfBoundsException e) {
                fail(String.format(ERR, manifestFile, lineNo, "a file name"));
            }
        }

        void assertNameIsValid() {
            assertFalse(String.format("File %s, line %s: Name cannot be same as metadata file.", manifestFile, lineNo),
                    manifestFile.getName() == NihmsZippedPackageStream.METADATA_ENTRY_NAME);
            assertFalse(String.format("File %s, line %s: Name cannot be same as manifest file.", manifestFile, lineNo),
                    manifestFile.getName() == NihmsZippedPackageStream.MANIFEST_ENTRY_NAME);
        }
    }

}


