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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositManifest;
import org.dataconservancy.nihms.model.DepositMetadata;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class DepositTestUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DepositTestUtil.class);

    /**
     * List files that are present under {@code classpath}.
     * <p>
     * Resolve the {@code classpath} to a directory on the filesystem, and
     * recursively list all files under the directory.
     * </p>
     *
     * @param classpath a classpath resource which must resolve to a directory on the filesystem
     * @return a {@code List} of Spring {@code Resource}s present under the {@code classpath}
     */
    static List<Resource> fromClasspath(String classpath) {
        ClassPathResource base = new ClassPathResource(classpath);
        assertTrue("Classpath resource cannot be found on the filesystem: '" + classpath + "'", base.exists());

        File baseDir = null;
        try {
            baseDir = new File(base.getURL().getPath());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        assertTrue("Classpath resource '" + classpath + "' cannot be resolved as a filesystem resource '" + baseDir + "'", baseDir.exists());
        assertTrue("Filesystem resource '" + baseDir + "' is expected to be a directory", baseDir.isDirectory());

        Collection<File> files = FileUtils.listFiles(baseDir, null, true);

        return files.stream()
                .map(FileSystemResource::new)
                .collect(Collectors.toList());
    }

    /**
     * Convert the {@link Class#getPackage() package} of the supplied class to a resource path.
     * @param c the class
     * @return the package of the class as a classpath resource
     */
    static String packageToClasspath(Class c) {
        return c.getPackage().getName().replace(".", "/");
    }

    /**
     * Mocks a {@link DepositSubmission}.  The mock submission includes:
     * <ul>
     *     <li>article metadata, including doi and embargo (this embargo is the one that "counts")</li>
     *     <li>manuscript metadata, including title and embargo md (TODO remove embargo MD)</li>
     *     <li>journal metadata, not really used</li>
     *     <li>two Persons, ostensibly the authors of the article</li>
     * </ul>
     * @return a mocked {@code DepositSubmission}
     */
    static DepositSubmission composeSubmission() {
        DepositSubmission submission = mock(DepositSubmission.class);
        DepositMetadata mdHolder = mock(DepositMetadata.class);
        DepositMetadata.Person contributorOne = mock(DepositMetadata.Person.class);
        DepositMetadata.Person contributorTwo = mock(DepositMetadata.Person.class);
        DepositMetadata.Manuscript manuscript = mock(DepositMetadata.Manuscript.class);
        DepositMetadata.Journal journal = mock(DepositMetadata.Journal.class);
        DepositMetadata.Article article = mock(DepositMetadata.Article.class);

        when(submission.getMetadata()).thenReturn(mdHolder);

        when(mdHolder.getPersons()).thenReturn(Arrays.asList(contributorOne, contributorTwo));
        when(mdHolder.getManuscriptMetadata()).thenReturn(manuscript);
        when(mdHolder.getArticleMetadata()).thenReturn(article);
        when(mdHolder.getJournalMetadata()).thenReturn(journal);

        when(contributorOne.getFirstName()).thenReturn("Albert");
        when(contributorOne.getLastName()).thenReturn("Einstein");
        when(contributorTwo.getFirstName()).thenReturn("Stephen");
        when(contributorTwo.getLastName()).thenReturn("Hawking");

        when(manuscript.getTitle()).thenReturn("Two stupendous minds.");
        try {
            when(manuscript.getManuscriptUrl()).thenReturn(
                    URI.create("https://pass.library.johnshopkins.edu/fcrepo/rest/manuscripts/1234").toURL());
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
        when(manuscript.isPublisherPdf()).thenReturn(false);
        when(manuscript.getMsAbstract()).thenReturn("This is an abstract for the manuscript, provided by the" +
                " submitter.");

        when(article.getTitle()).thenReturn("Two stupendous minds.");
        when(article.getDoi()).thenReturn(URI.create("https://dx.doi.org/123/456"));
        when(article.getPubmedId()).thenReturn("pmid:1234");
        when(article.getPubmedCentralId()).thenReturn("pmcid:5678");
        when(article.getEmbargoLiftDate()).thenReturn(ZonedDateTime.now().plusDays(10));

        when(journal.getIssn()).thenReturn("1236-5678");
        when(journal.getJournalType()).thenReturn(DepositMetadata.JOURNAL_PUBLICATION_TYPE.ppub.name());
        when(journal.getJournalTitle()).thenReturn("American Journal of XYZ Research");
        when(journal.getJournalId()).thenReturn("Am J of XYZ Res");
        return submission;
    }

    /**
     * Mocks a {@link DepositSubmission}.  The mock submission includes:
     * <ul>
     *     <li>article metadata, including doi and embargo (this embargo is the one that "counts")</li>
     *     <li>manuscript metadata, including title and embargo md (TODO remove embargo MD)</li>
     *     <li>journal metadata, not really used</li>
     *     <li>two Persons, ostensibly the authors of the article</li>
     *     <li>the custodial content provided</li>
     * </ul>
     * @return a mocked {@code DepositSubmission}
     */
    static DepositSubmission composeSubmission(String submissionName, Map<File, DepositFileType> custodialContent) {
        DepositSubmission submission = composeSubmission();

        // need a DepositFile for each file
        List<DepositFile> depositFiles = custodialContent.entrySet().stream().map(entry -> {
            File file = entry.getKey();
            DepositFileType type = entry.getValue();
            DepositFile dFile = new DepositFile();
            dFile.setName(file.getName());
            try {
                dFile.setLocation(file.toURI().toURL().toString());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            dFile.setType(type);
            dFile.setLabel(type.name());
            return dFile;
        }).collect(Collectors.toList());
        when(submission.getFiles()).thenReturn(depositFiles);

        // need a DepositManifest
        DepositManifest manifest = new DepositManifest();
        manifest.setFiles(depositFiles);
        when(submission.getManifest()).thenReturn(manifest);

        // need a Submission name
        when(submission.getName()).thenReturn(submissionName);

        return submission;
    }

    static File tmpFile(Class testClass, TestName testName, String suffix) throws IOException {
        String nameFmt = "%s-%s-";
        return File.createTempFile(String.format(nameFmt, testClass.getSimpleName(), testName.getMethodName()),
                suffix);
    }

    static File tmpDir() throws IOException {
        File tmpFile = File.createTempFile(DepositTestUtil.class.getSimpleName(), ".tmp");
        assertTrue(tmpFile.delete());
        assertTrue(tmpFile.mkdirs());
        assertTrue(tmpFile.isDirectory());
        return tmpFile;
    }

    /**
     * Extracts the archive (ZIP, GZip, whatever) to a temporary directory, and returns the directory.
     *
     * @param packageFile the package file to open
     * @return the directory that the package file was extracted to
     * @throws IOException if an error occurs opening the file or extracting its contents
     */
    static File openArchive(File packageFile) throws IOException {
        File tmpDir = tmpDir();

        if (!packageFile.getName().endsWith(".zip")) {
            throw new RuntimeException("Do not know how to open archive file '" + packageFile + "'");
        }

        LOG.debug(">>>> Extracting {} to {} ...", packageFile, tmpDir);

        try (InputStream packageFileIn = Files.newInputStream(packageFile.toPath());
             ZipArchiveInputStream zipIn = new ZipArchiveInputStream(packageFileIn)) {

            ArchiveEntry entry;
            while ((entry = zipIn.getNextEntry()) != null ) {
                String entryName = entry.getName();
                boolean isDir = entry.isDirectory();


                Path entryAsPath = tmpDir.toPath().resolve(entryName);
                if (isDir) {
                    Files.createDirectories(entryAsPath);
                } else {
                    Path parentDir = entryAsPath.getParent();
                    if (!parentDir.toFile().exists()) {
                        Files.createDirectories(parentDir);
                    }
                    Files.copy(zipIn, entryAsPath);
                }

            }
        }

        return tmpDir;
    }

    /**
     * Invokes {@link DspaceMetadataDomWriter#write(OutputStream)}, and returns a {@link Document} containing the the
     * parsed output.  This allows the internals of the {@code DspaceMetadataDomWriter} to change (to using SAX, for
     * example), without this test depending on the internal XML parsing model used by the writer.
     *
     * @param dbf
     * @param underTest
     * @return
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    static Document writeAndParseResults(DocumentBuilderFactory dbf, DspaceMetadataDomWriter underTest)
            throws SAXException, IOException, ParserConfigurationException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        underTest.write(out);
        System.err.println(">>> Wrote: \n" + out.toString("UTF-8"));
        Document result = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));
        assertTrue(result.getChildNodes().getLength() > 0);
        return result;
    }

    /**
     * Returns a {@link NodeList} as a {@link List}
     * @param nl
     * @return
     */
    static List<Element> asList(NodeList nl) {
        ArrayList<Element> al = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) {
            al.add((Element)nl.item(i));
        }

        return al;
    }

    /**
     * Returns the {@code mets.xml} from an opened package as a parsed {@link Document}
     *
     * @param extractedPackageDir the base directory of the extracted package
     * @return the METS file parsed into a DOM
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    static Document getMetsXml(File extractedPackageDir) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(Files.newInputStream(extractedPackageDir.toPath().resolve("mets.xml")));
    }
}
