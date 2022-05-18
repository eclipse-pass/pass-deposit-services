/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.assembler.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.io.DirectoryWalker;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates re-usable logic for verifying an exploded package on the filesystem.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface PackageVerifier {

    Logger LOG = LoggerFactory.getLogger(PackageVerifier.class);

    String DOUBLE_CHECK_MSG = "Double-check the custodial FileFilter supplied to this method, and manually examine " +
                              "the package directory for any discrepancies.";

    String DOUBLE_CHECK_MAPPER_MSG = "Double-check the 'custodialFilter' and 'packageFileMapper' supplied to this " +
                                     "method, and manually examine the package directory for any discrepancies";

    /**
     * This is the primary method implemented by subclasses to verify that their {@link Assembler} created a proper
     * package.  The {@link DepositSubmission} processed by the {@code Assembler} and the base directory of the exploded
     * package produced by the {@code Assembler} are provided to the subclass.
     * <p>
     * Implementations are encouraged to invoke {@link #verifyCustodialFiles(DepositSubmission, File, FileFilter,
     * BiFunction)} in order to verify the presence of their custodial content in the exploded package produced by this
     * test.
     * </p>
     * <p>
     * Implementations are responsible for additional checks on the custodial content (like checksum verification) and
     * for insuring any supplemental content (e.g. BagIT tag files, or any metadata generated like a METS.xml) is
     * present and contains the correct content.
     * </p>
     *
     * @param submission      the submission containing custodial content that is contained in the package
     * @param explodedPackage the exploded version of the package, including a reference to the original archive file
     * @param options         the options used when creating the package
     * @throws Exception if there are any errors verifying the exploded package
     */
    void verify(DepositSubmission submission, ExplodedPackage explodedPackage, Map<String, Object> options)
        throws Exception;

    /**
     * Typically invoked by subclasses from their implementation of
     * {@link #verify(DepositSubmission, ExplodedPackage, Map)}.  This
     * implementation insures that every file present in the submission is present in the extracted package, and that
     * every custodial file in the extracted package is present in the submission.
     * <p>
     * The {@code FileFilter} supplied to this method determines whether or not a {@code File} in the exploded package
     * is custodial content or supplemental content.  Files that are determined to be custodial are verified by this
     * method, supplemental content is ignored.  Subclasses must use {@code verify(...)} to verify their supplemental
     * content.
     * </p>
     *
     * @param submission        the submission containing the custodial content packaged by this test
     * @param packageDir        the base directory of the exploded package created by this test
     * @param custodialFilter   identifies custodial files in the package (distinct from supplemental or
     *                          non-custodial content)
     * @param packageFileMapper maps the custodial file in the package back to the DepositFile in the
     *                          submission
     */
    default void verifyCustodialFiles(DepositSubmission submission, File packageDir, FileFilter custodialFilter,
                                      BiFunction<File, File, DepositFile> packageFileMapper) throws Exception {

        List<File> supplementalFiles = new ArrayList<>();
        List<File> custodialFiles = new ArrayList<>();
        Map<File, DepositFile> custodialMap = new HashMap<>();
        new DirectoryWalker<File>() {
            {
                try {
                    walk(packageDir, null);
                } catch (IOException e) {
                    throw new RuntimeException("Error walking the extracted package directory " + packageDir, e);
                }
            }

            @Override
            protected void handleFile(File file, int depth, Collection ignored) throws IOException {
                if (custodialFilter.accept(file)) {
                    custodialFiles.add(file);
                    DepositFile depositFile = packageFileMapper.andThen(df -> {
                        assertNotNull("Package File Mapper returned a null DepositFile for the file " + file, df);
                        return df;
                    }).apply(packageDir, file);
                    custodialMap.put(file, depositFile);
                } else {
                    supplementalFiles.add(file);
                }
            }
        };

        // Warn if no supplemental files are discovered in the extracted package
        //noinspection ConstantConditions
        if (supplementalFiles.size() < 1) {
            LOG.warn("No supplemental files were detected in the package directory {}", packageDir);
        }

        // Assert the expected number of *custodial* files in the extracted package equals the number of files actually
        // packaged
        assertTrue("No custodial files were detected in the package directory " + packageDir + ".  " +
                   DOUBLE_CHECK_MSG, custodialFiles.size() > 0);
        assertEquals("The number of files in the submission (" + submission.getFiles().size() + ") does not " +
                     "equal the number of custodial files (" + custodialFiles.size() + ") found in the package " +
                     "directory " + packageDir + ".  " + DOUBLE_CHECK_MSG, submission.getFiles().size(),
                     custodialFiles.size());

        // Sanity check the size and contents of the depositFileMap.  Every DepositFile in the DepositSubmission should
        // be present in the map, and every DepositFile in the map should be present in the DepositSubmission.
        assertEquals(submission.getFiles().size(), custodialMap.size());
        custodialMap.values().forEach(df -> assertTrue(submission.getFiles().contains(df)));
        submission.getFiles().forEach(df -> assertTrue(custodialMap.containsValue(df)));

        // Assert each custodial file in the DepositSubmission is present on the filesystem
        submission.getFiles().forEach(depositFile -> assertTrue("The custodial file from the submission (" +
                                                                depositFile.getName() + ") was not found in the " +
                                                                "exploded package under " + packageDir + ".  " +
                                                                DOUBLE_CHECK_MAPPER_MSG,
                                                                lookup(depositFile, custodialMap).exists()));

        // Each custodial file on the filesystem is present in the DepositSubmission
        custodialFiles.forEach(file -> assertTrue("A custodial file found inside the package ( " + file +
                                                  ") is not present in the submission.",
                                                  custodialMap.containsKey(file)));
    }

    /**
     * Retrieves the corresponding File on the filesystem for the supplied DepositFile.
     *
     * @param df  the DepositFile being looked up
     * @param map one-to-one map of DepositFiles to their corresponding location on the filesystem
     * @return the File corresponding to the DepositFile, never {@code null}
     * @throws RuntimeException if the corresponding File is not found
     */
    static File lookup(DepositFile df, Map<File, DepositFile> map) {
        Map.Entry<File, DepositFile> mapping = map
            .entrySet()
            .stream()
            .filter((entry) -> entry.getValue() == df)
            .findAny()
            .orElseThrow(() ->
                             new RuntimeException(
                                 "Missing expected DepositFile " + df + " from the deposit file map."));

        return mapping.getKey();
    }
}
