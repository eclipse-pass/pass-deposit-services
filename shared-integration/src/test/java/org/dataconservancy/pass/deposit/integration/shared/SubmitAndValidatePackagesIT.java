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
package org.dataconservancy.pass.deposit.integration.shared;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.shared.PackageVerifier;
import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Math.floorDiv;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static org.dataconservancy.pass.deposit.DepositTestUtil.openArchive;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.COMPLETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static submissions.SubmissionResourceUtil.REPOSITORY_TYPE_FILTER;
import static submissions.SubmissionResourceUtil.SUBMISSION_TYPE_FILTER;
import static submissions.SubmissionResourceUtil.asJson;
import static submissions.SubmissionResourceUtil.asStream;
import static submissions.SubmissionResourceUtil.toInputStream;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class SubmitAndValidatePackagesIT extends AbstractSubmissionFixture {

    private static final Logger LOG = LoggerFactory.getLogger(SubmitAndValidatePackagesIT.class);

    /**
     * The number of threads to launch.  The {@link #itExecutorService} should account for this and allow this
     * number of threads to execute simultaneously.
     */
    private static final int NO_THREADS = 10;

    /**
     * Used to name the threads created by the {@link #itExecutorService}.
     */
    private static final AtomicInteger IT_THREAD = new AtomicInteger(0);

    /**
     * Manages the threads used for the test execution.  Each thread will use the same instance of the {@code
     * Assembler} under test, and run {@link Assembler#assemble(DepositSubmission, Map)}.
     */
    private static ExecutorService itExecutorService;

    private PassJsonFedoraAdapter passAdapter = new PassJsonFedoraAdapter();

    private PackageVerifier verifier;

    private Map<String, Object> packageOpts;

    private Set<File> packageDirs = new HashSet<>();

    /**
     * Instantiates the {@link #itExecutorService}.
     */
    @BeforeClass
    public static void setUpExecutorService() {
        ThreadFactory itTf = r -> new Thread(r, "SubmitAndValidatePackagesITPool-" + IT_THREAD.getAndIncrement());
        itExecutorService = new ThreadPoolExecutor(floorDiv(NO_THREADS, 2), NO_THREADS, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(floorDiv(NO_THREADS, 2)), itTf);
    }

    /**
     * This integration test won't work if the {@code accessUrl} carries a {@code fedora_uri normalizer} according to
     * the ElasticSearch index configuration.  {@code copyIndex()} attempts to copy an existing index to a new location
     * with an updated configuration that removes the {@code normalizer} from {@code accessUrl}.
     *
     * @throws IOException if there is an error creating or copying indexes around
     */
    @BeforeClass
    public static void copyIndex() throws IOException {
        OkHttpClient okHttp = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS).build();

        URL passIndexUrl = new URL(getenv().getOrDefault("PASS_ELASTICSEARCH_URL",
                getProperty("pass.elasticsearch.url")));

        assertNotNull("Missing value for PASS_ELASTICSEARCH_URL environment variable or " +
                "pass.elasticsearch.url system property", passIndexUrl);

        if (!accessUrlFieldMappingCarriesNormalizer(okHttp, passIndexUrl)) {
            return;
        }

        URL newIndexUrl = new URL(passIndexUrl, "/pass2");
        ObjectMapper objectMapper = new ObjectMapper();

        IndexUtil indexUtil = new IndexUtil(objectMapper, okHttp);
        indexUtil.copyIndex(passIndexUrl, newIndexUrl, transformConfig(objectMapper));
    }

    @Before
    public void initPackageOptions() {
        this.packageOpts = getPackageOpts();
    }

    @Before
    public void initVerifier() throws Exception {
        this.verifier = getVerifier();
    }

    @Override
    @Before
    public void createSubmission() throws Exception {
        Map<Future<URI>, Map<URI, PassEntity>> futureSubmissions = new HashMap<>();

        // Create a Submission resource in Fedora for each test submission resource in the 'shared-resources' module

        Random randomId = new Random();
        LOG.info("Submitting content to the PASS repository:");
        for (int i = 0; i < NO_THREADS; i++) {
            int submissionId = 0;
            do {
                submissionId = randomId.nextInt(10);
            } while (submissionId < 1 || submissionId == 5 || submissionId == 7);  // weeds out invalid submissions

            URI localSubmissionUri = URI.create("fake:submission" + submissionId);

            JsonNode root = asJson(localSubmissionUri);
            asStream(root).forEach(node -> {
                // If the sample submission claims to be have been submitted, flip its submitted flag to false
                // (they'll be triggered for submission later in the IT)
                if (SUBMISSION_TYPE_FILTER.test(node)) {
                    ((ObjectNode) node).put("submitted", false);
                }
                // FIXME: this munging is to align the Repositories in the sample submissions from shared-resources
                //    with the configured repositories in repositories.json.  What may be better is to gather all the
                //    repositories from the sample submissions, and then create the proper resources in Fedora, and
                //    automatically generate a repositories.json configuration that wires up those repositories.
                if (REPOSITORY_TYPE_FILTER.test(node)) {
                    if (!node.has("repositoryKey")) {
                        LOG.debug("Adding repositoryKey {}", "jscholarship");
                        ((ObjectNode) node).put("repositoryKey", "jscholarship");
                    } else {
                        String repositoryKey = node.get("repositoryKey").asText();
                        LOG.debug("Attempting to transform repositoryKey {}", repositoryKey);
                        if (repositoryKey.matches("PubMed Central")) {
                            LOG.debug("Transformed repositoryKey {}", repositoryKey);
                            ((ObjectNode) node).put("repositoryKey", "pmc");
                        }

                        if (repositoryKey.matches("JScholarship")) {
                            LOG.debug("Transformed repositoryKey {}", repositoryKey);
                            ((ObjectNode) node).put("repositoryKey", "jscholarship");
                        }
                    }
                }
            });

            HashMap<URI, PassEntity> submissionMap = new HashMap<>();

            LOG.info("Creating Submission in the Fedora repository for {}", localSubmissionUri);
            Future<URI> passSubmissionUri =
                    itExecutorService.submit(() -> passAdapter.jsonToFcrepo(toInputStream(root), submissionMap));
            futureSubmissions.put(passSubmissionUri, submissionMap);
        }

        Map<URI, Map<URI, PassEntity>> submissions =
                futureSubmissions.entrySet().stream().collect(Collectors.toMap((entry) -> {
            try {
                URI submissionUri = entry.getKey().get();
                LOG.info("Created Submission {}", submissionUri);
                return submissionUri;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, Map.Entry::getValue));

        // Trigger Deposit Services to process the Submission by flipping the Submission.submitted flag to 'true'

        submissions.keySet().stream()
                .map(submissionUri -> passClient.readResource(submissionUri, Submission.class))
                .filter(submission -> Boolean.FALSE.equals(submission.getSubmitted()))
                .forEach(submission -> {
                    // Trigger the Submission so that Deposit Services will package the custodial content and perform a
                    // deposit to a downstream repository
                    LOG.info("Triggering Submission {}", submission.getId());
                    triggerSubmission(submission.getId());
                });

        // Wait for Deposit Services to process each Submission by searching for RepositoryCopies that are ACCEPTED
        // which have incoming links matching a Submission we just created.  There should be one RepositoryCopy for
        // each Repository for each Submission.

        long expectedRepositoryCopyCount = submissions.values().stream()
                .flatMap(map -> map.values().stream())
                .filter(entity -> entity instanceof Repository)
                .count();

        // A Condition executes this logic, and provides the results (the Set of RepositoryCopy resources created for
        // each Submission)

        Condition<Set<URI>> repoCountCondition = new Condition<>(
        () -> {
            Map<String, Object> attributes = new HashMap<String, Object>() {
                {
                    put("copyStatus", COMPLETE.toString().toLowerCase());
                    put("accessUrl", "file:/packages/*");
                }
            };

            LOG.debug("Executing Repository Count Condition");

            Set<URI> candidates = passClient.findAllByAttributes(RepositoryCopy.class, attributes);
            LOG.debug("Found {} candidates", candidates.size());

            Set<URI> results = candidates.stream()
                    .filter(candidateRepoCopyUri -> {
                        if (!candidateRepoCopyUri.toString().startsWith(fcrepoBaseUrl)) {
                            LOG.warn("Excluding RepositoryCopy with unknown base url: {} " + "(doesn't start with {})",
                                    candidateRepoCopyUri, fcrepoBaseUrl);
                            return false;
                        }
                        return true;
                    })
                    .filter(candidateRepoCopyUri -> {
                        // Filter RepositoryCopy URIs that have in incoming link from a Deposit resource created
                        // for each Submission.
                        Map<String, Collection<URI>> incoming = passClient.getIncoming(candidateRepoCopyUri);
                        return incoming.getOrDefault("repositoryCopy", Collections.emptySet())
                                .stream()
                                .map(depositUri -> passClient.readResource(depositUri, Deposit.class))
                                .anyMatch(deposit -> submissions.keySet().contains(deposit.getSubmission()));
                        })
                    .collect(Collectors.toSet());

            LOG.debug("Found {} RepositoryCopies", results.size());
            return results;
        },

        // There should be a RepositoryCopy for each Repository for each Submission

        (results) -> {
            if (results.size() > 0) {
                LOG.debug("Discovered {} Repository Copies: ", results.size());
                results.forEach(repoCopy -> LOG.debug("  {}", repoCopy));
            }
            return results.size() == expectedRepositoryCopyCount;
            }, "Repository Copy Count");

        LOG.info("Waiting for {} Submissions to be processed and produce {} expected Repository Copies " + "by " +
                "Deposit Services.", submissions.size(), expectedRepositoryCopyCount);
        repoCountCondition.setTimeoutThresholdMs(300 * 1000);
        repoCountCondition.await();

        Set<URI> repositoryCopies = repoCountCondition.getResult();
        // sanity
        assertEquals(expectedRepositoryCopyCount, repositoryCopies.size());

        // For each RepositoryCopy, examine the externalIds field for the package location
        // Explode the package to a temporary directory

        repositoryCopies.stream()
                .map(uri -> passClient.readResource(uri, RepositoryCopy.class))
                .flatMap(rc -> rc.getExternalIds().stream())
                .map(URI::create)
                .map(URI::getPath)
                .map(path -> "target" + path)
                .map(File::new)
                .forEach(packageFile -> {
                    try {
                        LOG.info("Exploding {}", packageFile);
                        File packageDir = openArchive(packageFile,
                                (PackageOptions.Archive.OPTS) packageOpts.get(PackageOptions.Archive.KEY),
                                (PackageOptions.Compression.OPTS) packageOpts.get(PackageOptions.Compression.KEY));
                        LOG.info("{} exploded to {}", packageFile, packageDir);
                        packageDirs.add(packageDir);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        // Sanity - one package per RepositoryCopy

        assertEquals(expectedRepositoryCopyCount, packageDirs.size());
    }

    protected abstract Map<String, Object> getPackageOpts();

    protected abstract PackageVerifier getVerifier();

    /**
     * Answers a {@code Function} that accepts the ElasticSearch index configuration, removes the {@code normalizer}
     * from the {@code accessUrl} field if it exists.  Returns a new JSON object that contains the {@code
     * settings} and
     * updated {@code mappings}, suitable for use when creating a new index.
     *
     * @param objectMapper a Jackson ObjectMapper instance
     * @return a {@code Function} which transforms a {@code JsonNode} representing the configuration of an index
     */
    private static Function<JsonNode, JsonNode> transformConfig(ObjectMapper objectMapper) {
        return jsonNode -> {
            JsonNode settings = jsonNode.findValue("settings");
            JsonNode mappings = jsonNode.findValue("mappings");

            ObjectNode accessUrlConfig = (ObjectNode) mappings.findValue("accessUrl");
            if (accessUrlConfig.get("normalizer") != null) {
                accessUrlConfig.remove("normalizer");
            }

            TreeNode transformed = objectMapper.getFactory().getCodec().createObjectNode();

            ((ObjectNode) transformed).set("settings", settings);
            ((ObjectNode) transformed).set("mappings", mappings);

            return (ObjectNode) transformed;
        };
    }

    /**
     * Determines if the ElasticSearch configuration at the provided url carries a {@code normalizer} on the {@code
     * accessUrl} field in its mapping.
     *
     * @param okHttp the OkHttpClient
     * @param passIndexUrl the URL to the ElasticSearch PASS index
     * @return true if the index has an {@code accessUrl} field with a {@code normalizer}
     * @throws IOException if communication with the index fails
     */
    private static boolean accessUrlFieldMappingCarriesNormalizer(OkHttpClient okHttp, URL passIndexUrl) throws IOException {
        try (Response res = okHttp.newCall(new Request.Builder().url(passIndexUrl).get().build()).execute()) {
            JsonNode config = new ObjectMapper().readTree(res.body().string());
            JsonNode accessUrl = config.findValue("accessUrl");
            if (accessUrl != null && accessUrl.has("normalizer")) {
                return true;
            }
        }
        return false;
    }
}
