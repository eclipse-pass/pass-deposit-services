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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositFileType;
import org.dataconservancy.pass.deposit.model.DepositMetadata;
import org.dataconservancy.pass.deposit.model.DepositMetadata.Person;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.BaseAssemblerIT;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsAssembler.APPLICATION_GZIP;
import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsAssembler.SPEC_NIHMS_NATIVE_2017_07;
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

    @Override
    protected void verifyStreamMetadata(PackageStream.Metadata metadata) {
        assertEquals(PackageStream.COMPRESSION.GZIP, metadata.compression());
        assertEquals(PackageStream.ARCHIVE.TAR, metadata.archive());
        assertTrue(metadata.archived());
        assertEquals(SPEC_NIHMS_NATIVE_2017_07, metadata.spec());
        assertEquals(APPLICATION_GZIP, metadata.mimeType());
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
        assertTrue("Expected Files to be attached to the DepositSubmission!",
                submission.getFiles().size() > 0);
        assertTrue("Expected exactly 1 manuscript to be attached to the DepositSubmission!",
                submission.getFiles().stream()
                        .filter(df -> df.getType() == DepositFileType.manuscript).count() == 1);

        Map<String, DepositFileType> custodialResourcesTypeMap = custodialResources.stream()
                .collect(Collectors.toMap(DepositFile::getName, DepositFile::getType));

        // Each custodial resource is present in the package.  The tested filenames need to be remediated, in case
        // a custodial resource uses a reserved file name.
        custodialResources.forEach(custodialResource -> {
            String filename = NihmsZippedPackageStream.getNonCollidingFilename(custodialResource.getName(),
                    custodialResource.getType());
            assertTrue(extractedPackageDir.toPath().resolve(filename).toFile().exists());
        });

        Map<String, File> packageFiles = Arrays.stream(extractedPackageDir.listFiles())
                .collect(Collectors.toMap((File::getName), Function.identity()));

        // Each file in the package is accounted for as a custodial resource or as a metadata file
        // Remediated resources are detected by their file prefix
        packageFiles.keySet().stream()
                .filter(fileName -> !fileName.equals(manifest.getName()) && !fileName.equals(metadata.getName()))
                .forEach(fileName -> {
                    String remediatedFilename = NihmsZippedPackageStream.getNonCollidingFilename(fileName,
                            custodialResourcesTypeMap.get(fileName));

                    if (!remediatedFilename.startsWith(NihmsZippedPackageStream.REMEDIATED_FILE_PREFIX)) {
                        assertTrue("Missing file from custodial resources: '" + remediatedFilename + "'",
                                custodialResourcesMap.containsKey(remediatedFilename));
                    } else {
                        assertTrue("Missing remediated file from custodial resources: '" + remediatedFilename + "'",
                                custodialResourcesMap.containsKey(
                                        remediatedFilename.substring(NihmsZippedPackageStream.REMEDIATED_FILE_PREFIX.length())));
                    }
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
        List<String> entries = new ArrayList<>();
        while (lines.hasNext()) {
            String line = lines.nextLine();
            entries.add(line);
            new ManifestLine(manifest, line, lineCount++).assertAll();
        }
        assertEquals("Expected one line per custodial resource plus metadata file in NIHMS manifest file " + manifest,
                submission.getFiles().size() + 1, lineCount);

        //check for compliance with the NIHMS Bulk Submission Specification
        //table, figure and supplement file types must have a label
        //labels must be unique within type for all types
        Map<String, Set<String>> labels = new HashMap<>();
        for (DepositFileType fileType : Arrays.asList(DepositFileType.values())) {
            labels.put(fileType.toString(), new HashSet<>());
        }

        for (String entry : entries) {
            String[] fields = entry.split("\t");
            assertFalse (labels.get(fields[0]).contains(fields[1]));
            if (fields[0].equals("figure") || fields[0].equals("table") || fields[0].equals("supplement")) {
                assertTrue(fields[1].length()>0);
            }
        }
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

        // Insure that only one <person> element is present in the submission metadata
        // and insure that the <person> is a PI or a Co-PI for the grant that funded the submission.

        List<Element> personElements = asList(root.getElementsByTagName("person"));
        // Assert that there is only one Person present in the metadata
        assertEquals(1, personElements.size());

        // Map persons from the metadata to Person objects
        List<Person> asPersons = personElements.stream().map(element -> {
            Person asPerson = new Person();
            asPerson.setFirstName(element.getAttribute("fname"));
            asPerson.setLastName(element.getAttribute("lname"));
            asPerson.setMiddleName(element.getAttribute("mname"));
            asPerson.setType(DepositMetadata.PERSON_TYPE.submitter);
            return asPerson;
        }).collect(Collectors.toList());

        // Insure that the Person in the metadata matches a Person on the Submission, and that the person is a corresponding pi
        asPersons.stream().forEach(person -> {
            assertTrue(submission.getMetadata().getPersons().stream().anyMatch(candidate ->
                    // NIHMS metadata only use first/last/middle names, so never compare against the "full" version
                    candidate.getConstructedName().equals(person.getName()) &&
                    candidate.getType() == person.getType()));
        });

        // Assert that the DOI is present in the metadata
        Element ms = asList(root.getElementsByTagName("manuscript")).get(0);
        assertEquals(submission.getMetadata().getArticleMetadata().getDoi().toString(), ms.getAttribute("doi"));

        // Assert that the ISSNs are present in the metadata as the <issn> element
        List<Element> issns = asList(root.getElementsByTagName("issn"));
        Map<String, DepositMetadata.IssnPubType> issnPubTypes =
                submission.getMetadata().getJournalMetadata().getIssnPubTypes();
        assertEquals(issnPubTypes.size(), issns.size());
        assertTrue(issnPubTypes.size() > 0);

        issnPubTypes.values().forEach(issnPubType -> {
            assertTrue(issns.stream().anyMatch(element -> element.getTextContent().equals(issnPubType.issn) && element.getAttribute("pub-type").equals(issnPubType.pubType.name().toLowerCase())));
        });
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


