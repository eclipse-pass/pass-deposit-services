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
package org.dataconservancy.pass.deposit;

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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
public class DepositTestUtil {

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
    public static List<Resource> fromClasspath(String classpath) {
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
    public static String packageToClasspath(Class c) {
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
    public static DepositSubmission composeSubmission() {
        DepositSubmission submission = new DepositSubmission();
        DepositMetadata mdHolder = new DepositMetadata();
        DepositMetadata.Person contributorOne = new DepositMetadata.Person();
        DepositMetadata.Person contributorTwo = new DepositMetadata.Person();
        DepositMetadata.Manuscript manuscript = new DepositMetadata.Manuscript();
        DepositMetadata.Journal journal = new DepositMetadata.Journal();
        DepositMetadata.Article article = new DepositMetadata.Article();

        submission.setMetadata(mdHolder);

        mdHolder.setPersons(Arrays.asList(contributorOne, contributorTwo));
        mdHolder.setManuscriptMetadata(manuscript);
        mdHolder.setArticleMetadata(article);
        mdHolder.setJournalMetadata(journal);

        contributorOne.setFirstName("Albert");
        contributorOne.setLastName("Einstien");
        contributorTwo.setFirstName("Stephen");
        contributorTwo.setLastName("Hawking");

        manuscript.setTitle("Two stupendous minds.");
        try {
            manuscript.setManuscriptUrl(
                    URI.create("https://pass.library.johnshopkins.edu/fcrepo/rest/manuscripts/1234").toURL());
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
        manuscript.setPublisherPdf(false);
        manuscript.setMsAbstract("This is an abstract for the manuscript, provided by the submitter.");

        article.setTitle("Two stupendous minds.");
        article.setDoi(URI.create("https://dx.doi.org/123/456"));
        article.setPubmedId("pmid:1234");
        article.setPubmedCentralId("pmcid:5678");
        article.setEmbargoLiftDate(ZonedDateTime.now().plusDays(10));

        journal.setIssn("1236-5678");
        journal.setPubType(DepositMetadata.JOURNAL_PUBLICATION_TYPE.ppub);
        journal.setJournalTitle("American Journal of XYZ Research");
        journal.setJournalId("Am J of XYZ Res");
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
    public static DepositSubmission composeSubmission(String submissionName, Map<File, DepositFileType> custodialContent) {
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
        submission.setFiles(depositFiles);

        // need a DepositManifest
        DepositManifest manifest = new DepositManifest();
        manifest.setFiles(depositFiles);
        submission.setManifest(manifest);

        // need a Submission name
        submission.setName(submissionName);

        return submission;
    }

    public static File tmpFile(Class testClass, TestName testName, String suffix) throws IOException {
        String nameFmt = "%s-%s-";
        return File.createTempFile(String.format(nameFmt, testClass.getSimpleName(), testName.getMethodName()),
                suffix);
    }

    public static File tmpDir() throws IOException {
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
    public static File openArchive(File packageFile) throws IOException {
        File tmpDir = tmpDir();

        if (!packageFile.getName().endsWith(".zip")) {
            throw new RuntimeException("Do not know how to open archive file '" + packageFile + "'");
        }

        DepositTestUtil.LOG.debug(">>>> Extracting {} to {} ...", packageFile, tmpDir);

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
     * Returns a {@link NodeList} as a {@link List}
     * @param nl
     * @return
     */
    public static List<Element> asList(NodeList nl) {
        ArrayList<Element> al = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) {
            al.add((Element)nl.item(i));
        }

        return al;
    }
}
