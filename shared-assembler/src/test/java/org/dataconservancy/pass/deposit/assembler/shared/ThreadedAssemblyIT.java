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

import static java.lang.Math.floorDiv;
import static org.dataconservancy.pass.deposit.DepositTestUtil.openArchive;
import static org.dataconservancy.pass.deposit.DepositTestUtil.packageFile;
import static org.dataconservancy.pass.deposit.DepositTestUtil.savePackage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resources.SharedSubmissionUtil;

/**
 * Invokes a single instance of an {@link Assembler} by multiple threads, insuring that the {@code Assembler} does not
 * maintain any state across threads that would produce corrupt packages.
 * <p>
 * The packages produced by the {@code Assembler} are opaque to this test, therefore subclasses must implement several
 * methods that verify the contents of the packages produced by the {@code Assembler}.
 * </p>
 * <h3>Methods to implement</h3>
 * <dl>
 *     <dt>{@link #assemblerUnderTest()}</dt>
 *     <dd>Must return a single instance of an {@code Assembler}, fully configured and ready to produce packages</dd>
 *     <dt>{@link #packageOptions()}</dt>
 *     <dd>Must return the a {@code Map} of options used to configure the {@code Assembler}.  Required options are
 *         {@link Compression.OPTS} and {@link Archive.OPTS}.  {@code Compression.OPTS} may be specified as
 *         {@link Compression.OPTS#NONE NONE}, but {@link Archive.OPTS#NONE Archive.OPTS.NONE} is <em>not</em> a valid
 *         option.  This test expects the {@code Assembler} to stream an archive, not individual resources</dd>
 *     <dt>{@link #packageVerifier()}</dt>
 *     <dd>Returns an implementation of {@link PackageVerifier} used to validate the packages produced by this test</dd>
 * </dl>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class ThreadedAssemblyIT {

    /**
     * Provides access to the method name of the currently running test.  Useful for uniquely naming files that are
     * outputs of a test.
     */
    @Rule
    public TestName testName = new TestName();

    /**
     * Insures the {@code Assembler} under test is instantiated once, so it is effectively a singleton throughout the
     * test execution.
     */
    private static boolean assemblerInitialized = false;

    /**
     * Manages the threads used for the test execution.  Each thread will use the same instance of the {@code Assembler}
     * under test, and run {@link Assembler#assemble(DepositSubmission, Map)}.
     */
    private static ExecutorService itExecutorService;

    /**
     * The number of threads to launch.  The {@link #itExecutorService} should account for this and allow this number
     * of threads to execute simultaneously.
     */
    private static final int NO_THREADS = 10;

    /**
     * Used to name the threads created by the {@link #itExecutorService}.
     */
    private static final AtomicInteger IT_THREAD = new AtomicInteger(0);

    private static final String DOUBLE_CHECK_MSG = "Double-check the custodial FileFilter supplied to this method, " +
                                                   "and manually examine the package directory for any discrepancies.";

    private static final String DOUBLE_CHECK_MAPPER_MSG = "Double-check the 'custodialFilter' and " +
                                                          "'packageFileMapper' supplied to this method, and manually " +
                                                          "examine the package directory for any " +
                                                          "discrepancies";

    /**
     * Logger
     */
    protected static Logger LOG = LoggerFactory.getLogger(ThreadedAssemblyIT.class);

    /**
     * Provides access to classpath resources as a {@link DepositSubmission}.
     */
    protected SharedSubmissionUtil submissionUtil = new SharedSubmissionUtil();

    /**
     * Builds sample Submission graphs present on the classpath (in concert with {@link #submissionUtil})
     */
    protected SubmissionBuilder builder = new FilesystemModelBuilder();

    /**
     * The factory used to create instances of {@link org.dataconservancy.pass.deposit.assembler.MetadataBuilder}.
     * {@code MetadataBuilder} is used to add or create metadata describing the {@link PackageStream}.  The factory is
     * typically invoked <em>once</em> when building a {@code PackageStream} to create a single instance of
     * {@code MetadataBuilder}.
     */
    protected MetadataBuilderFactory mbf;

    /**
     * The factory used to create instances of {@link org.dataconservancy.pass.deposit.assembler.ResourceBuilder}.
     * {@code ResourceBuilder} is used to add or create metadata describing individual resources in the {@link
     * PackageStream}.  This factory is typically invoked <em>once per resource</em>.  There will be an instance of
     * {@code ResourceBuilder} instantiated for every resource contained in the {@code PackageStream}.
     */
    protected ResourceBuilderFactory rbf;

    /**
     * If {@code true} (the default) the packages produced by this test will be removed after this test succeeds.  Set
     * this flag to {@code false} to always leave the packages on the filesystem regardless of the test outcome.
     */
    protected boolean performCleanup = true;

    protected List<File> packagesToCleanup = new ArrayList<>();

    /**
     * The {@link Assembler} being tested.  Private so that sub classes are unable to change the object reference.
     */
    private Assembler underTest;

    private PackageVerifier verifier;

    /**
     * Instantiates the {@link #itExecutorService}.
     */
    @BeforeClass
    public static void setUpExecutorService() {
        ThreadFactory itTf = r -> new Thread(r, "ThreadedAssemblyITPool-" + IT_THREAD.getAndIncrement());
        itExecutorService = new ThreadPoolExecutor(floorDiv(NO_THREADS, 2), NO_THREADS, 10,
                                                   TimeUnit.SECONDS, new ArrayBlockingQueue<>(floorDiv(NO_THREADS, 2)),
                                                   itTf);
    }

    /**
     * Shuts down the {@link #itExecutorService}.
     *
     * @throws InterruptedException if the service is interrupted while awaiting termination
     */
    @AfterClass
    public static void stopExecutorService() throws InterruptedException {
        itExecutorService.shutdown();
        itExecutorService.awaitTermination(30, TimeUnit.SECONDS);
    }

    @After
    public void cleanupPackageDirectories() throws Exception {
        if (performCleanup) {
            LOG.info("Cleaning up packages created by this test.");
            packagesToCleanup.forEach(FileUtils::deleteQuietly);
        }
    }

    /**
     * Initializes the {@link #mbf MetadataBuilderFactory}, {@link #rbf ResourceBuilderFactory}, and the {@link
     * #underTest Assembler under test}.  After executing, the {@link #assemblerInitialized initialization flag}
     * is set, insuring that the same instances of these classes are used for each test.
     */
    @Before
    public void setUpAssembler() {
        // Use a single instance of the Assembler and its dependencies for all tests
        if (!assemblerInitialized) {
            this.mbf = new DefaultMetadataBuilderFactory();
            this.rbf = new DefaultResourceBuilderFactory();
            this.underTest = assemblerUnderTest();
            assemblerInitialized = true;
        }
    }

    @Before
    public void setUpPackageVerifier() {
        this.verifier = packageVerifier();
    }

    @Test
    public void testMultiplePackageStreams() throws Exception {
        Map<String, Object> packageOptions = packageOptions();

        assertTrue("PackageOptions map must contain Archive.KEY", packageOptions.containsKey(Archive.KEY));
        assertTrue("PackageOptions map must contain Compression.KEY",
                   packageOptions.containsKey(Compression.KEY));
        assertFalse("PackageOptions map must contain a valid Archive option " +
                    "(Archive.OPTS.NONE is not supported)", packageOptions.containsValue(Archive.OPTS.NONE));

        Map<DepositSubmission, Future<PackageStream>> results = new HashMap<>();
        Map<DepositSubmission, File> packages = new HashMap<>();

        Random randomSubmission = new Random();
        LOG.info("Submitting packages to the assembler:");
        for (int i = 0; i < NO_THREADS; i++) {
            int submissionId = 0;
            do {
                submissionId = randomSubmission.nextInt(10);
            } while (submissionId < 1 || submissionId == 5 || submissionId == 7);

            URI submissionUri = URI.create("fake:submission" + submissionId);
            DepositSubmission submission = submissionUtil.asDepositSubmission(submissionUri, builder);
            LOG.info("Submitting package {}", submissionUri);
            LOG.info(".");
            results.put(submission, itExecutorService.submit(() -> underTest.assemble(submission, packageOptions)));
        }

        LOG.info("Waiting for results from the assembler, and saving each package:");
        // Get each result insuring no exceptions were thrown.
        results.forEach((submission, future) -> {
            try {
                LOG.info("{} ...", submission.getId());
                PackageStream stream = future.get();
                assertNotNull(stream.metadata());
                assertNotNull(stream.metadata().archive());
                assertNotNull(stream.metadata().compression());
                assertEquals(stream.metadata().archive(), packageOptions.get(Archive.KEY));
                assertEquals(stream.metadata().compression(), packageOptions.get(Compression.KEY));

                // TODO: verify resources metadata, but PackageStream#resources is unsupported

                // Read the stream and save it
                File packageFile = savePackage(packageFile(this.getClass(), testName, stream.metadata()), stream);
                packages.put(submission, packageFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        List<File> toClean = new ArrayList<>();

        LOG.info("Opening and verifying each package:");
        packages.forEach((submission, packageFile) -> {
            LOG.info(".");
            File dir;
            try {
                dir = openArchive(packageFile, (Archive.OPTS) packageOptions.get(Archive.KEY),
                                  (Compression.OPTS) packageOptions.get(Compression.KEY));
                LOG.info("Extracted package {} to {}", packageFile, dir);
                // Have subclass verify the content in the extracted package directory
                verifier.verify(submission, new ExplodedPackage(packageFile, dir), packageOptions);
                LOG.info("Successfully verified package in {}", dir);
                toClean.add(dir);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Upon success, add the package directories for cleanup.
        // Failure will leave the directories behind for diagnosis.
        packagesToCleanup.addAll(packages.values());
        packagesToCleanup.addAll(toClean);
    }

    /**
     * To be implemented by subclasses: must return a fully functional instance of the {@link Assembler} to be
     * tested.  {@link #setUpAssembler()} stores this instance as {@link #underTest}.  This instance is used to process
     * a {@link DepositSubmission} and produce a package containing the custodial content of the submission and any
     * supplemental files like BagIT tag files or additional metadata.
     *
     * @return the {@code AbstractAssembler} under test
     */
    protected abstract Assembler assemblerUnderTest();

    /**
     * To be implemented by subclasses: must return the {@link PackageOptions} that will be used when invoking
     * {@link Assembler#assemble(DepositSubmission, Map)}.  {@link Compression.OPTS} and {@link Archive.OPTS} must be
     * supplied.  Note that {@link Archive.OPTS#NONE} <strong>is not supported</strong> at this time.
     *
     * @return the package options supplied to the {@code Assembler} under test
     */
    protected abstract Map<String, Object> packageOptions();

    /**
     * Returns a {@link PackageVerifier} used to verify the package created by this test.
     *
     * @return the PackageVerifier
     */
    protected abstract PackageVerifier packageVerifier();

}
