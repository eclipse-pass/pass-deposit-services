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

package org.dataconservancy.pass.deposit.assembler.shared;

import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.DepositTestUtil.composeSubmission;
import static org.dataconservancy.pass.deposit.DepositTestUtil.fromClasspath;
import static org.dataconservancy.pass.deposit.DepositTestUtil.openArchive;
import static org.dataconservancy.pass.deposit.DepositTestUtil.packageToClasspath;
import static org.dataconservancy.pass.deposit.DepositTestUtil.tmpFile;
import static org.junit.Assert.assertTrue;

/**
 * Abstract integration test for {@link AbstractAssembler} implementations.
 * <p>
 * Creates and extracts a package using the {@link #assemblerUnderTest() assembler under test}.  Subclasses have access
 * to the extracted package directory as {@link #extractedPackageDir}, and to the contents being packaged (in various
 * forms):
 * <ul>
 *     <li>{@link #custodialResources}: a simple {@code List} of Spring {@link Resource}s</li>
 *     <li>{@link #custodialResourcesMap}: a {@code Map} of Spring {@link Resource}s, keyed by resource name</li>
 * </ul>
 * </p>
 */
public abstract class BaseAssemblerIT {

    @Rule
    public TestName testName = new TestName();

    protected static final Logger LOG = LoggerFactory.getLogger(BaseAssemblerIT.class);

    /**
     * The custodial resources that to be packaged up by {@link #setUp()}.  They should be present in the extracted
     * package.
     */
    protected List<Resource> custodialResources;

    /**
     * The custodial resources that to be packaged up by {@link #setUp()}, keyed by file name.  They should be present
     * in the extracted package
     */
    protected Map<String, Resource> custodialResourcesMap;

    /**
     * The package generated by {@link #setUp()} is extracted to this directory
     */
    protected File extractedPackageDir;

    /**
     * The {@link ResourceBuilderFactory} used by the {@link AbstractAssembler} to create {@link
     * PackageStream.Resource}s from the {@link #custodialResources custodial resources}
     */
    protected ResourceBuilderFactory rbf;

    /**
     * The {@link MetadataBuilderFactory} used by the {@link AbstractAssembler} to create {@link
     * PackageStream.Metadata}
     */
    protected MetadataBuilderFactory mbf;

    /**
     * The submission that the {@link #extractedPackageDir extracted package} is composed from
     */
    protected DepositSubmission submission;

    /**
     * Mocks a submission, and invokes the assembler to create a package based on the resources under the
     * {@code sample1/} resource path.  Sets the {@link #extractedPackageDir} to the base directory of the newly created
     * and extracted package.
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        mbf = metadataBuilderFactory();
        rbf = resourceBuilderFactory();
        AbstractAssembler underTest = assemblerUnderTest();

        Map<File, DepositFileType> custodialContentWithTypes = prepareCustodialResources();

        submission = prepareSubmission(custodialContentWithTypes);

        PackageStream stream = underTest.assemble(submission);

        File packageArchive = savePackage(stream);

        extractPackage(packageArchive);
    }

    /**
     * Creates a {@link DepositSubmission} from the custodial content.
     *
     * @param custodialContentWithTypes the custodial content, with each file mapped to a content type
     * @return the newly created submission
     */
    protected DepositSubmission prepareSubmission(Map<File, DepositFileType> custodialContentWithTypes) {
        return composeSubmission(this.getClass().getName() + "-" + testName.getMethodName(),
                custodialContentWithTypes);
    }

    /**
     * Obtains a List of Resources from the classpath, stores them in {@link #custodialResources}.
     *
     * Creates a convenience {@code Map}, mapping file names to their corresponding Resources in {@link
     * #custodialResourcesMap}.  Every Resource in {@code custodialResources} should be represented in {@code
     * custodialResourcesMap}, and vice-versa.
     *
     * {@link #mapContentTypes(Map) Maps} the Resources to deposit content types from {@link DepositFileType}.
     *
     * @return a {@code Map} of custodial resources to be packaged, and their corresponding {@code DepositFileType}
     */
    protected Map<File, DepositFileType> prepareCustodialResources() {
        custodialResources = fromClasspath(packageToClasspath(AbstractAssembler.class) + "/sample1");
        // Insure we're packaging something
        assertTrue("Refusing to create an empty package!",custodialResources.size() > 0);

        custodialResourcesMap = custodialResources.stream().collect(Collectors.toMap(Resource::getFilename, Function
                .identity()));

        return mapContentTypes(custodialResourcesMap);
    }

    /**
     * Extracts the supplied package archive file (.zip, .gzip, etc) to the {@link #extractedPackageDir}.
     *
     * @param packageArchive the package archive file to open
     * @throws IOException if there is an error opening the package
     */
    protected void extractPackage(File packageArchive) throws IOException {
        extractedPackageDir = openArchive(packageArchive);

        LOG.debug(">>>> Extracted package to '{}'", extractedPackageDir);
    }

    /**
     * Saves the supplied {@link PackageStream} to a temporary file.
     *
     * @param stream the {@code PackageStream} generated by the assembler under test
     * @return the {@code File} representing the saved package
     * @throws IOException if there is an error saving the package
     */
    protected File savePackage(PackageStream stream) throws IOException {
        File tmpOut = tmpFile(this.getClass(), testName, ".zip");

        try (InputStream in = stream.open()) {
            Files.copy(in, tmpOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        LOG.debug(">>>> Wrote package to '{}'", tmpOut);
        return tmpOut;
    }

    /**
     * Maps the supplied custodial {@link Resource}s to {@link File}s associated with their {@link DepositFileType}s.
     *
     * @param custodialResourcesMap the custodial resources being included in the package, keyed by file name
     * @return a map of {@code File} objects and their corresponding {@code DepositFileType}
     */
    protected Map<File, DepositFileType> mapContentTypes(Map<String, Resource> custodialResourcesMap) {
        Map<File, DepositFileType> custodialContentWithTypes = new HashMap<>();

        custodialResourcesMap.forEach((fileName, resource) -> {
            if (fileName.endsWith(".doc")) {
                try {
                    custodialContentWithTypes.put(resource.getFile(), DepositFileType.manuscript);
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            } else {
                try {
                    custodialContentWithTypes.put(resource.getFile(), DepositFileType.supplemental);
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        });

        return custodialContentWithTypes;
    }

    /**
     * Returns a new instance of the {@link DefaultMetadataBuilderFactory}
     *
     * @return
     */
    protected MetadataBuilderFactory metadataBuilderFactory() {
        return new DefaultMetadataBuilderFactory();
    }

    /**
     * Returns a new instance of the {@link DefaultResourceBuilderFactory}
     *
     * @return
     */
    protected ResourceBuilderFactory resourceBuilderFactory() {
        return new DefaultResourceBuilderFactory();
    }

    /**
     * To be implemented by sub-classes: must return a fully functional instance of the {@link AbstractAssembler} to be
     * tested.
     *
     * @return the {@code AbstractAssembler} under test
     */
    protected abstract AbstractAssembler assemblerUnderTest();
}