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

import static java.lang.Math.floorDiv;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.net.URI.create;
import static org.dataconservancy.pass.deposit.DepositTestUtil.openArchive;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static submissions.SubmissionResourceUtil.REPOSITORY_TYPE_FILTER;
import static submissions.SubmissionResourceUtil.SUBMISSION_TYPE_FILTER;
import static submissions.SubmissionResourceUtil.asJson;
import static submissions.SubmissionResourceUtil.asStream;
import static submissions.SubmissionResourceUtil.toInputStream;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.client.PassJsonAdapter;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.shared.ExplodedPackage;
import org.dataconservancy.pass.deposit.assembler.shared.PackageVerifier;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.builder.fs.FcrepoModelBuilder;
import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integration test fixture that performs submissions against Deposit Services and gathers the resulting packages for
 * verification by subclasses.
 * <p>
 * Subclasses must return a {@code PackageVerifier} for each package generated by this IT.  The {@code PackageVerifier}
 * encapsulates the <em>all</em> of the logic for insuring that a package for a given Submission is valid.
 * </p>
 * <p>
 * This IT creates a number of Submission graphs (a Submission along with its Repositories, Files, etc) in the PASS
 * repository.  Each Submission resource is triggered for processing by Deposit Services (as Ember would, by flipping
 * Submission.submitted to true).  Deposit Services will invoke the Assembler according to its configuration, and place
 * a package on the file system using the file system transport.  Each package will be verified using a {@link
 * PackageVerifier} provided by the subclass.
 * </p>
 * <p>
 * This IT treats Deposit Services as a black box, but as noted above, it does expect that Deposit Services will produce
 * packages on the filesystem.  After triggering the Submissions in PASS, this IT waits for RepositoryCopy resources to
 * show up in the index with a RepositoryCopy.copyStatus of complete.  The RepositoryCopy.externalIds is used to locate
 * the generated package on the filesystem and explode it.  This means that subclasses <em>must</em> configure the
 * {@code FilesystemTransport} to be used with their IT.  This will result in {@code RepositoryCopy} resources having
 * {@code file:/} URIs as their {@code accessUrl} and {@code externalIds} (a future enhancement for this IT would be to
 * support http URI schemes, eliminating the requirement that packages must be produced on the local filesystem).
 * </p>
 * <p>
 * Subclasses are provided the DepositSubmission and the location of the exploded package, useful for providing the
 * proper {@code PackageVerifier} (e.g. there may be a {@code PackageVerifier} implementation for each packaging
 * specification).
 * </p>
 * <h4>Example Deposit Services Configuration</h4>
 * Below is an example configuration that a subclass might use.  There are two repositories configured, one for {@code
 * pmc} and one for {@code jscholarship}.  Note that each repository configuration uses the filesystem transport in
 * their {@code transport-config}.  This is so that the packages produced by Deposit Services go to a local directory
 * for access.
 * <pre>
 *  {
 *   "jscholarship": {
 *     "deposit-config": {
 *       "processing": {
 *         "beanName": "org.dataconservancy.pass.deposit.messaging.status.DefaultDepositStatusProcessor"
 *       },
 *       "mapping": {
 *         "http://dspace.org/state/archived": "accepted",
 *         "http://dspace.org/state/withdrawn": "rejected",
 *         "default-mapping": "submitted"
 *       }
 *     },
 *     "assembler": {
 *       "specification": "http://purl.org/net/sword/package/METSDSpaceSIP",
 *       "beanName": "dspaceMetsAssembler",
 *       "options": {
 *         "archive": "ZIP",
 *         "compression": "NONE",
 *         "algorithms": [
 *           "sha512",
 *           "md5"
 *         ]
 *       }
 *     },
 *     "transport-config": {
 *       "protocol-binding": {
 *         "protocol": "filesystem",
 *         "baseDir": "/packages/jscholarship",
 *         "createIfMissing": "true",
 *         "overwrite": "false"
 *       }
 *     }
 *   },
 *   "pmc": {
 *     "assembler": {
 *       "specification": "nihms-native-2017-07",
 *       "beanName": "nihmsAssembler",
 *       "options": {
 *         "archive": "ZIP",
 *         "compression": "NONE",
 *         "algorithms": [
 *           "sha512",
 *           "md5"
 *         ]
 *       }
 *     },
 *     "transport-config": {
 *       "protocol-binding": {
 *         "protocol": "filesystem",
 *         "baseDir": "/packages/pmc",
 *         "createIfMissing": "true",
 *         "overwrite": "false"
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see PackageVerifier
 */
@RunWith(SpringRunner.class)
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

    /**
     * HTTP client used to execute operations against the index
     */
    private static OkHttpClient okHttp;

    /**
     * The PASS Elastic Search URL (equivalent to the environment variable {@code PASS_ELASTICSEARCH_URL} or
     * system property {@code pass.elasticsearch.url})
     */
    private static URL passIndexUrl;

    /**
     * Used to submit a Submission graph (i.e. a Submission and all linked entities) to the Fedora PASS repository
     */
    private PassJsonFedoraAdapter passAdapter = new PassJsonFedoraAdapter();

    /**
     * Can convert JSON representations of PASS resources to Java representations
     */
    private PassJsonAdapter jsonAdapter = new PassJsonAdapterBasic();

    /**
     * Builds DepositSubmission from a Submission resource.
     */
    private SubmissionBuilder builder = new FcrepoModelBuilder();

    /**
     * A Map of each DepositSubmission to its location on the filesystem.
     * <p>
     * This IT creates a number of Submission graphs (a Submission, Repositories, Files, etc) in the PASS repository.
     * Each Submission resource is triggered for processing by Deposit Services, which places a package in the
     * 'target/packages' directory on the file system.
     * </p>
     * <p>
     * This Map contains the location of the package created for each Submission, but instead of using the Submission
     * as the key, the internal Deposit Services class, DepositSubmission, is used.
     * </p>
     * <p>
     * Subclasses are responsible for verifying that the the package contains the correct information.
     * </p>
     */
    private Map<DepositSubmission, ExplodedPackage> toVerify;

    /**
     * Instantiates the {@link #itExecutorService} used to create Submission graphs in Fedora
     */
    @BeforeClass
    public static void setUpExecutorService() {
        ThreadFactory itTf = r -> new Thread(r, "SubmitAndValidatePackagesITPool-" + IT_THREAD.getAndIncrement());
        itExecutorService = new ThreadPoolExecutor(floorDiv(NO_THREADS, 2), NO_THREADS, 10,
                                                   TimeUnit.SECONDS, new ArrayBlockingQueue<>(floorDiv(NO_THREADS, 2)),
                                                   itTf);
    }

    /**
     * This integration test won't work if the {@code accessUrl} carries a {@code fedora_uri normalizer} according to
     * the ElasticSearch index configuration.  {@code copyIndex()} attempts to copy an existing index to a new location
     * with an updated configuration that removes the {@code normalizer} from {@code accessUrl}.
     * <p>
     * The old index location will be aliased to the new index location, so the environment variables and various
     * clients of the index won't have to be updated with the new location.
     * </p>
     *
     * @throws IOException if there is an error creating or copying indexes around
     */
    @BeforeClass
    public static void copyIndex() throws IOException {
        okHttp = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).build();

        passIndexUrl = new URL(getenv().getOrDefault("PASS_ELASTICSEARCH_URL",
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
    public void preparePackagesForVerification() throws Exception {
        Collection<Submission> submissions =
            performSubmissions()
                .stream()
                .map(submissionUri -> passClient.readResource(submissionUri, Submission.class))
                .collect(Collectors.toSet());

        // Wait for Deposit Services to process each Submission by searching for RepositoryCopies that are ACCEPTED
        // which have incoming links matching a Submission we just created.  There should be one RepositoryCopy for
        // each Repository for each Submission.

        long expectedRepositoryCopyCount = submissions.stream()
              .flatMap(submission -> submission.getRepositories().stream())
              .map(repoUri -> passClient.readResource(repoUri, Repository.class))
              // Filter out Repositories that have an integration type of web
              // link, because Deposit Services will not
              // attempt deposits to those repositories
              .filter(repo -> Repository.IntegrationType.WEB_LINK != repo.getIntegrationType())
              .count();

        // A Condition executes this logic, and provides the results (the Set of RepositoryCopy resources created for
        // each Submission)

        Condition<Collection<RepositoryCopy>> repoCountCondition = new Condition<>(
            () -> {
                LOG.debug("Executing Repository Count Condition");

                Collection<RepositoryCopy> candidates = RepositoryCopyPackageQuery.execute(
                    okHttp, jsonAdapter, passIndexUrl.toString() + "/_search");

                LOG.debug("Found {} candidates", candidates.size());

                Set<RepositoryCopy> results = candidates.stream()
                                                        .filter(candidate -> {
                                                            if (!candidate.getId().toString()
                                                                          .startsWith(fcrepoBaseUrl)) {
                                                                LOG.warn(
                                                                    "Excluding RepositoryCopy with unknown base url: " +
                                                                    "{} " + "(doesn't start with {})",
                                                                    candidate, fcrepoBaseUrl);
                                                                return false;
                                                            }
                                                            return true;
                                                        })
                                                        .filter(candidate -> {
                                                            // Filter RepositoryCopies that have in incoming link
                                                            // from a Deposit resource created
                                                            // for each Submission.
                                                            Map<String, Collection<URI>> incoming =
                                                                passClient.getIncoming(
                                                                candidate.getId());
                                                            Collection<URI> submissionUris = submissions
                                                                .stream()
                                                                .map(Submission::getId)
                                                                .collect(Collectors.toSet());
                                                            return incoming.getOrDefault("repositoryCopy",
                                                                                         Collections.emptySet())
                                                                           .stream()
                                                                           .map(depositUri -> passClient.readResource(
                                                                               depositUri, Deposit.class))
                                                                           .map(Deposit::getSubmission)
                                                                           .anyMatch(submissionUris::contains);
                                                        })
                                                        .collect(Collectors.toSet());

                LOG.debug("Found {} RepositoryCopies", results.size());
                return results;
            },

            // There should be a RepositoryCopy for each Repository for each Submission

            (results) -> {
                if (results.size() > 0) {
                    LOG.debug("Discovered {} Repository Copies: ", results.size());
                    results.forEach(repoCopy -> LOG.debug("  {}", repoCopy.getId()));
                }
                return results.size() == expectedRepositoryCopyCount;
            }, "Repository Copy Count");

        LOG.info("Waiting for {} Submissions to be processed and produce {} expected Repository Copies " +
                 "by Deposit Services.", submissions.size(), expectedRepositoryCopyCount);
        repoCountCondition.setTimeoutThresholdMs(300 * 1000);
        repoCountCondition.setBackoffFactor(1.1f);
        repoCountCondition.await();

        Collection<RepositoryCopy> repositoryCopies = repoCountCondition.getResult();
        // sanity
        assertEquals(expectedRepositoryCopyCount, repositoryCopies.size());

        // For each RepositoryCopy, examine the externalIds field for the package location
        // Explode the package to a temporary directory

        Map<RepositoryCopy, ExplodedPackage> packageDirs = new HashMap<>();

        repositoryCopies.forEach(repoCopy -> {
            File packageFile = new File("target" +
                                        URI.create(repoCopy.getExternalIds().iterator().next()).getPath());
            try {
                LOG.info("Exploding {}", packageFile);
                File packageDir = openArchive(packageFile,
                                              sniffArchive(packageFile), sniffCompression(packageFile));
                LOG.info("{} exploded to {}", packageFile, packageDir);
                packageDirs.put(repoCopy, new ExplodedPackage(packageFile, packageDir));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Sanity - one package per RepositoryCopy

        assertEquals(expectedRepositoryCopyCount, packageDirs.size());

        toVerify = repositoryCopies
            .stream()
            .collect(Collectors.toMap(
                (repoCopy) -> {
                    URI depositUri = passClient.getIncoming(repoCopy.getId())
                                               .get("repositoryCopy").stream()
                                               .findFirst()
                                               .orElseThrow(() ->
                                                                new RuntimeException(
                                                                    "Missing expected incoming link 'Deposit -> " +
                                                                    "repositoryCopy'"));
                    URI submissionUri = passClient.readResource(depositUri, Deposit.class).getSubmission();
                    try {
                        return builder.build(submissionUri.toString());
                    } catch (InvalidModel e) {
                        throw new RuntimeException(e);
                    }
                }, packageDirs::get));
    }

    /**
     * Retrieves a {@code PackageVerifier} from the subclass for each package generated by this IT and invokes
     * {@link PackageVerifier#verify(DepositSubmission, ExplodedPackage, Map)} with an <em>empty map</em>.
     * <h4>Implementation note</h4>
     * This class does not have the package options used to generate a given package.  Therefore the package options
     * supplied to {@link PackageVerifier#verify(DepositSubmission, ExplodedPackage, Map)} will <em>always</em> be an
     * empty Map.
     * It's unfortunate, but because the Deposit Services runtime is configured by the subclass, there's no way for this
     * class to know what they are without some heavy lifting.
     */
    @Test
    public void verifyPackages() {
        toVerify.forEach((depositSubmission, explodedPackage) -> {
            try {
                PackageVerifier verifier = getVerifier(depositSubmission, explodedPackage);
                LOG.debug("Invoking verify on {} (original archive: {}), using {}",
                          explodedPackage.getExplodedDir(), explodedPackage.getPackageFile(), verifier);
                verifier.verify(depositSubmission, explodedPackage, Collections.emptyMap());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Obtain the {@link PackageVerifier} used to verify each package generated by Deposit Services.  If the subclass
     * has configured multiple repositories with different packaging specifications, the {@code submission} and
     * {@code packageDir} can be used to determine which package spec was used, and the proper {@code PackageVerifier}
     * implementation can be returned.
     * <h4>Implementation note</h4>
     * This class does not have the package options used to generate a given package.  Therefore the package options
     * supplied to {@link PackageVerifier#verify(DepositSubmission, ExplodedPackage, Map)} will <em>always</em> be an
     * empty Map.
     * It's unfortunate, but because the Deposit Services runtime is configured by the subclass, there's no way for this
     * class to know what they are without some heavy lifting.
     *
     * @param submission      the Submission being verified as a DepositSubmission
     * @param explodedPackage the exploded package containing its original filename and exploded location
     * @return the PackageVerifier
     */
    protected abstract PackageVerifier getVerifier(DepositSubmission submission, ExplodedPackage explodedPackage);

    /**
     * It is the responsibility of this method to create at least one Submission graph in the PASS repository, and
     * trigger it for processing by Deposit Services (setting Submission.submitted to true).  The Submissions created
     * and submitted by this method are returned.
     * <p>
     * By default this implementation will create multiple Submissions from the shared-resources module.  Subclasses may
     * override this method, and supply their own Submission or Submissions.  If subclasses choose to override this
     * method, they are responsible for persisting entire Submission graphs in the PASS repository and triggering their
     * processing.
     * </p>
     *
     * @return a Collection of Submissions that have been persisted in the PASS respository and submitted for processing
     * by Deposit Services
     */
    protected Collection<URI> performSubmissions() {
        Map<Future<URI>, Map<URI, PassEntity>> futureSubmissions = new HashMap<>();

        // Create a Submission resource for each test submission resource in the 'shared-resources' module

        Random randomId = new Random();
        LOG.info("Submitting content to the PASS repository:");
        for (int i = 0; i < NO_THREADS; i++) {
            int submissionId = 0;
            do {
                submissionId = randomId.nextInt(10);
            } while (submissionId < 1 || submissionId == 5 || submissionId == 7);  // weeds out invalid submissions

            URI localSubmissionUri = create("fake:submission" + submissionId);

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

            // Put the Submission resources in Fedora in a background thread

            HashMap<URI, PassEntity> submissionMap = new HashMap<>();

            LOG.info("Creating Submission in the Fedora repository for {}", localSubmissionUri);
            Future<URI> passSubmissionUri =
                itExecutorService.submit(() -> passAdapter.jsonToFcrepo(toInputStream(root), submissionMap).getId());
            futureSubmissions.put(passSubmissionUri, submissionMap);
        }

        // Wait for the Submission resources to be created in Fedora

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

        // Trigger Deposit Services to process each Submission by flipping the Submission.submitted flag to 'true'

        submissions.keySet().stream()
                   .map(submissionUri -> passClient.readResource(submissionUri, Submission.class))
                   .filter(submission -> Boolean.FALSE.equals(submission.getSubmitted()))
                   .forEach(submission -> {
                       // Trigger the Submission so that Deposit Services will package the custodial content and
                       // perform a
                       // deposit to a downstream repository
                       LOG.info("Triggering Submission {}", submission.getId());
                       triggerSubmission(submission.getId());
                   });

        return submissions.keySet();
    }

    /**
     * Determines the compression format based on the package file name.
     * <p>
     * This implementation tests for ".gz", ".gzip", ".zip", ".bz2", and ".bzip2"
     * </p>
     *
     * @param packageFile the package file generated by this IT
     * @return the compression format
     */
    protected PackageOptions.Compression.OPTS sniffCompression(File packageFile) {
        if (packageFile.getName().endsWith(".gz") || packageFile.getName().endsWith(".gzip")) {
            return PackageOptions.Compression.OPTS.GZIP;
        }

        if (packageFile.getName().endsWith(".zip")) {
            return PackageOptions.Compression.OPTS.ZIP;
        }

        if (packageFile.getName().endsWith(".bz2") || packageFile.getName().endsWith(".bzip2")) {
            return PackageOptions.Compression.OPTS.BZIP2;
        }

        return PackageOptions.Compression.OPTS.NONE;
    }

    /**
     * Determines the archive format based on the package file name.
     * <p>
     * This implementation tests for ".tar.gz", ".tar.gzip", ".tar", and ".zip".
     * </p>
     *
     * @param packageFile the package file generated by this IT
     * @return the archive format
     */
    protected PackageOptions.Archive.OPTS sniffArchive(File packageFile) {
        if (packageFile.getName().endsWith(".tar.gz") ||
            packageFile.getName().endsWith(".tar.gzip") ||
            packageFile.getName().endsWith(".tar")) {
            return PackageOptions.Archive.OPTS.TAR;
        }

        if (packageFile.getName().endsWith(".zip")) {
            return PackageOptions.Archive.OPTS.ZIP;
        }

        return PackageOptions.Archive.OPTS.NONE;
    }

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
     * @param okHttp       the OkHttpClient
     * @param passIndexUrl the URL to the ElasticSearch PASS index
     * @return true if the index has an {@code accessUrl} field with a {@code normalizer}
     * @throws IOException if communication with the index fails
     */
    private static boolean accessUrlFieldMappingCarriesNormalizer(OkHttpClient okHttp, URL passIndexUrl)
        throws IOException {
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
