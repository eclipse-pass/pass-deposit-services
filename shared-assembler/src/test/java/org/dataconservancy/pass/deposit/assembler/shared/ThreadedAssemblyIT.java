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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.dataconservancy.pass.deposit.DepositTestUtil;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.fs.SharedSubmissionUtil;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.lang.Math.floorDiv;
import static org.dataconservancy.pass.deposit.DepositTestUtil.openArchive;
import static org.dataconservancy.pass.deposit.DepositTestUtil.packageFile;
import static org.dataconservancy.pass.deposit.DepositTestUtil.savePackage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class ThreadedAssemblyIT {

    @Rule
    public TestName testName = new TestName();

    private static boolean assemblerInitialized = false;

    private static ExecutorService itExecutorService;

    private static int noThreads = 10;

    private static AtomicInteger itThread = new AtomicInteger(0);

    private static AtomicInteger packageWriterThread = new AtomicInteger(0);

    protected static Logger LOG = LoggerFactory.getLogger(ThreadedAssemblyIT.class);

    protected static ExceptionHandlingThreadPoolExecutor packageWritingExecutorService;

    private SharedSubmissionUtil submissionUtil = new SharedSubmissionUtil();

    private Assembler underTest;

    protected MetadataBuilderFactory mbf;

    protected ResourceBuilderFactory rbf;

    @BeforeClass
    public static void setUpExecutorService() throws Exception {
        ThreadFactory itTf = r -> new Thread(r, "ThreadedAssemblyITPool-" + itThread.getAndIncrement());
        ThreadFactory pwTf = r -> new Thread(r, "PackageWriterPool-" + packageWriterThread.getAndIncrement());


        itExecutorService = new ThreadPoolExecutor(floorDiv(noThreads, 2), noThreads, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(floorDiv(noThreads, 2)), itTf);
        packageWritingExecutorService = new ExceptionHandlingThreadPoolExecutor(floorDiv(noThreads, 2), noThreads, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(floorDiv(noThreads, 2)), pwTf);
        packageWritingExecutorService.setExceptionHandler((r, t) -> {
            throw new RuntimeException(t);
        });
    }

    @AfterClass
    public static void stopExecutorService() throws Exception {
        itExecutorService.shutdown();
        itExecutorService.awaitTermination(30, TimeUnit.SECONDS);
        packageWritingExecutorService.shutdown();
        packageWritingExecutorService.awaitTermination(30, TimeUnit.SECONDS);
    }

    @Before
    public void setUpAssembler() throws Exception {
        // Use a single instance of the Assembler for all tests
        if (!assemblerInitialized) {
            this.mbf = new DefaultMetadataBuilderFactory();
            this.rbf = new DefaultResourceBuilderFactory();
            this.underTest = assemblerUnderTest();
            assemblerInitialized = true;
        }
    }

    @Test
    public void testMultiplePackageStreams() throws Exception {
        DepositSubmission submission = submissionUtil.asDepositSubmission(URI.create("fake:submission3"));
        Map<String, Object> packageOptions = packageOptions();
        List<Future<PackageStream>> results = new ArrayList<>();
        List<File> packages = new ArrayList<>();

        LOG.info("Submitting packages to the assembler:");
        for (int i = 0; i < noThreads; i++) {
            LOG.info(".");
            results.add(itExecutorService.submit(() -> underTest.assemble(submission, packageOptions)));
        }

        LOG.info("Waiting for results from the assembler, and saving each package:");
        // Get each result insuring no exceptions were thrown.
        for (int i = 0; i < results.size(); i++) {
            LOG.info(".");
            Future<PackageStream> future = results.get(i);
            PackageStream stream = future.get();
            assertNotNull(stream.metadata());
            assertNotNull(stream.metadata().archive());
            assertNotNull(stream.metadata().compression());
            assertEquals(stream.metadata().archive(), packageOptions.get(Archive.KEY));
            assertEquals(stream.metadata().compression(), packageOptions.get(Compression.KEY));

            // TODO: verify resources metadata, but PackageStream#resources is unsupported

            // Read the stream and save it
            File packageFile = savePackage(packageFile(this.getClass(), testName, stream.metadata()), stream);
            packages.add(packageFile);
        }

        LOG.info("Opening and verifying each package:");
        packages.forEach(p -> {
            File dir;
            try {
                dir = openArchive(p, (Archive.OPTS)packageOptions.get(Archive.KEY),
                        (Compression.OPTS)packageOptions.get(Compression.KEY));
                // Have subclass verify the content in the extracted package directory
                verifyExtractedPackage(submission, dir);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            LOG.info("Extracted package to {}", dir);
        });

    }

    /**
     * To be implemented by sub-classes: must return a fully functional instance of the {@link AbstractAssembler} to be
     * tested.
     *
     * @return the {@code AbstractAssembler} under test
     */
    protected abstract AbstractAssembler assemblerUnderTest();

    protected abstract Map<String, Object> packageOptions();

    protected abstract void verifyExtractedPackage(DepositSubmission submission, File packageDir) throws Exception;

}
