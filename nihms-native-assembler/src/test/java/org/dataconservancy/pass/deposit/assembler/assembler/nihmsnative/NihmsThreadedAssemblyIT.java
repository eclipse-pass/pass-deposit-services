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
package org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative;

import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.ThreadedAssemblyIT;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsAssembler.BULK_META_FILENAME;
import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsAssembler.MANIFEST_FILENAME;
import static org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsAssembler.SPEC_NIHMS_NATIVE_2017_07;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsThreadedAssemblyIT extends ThreadedAssemblyIT {

    private File candidateFile;

    @Override
    protected AbstractAssembler assemblerUnderTest() {
        NihmsPackageProvider pp = new NihmsPackageProvider();
        return new NihmsAssembler(mbf, rbf, packageWritingExecutorService, pp);
    }

    @Override
    protected Map<String, Object> packageOptions() {
        return new HashMap<String, Object>() {
            {
                put(PackageOptions.Spec.KEY, SPEC_NIHMS_NATIVE_2017_07);
                put(PackageOptions.Archive.KEY, PackageOptions.Archive.OPTS.TAR);
                put(PackageOptions.Compression.KEY, PackageOptions.Compression.OPTS.GZIP);
                put(PackageOptions.Checksum.KEY, singletonList(PackageOptions.Checksum.OPTS.SHA256));
            }
        };
    }

    @Override
    protected void verifyExtractedPackage(DepositSubmission submission, File packageDir) throws IOException {
        List<File> actualFiles = new ArrayList<>();
        new DirectoryWalker<File>() {
            {
                walk(packageDir, actualFiles);
            }

            @Override
            protected void handleFile(File file, int depth, Collection results) throws IOException {
                actualFiles.add(file);
            }
        };

        // Assert the expected number of *custodial* files in the extracted package equals the number of files actually
        // packaged
        //noinspection ConstantConditions
        assertTrue(actualFiles.size() > 0);
        assertEquals(submission.getFiles().size(), actualFiles.size() - 2);

        // Verify supplemental files (i.e. non-custodial content like metadata) exist and have expected content
        File bulk_meta = new File(packageDir, NihmsAssembler.BULK_META_FILENAME);
        File manifest = new File(packageDir, NihmsAssembler.MANIFEST_FILENAME);

        assertTrue(bulk_meta.exists() && bulk_meta.length() > 0);
        assertTrue(manifest.exists() && manifest.length() > 0);

        String expectedManifest = IOUtils.toString(
                new NihmsManifestSerializer(submission.getManifest()).serialize().getInputStream(), UTF_8);
        String expectedBulkMeta = IOUtils.toString(
                new NihmsMetadataSerializer(submission.getMetadata()).serialize().getInputStream(), UTF_8);

        assertEquals(expectedManifest, IOUtils.toString(new FileInputStream(manifest), UTF_8));
        assertEquals(expectedBulkMeta, IOUtils.toString(new FileInputStream(bulk_meta), UTF_8));

        // Each custodial file in the DepositSubmission is present on the filesystem
        submission.getFiles().forEach(df -> {
            candidateFile = new File(packageDir, df.getName());
            assertTrue(candidateFile.exists());
        });

        // Each custodial file on the filesystem is present in the DepositSubmission
        Map<String, DepositFile> submittedFiles =
                submission.getFiles().stream().collect(toMap(DepositFile::getName, identity()));
        actualFiles.stream()
                .filter(f -> !f.getName().equals(BULK_META_FILENAME) && !f.getName().equals(MANIFEST_FILENAME))
                .forEach(f -> {
                    assertNotNull(f.getName());
                    assertTrue(submittedFiles.containsKey(f.getName()));
                });
    }
}
