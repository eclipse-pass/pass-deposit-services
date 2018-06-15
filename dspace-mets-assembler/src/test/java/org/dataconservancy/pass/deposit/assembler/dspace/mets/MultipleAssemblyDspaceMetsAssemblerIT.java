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

import org.dataconservancy.nihms.assembler.Assembler;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.dataconservancy.pass.deposit.assembler.shared.AbstractAssembler;
import org.dataconservancy.pass.deposit.assembler.shared.BaseAssemblerIT;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Map;

import static org.dataconservancy.pass.deposit.DepositTestUtil.packageToClasspath;
import static org.dataconservancy.pass.deposit.assembler.dspace.mets.DspaceDepositTestUtil.getMetsXml;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class MultipleAssemblyDspaceMetsAssemblerIT extends BaseDspaceMetsAssemblerIT {

    /**
     * Re-use the same assembler instance across tests.  This is to demonstrate that the collaborating objects,
     * including the DspaceMetsDomWriter, do not maintain state across invocations of {@link
     * Assembler#assemble(DepositSubmission)}
     */
    private static DspaceMetsAssembler underTest;

    @Override
    public void setUp() throws Exception {
        // override so that super class methods do nothing
    }

    /**
     * Creates an instance of DsspaceMetsAssembler that is shared across test method invocations.
     * See {@link #assemblePackage(String)}.
     */
    @BeforeClass
    public static void initAssembler() {
        underTest = new DspaceMetsAssembler(metadataBuilderFactory(), resourceBuilderFactory(),
                new DspaceMetadataDomWriter(DocumentBuilderFactory.newInstance()));
    }

    /**
     * Mocks a submission, and invokes the assembler to create a package based on the resources under the
     * {@code sample1/} resource path.  Sets the {@link #extractedPackageDir} to the base directory of the newly created
     * and extracted package.
     */
    private void assemblePackage(String classpathResourceBase) throws Exception {
        mbf = metadataBuilderFactory();
        rbf = resourceBuilderFactory();

        Map<File, DepositFileType> custodialContentWithTypes = prepareCustodialResources(classpathResourceBase);

        submission = prepareSubmission(custodialContentWithTypes);

        // Both tests in this IT will execute assemble(...) on the same instance of DspaceMetsAssembler
        PackageStream stream = underTest.assemble(submission);

        File packageArchive = savePackage(stream);

        verifyStreamMetadata(stream.metadata());

        extractPackage(packageArchive, stream.metadata().archive(), stream.metadata().compression());
    }

    @Test
    public void assembleSample1() throws Exception {
        assemblePackage(packageToClasspath(AbstractAssembler.class) + "/sample1");
        verifyPackageStructure(getMetsXml(extractedPackageDir), extractedPackageDir, custodialResources);
    }

    @Test
    public void assembleSample2() throws Exception {
        assemblePackage(packageToClasspath(AbstractAssembler.class) + "/sample2");
        verifyPackageStructure(getMetsXml(extractedPackageDir), extractedPackageDir, custodialResources);
    }

}
