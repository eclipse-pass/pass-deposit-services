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
package org.dataconservancy.pass.deposit.messaging.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static submissions.SubmissionResourceUtil.lookupStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.dataconservancy.deposit.util.async.Condition;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.PreassembledAssembler;
import org.dataconservancy.pass.deposit.integration.shared.AbstractSubmissionFixture;
import org.dataconservancy.pass.deposit.messaging.DepositServiceErrorHandler;
import org.dataconservancy.pass.deposit.messaging.config.quartz.QuartzConfig;
import org.dataconservancy.pass.deposit.messaging.config.spring.DepositConfig;
import org.dataconservancy.pass.deposit.messaging.config.spring.JmsConfig;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2Transport;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * This IT insures that the SWORD transport properly handles the Deposit.depositStatusRef field by updating the
 * Deposit.depositStatus field according to the SWORD state document.  It configures Deposit Services with an Assembler
 * that streams a pre-built package (the files actually submitted to Fedora in the Submission are ignored, and not
 * streamed to DSpace).  DSpace is the only concrete implementation of a SWORD server used by Deposit Services, so it is
 * employed here.
 *
 * Note this IT uses a specific runtime configuration for Deposit Services in the classpath resource
 * DepositTaskIT.json.  The status mapping indicates that by default the state of a Deposit will be SUBMITTED unless
 * the package is archived (SUCCESS) or withdrawn (REJECTED).  Now, if an exception occurs when performing the SWORD
 * deposit to DSpace (for example, if the package is corrupt), there will be no SWORD state to examine because the
 * package could not be ingested.  In the case of a corrupt package that is rejected without getting in the front door,
 * there will be no Deposit.depositStatusRef, and Deposit.depositStatus will be FAILED.
 *
 * Note that FAILED is an intermediate status.  This means that remedial action can be taken, and the package can be
 * re-submitted without creating a new Submission.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {"pass.deposit.repository.configuration=" +
                                  "classpath:org/dataconservancy/pass/deposit/messaging/status/DepositTaskIT.json"})
@Import({DepositConfig.class, JmsConfig.class, QuartzConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
// the repository configuration json pollutes the context
public class DepositTaskIT extends AbstractSubmissionFixture {

    /**
     * Package specification URI identifying a DSpace SIP with METS metadata
     */
    private static final String SPEC = "http://purl.org/net/sword/package/METSDSpaceSIP";

    /**
     * Pre-built package conforming to the DSpace METS SIP packaging: http://purl.org/net/sword/package/METSDSpaceSIP
     */
    private static final String PACKAGE_PATH = "/packages/example.zip";

    private static final String CHECKSUM_PATH = PACKAGE_PATH + ".md5";

    /**
     * Pre-built package missing a file specified in the METS.xml
     */
    private static final String MISSING_FILE_PACKAGE_PATH = "/packages/example-missing-file.zip";

    private static final String MISSING_FILE_CHECKSUM_PATH = MISSING_FILE_PACKAGE_PATH + ".md5";

    private final ArgumentCaptor<Throwable> throwableCaptor = ArgumentCaptor.forClass(Throwable.class);

    private Submission submission;

    @Autowired
    private PreassembledAssembler assembler;

    // N.B. the name of this bean and field are important, they must be named 'errorHandler' for the spy to be
    // properly injected into the Application Context, and into this test class.  Otherwise multiple beans are
    // registered in the Application Context, and one or the other (test class vs app ctx) doesn't get the Spy.
    @SpyBean(name = "errorHandler")
    private DepositServiceErrorHandler errorHandler;

    @SpyBean
    private DepositStatusProcessor depositStatusProcessor;

    @SpyBean
    private Sword2Transport sword2Transport;

    /**
     * Mocks up the {@link #assembler} so that it streams back a {@link #PACKAGE_PATH package} conforming to the
     * DSpace METS SIP profile.
     *
     * @throws Exception
     */
    @Before
    public void setUpSuccess() throws Exception {
        InputStream packageFile = this.getClass().getResourceAsStream(PACKAGE_PATH);
        PackageStream.Checksum checksum = mock(PackageStream.Checksum.class);
        when(checksum.algorithm()).thenReturn(PackageOptions.Checksum.OPTS.MD5);
        when(checksum.asHex()).thenReturn(IOUtils.resourceToString(CHECKSUM_PATH, StandardCharsets.UTF_8));

        assembler.setSpec(SPEC);
        assembler.setPackageStream(packageFile);
        assembler.setPackageName("example.zip");
        assembler.setChecksum(checksum);
        assembler.setPackageLength(33849);
        assembler.setCompression(PackageOptions.Compression.OPTS.ZIP);
        assembler.setArchive(PackageOptions.Archive.OPTS.ZIP);
    }

    /**
     * Streams a Submission graph from shared-resources.  Each object in the graph is persisted in Fedora by the
     * {@link AbstractSubmissionFixture} super class.  The Submission in Fedora will have the {@code submitted} flag
     * set to {@code false} so that Deposit Services won't act.
     *
     * Note that the 'repositoryKey' field on the Repository present in the graph has a value of 'pmc', used to
     * configure the repository in 'DepositTaskIT.json'.
     *
     * @return
     */
    @Before
    public void submit() {
        submission = findSubmission(createSubmission(lookupStream(URI.create("fake:submission2"))));
    }

    /**
     * A submission with a valid package should result in success.
     */
    @Test
    public void success() {
        // Configure a spy on the TransportSession returned by the Transport
        AtomicReference<TransportSession> transportSessionSpy = new AtomicReference<>();
        doAnswer(inv -> {
            transportSessionSpy.set((TransportSession) spy(inv.callRealMethod()));
            return transportSessionSpy.get();
        }).when(sword2Transport).open(any());

        // "Click" submit
        triggerSubmission(submission.getId());

        // Wait for the Deposit resource to show up as ACCEPTED (terminal state)
        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() != null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                Deposit.DepositStatus.ACCEPTED == deposits.iterator().next()
                                                                                          .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        Deposit deposit = deposits.iterator().next();

        // Insure a Deposit.depositStatusRef was set on the Deposit resource
        assertNotNull(deposit.getDepositStatusRef());

        // No exceptions should be handled by the error handler
        verifyZeroInteractions(errorHandler);

        // Insure the DepositStatusProcessor processed the Deposit.depositStatusRef
        ArgumentCaptor<Deposit> processedDepositCaptor = ArgumentCaptor.forClass(Deposit.class);
        verify(depositStatusProcessor).process(processedDepositCaptor.capture(), any());
        assertEquals(deposit.getId(), processedDepositCaptor.getValue().getId());

        // Insure the Transport and TransportSession were invoked
        ArgumentCaptor<PackageStream> packageStreamCaptor = ArgumentCaptor.forClass(PackageStream.class);
        verify(sword2Transport).open(any());
        verify(transportSessionSpy.get()).send(packageStreamCaptor.capture(), any());
        assertNotNull(packageStreamCaptor.getValue());
    }

    /**
     * A submission with an invalid checksum should result in failure, an intermediate status.  The exception should
     * carry a message to that effect.
     */
    @Test
    public void invalidChecksum() {
        PackageStream.Checksum checksum = mock(PackageStream.Checksum.class);
        when(checksum.algorithm()).thenReturn(PackageOptions.Checksum.OPTS.MD5);
        when(checksum.asHex()).thenReturn("invalid checksum");
        assembler.setChecksum(checksum);

        triggerSubmission(submission.getId());

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                Deposit.DepositStatus.FAILED == deposits.iterator().next()
                                                                                        .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage().contains("did not match the checksum"));
    }

    /**
     * A submission with an invalid package specification should result in failure, an intermediate status.  The
     * exception should carry a message to that effect.
     */
    @Test
    public void invalidSpec() {
        assembler.setSpec("invalid spec");
        triggerSubmission(submission.getId());

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                Deposit.DepositStatus.FAILED == deposits.iterator().next()
                                                                                        .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage().contains("Unacceptable packaging type"));
    }

    /**
     * A submission with an invalid length results in mismatched checksums.
     */
    @Test
    public void invalidLength() {
        assembler.setPackageLength(3);
        triggerSubmission(submission.getId());

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                Deposit.DepositStatus.FAILED == deposits.iterator().next()
                                                                                        .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage().contains("did not match the checksum"));
    }

    /**
     * If the METS.xml is missing a file, the submission fails as well.
     *
     * @throws IOException
     */
    @Test
    public void missingFile() throws IOException {
        InputStream packageFile = this.getClass().getResourceAsStream(MISSING_FILE_PACKAGE_PATH);
        PackageStream.Checksum checksum = mock(PackageStream.Checksum.class);
        when(checksum.algorithm()).thenReturn(PackageOptions.Checksum.OPTS.MD5);
        when(checksum.asHex()).thenReturn(IOUtils.resourceToString(MISSING_FILE_CHECKSUM_PATH, StandardCharsets.UTF_8));

        assembler.setPackageStream(packageFile);
        assembler.setPackageName("example-missing-file.zip");
        assembler.setChecksum(checksum);
        assembler.setPackageLength(23095);

        triggerSubmission(submission.getId());

        Condition<Set<Deposit>> c = depositsForSubmission(submission.getId(), 1, (deposit, repo) ->
            deposit.getDepositStatusRef() == null);
        assertTrue(c.awaitAndVerify(deposits -> deposits.size() == 1 &&
                                                Deposit.DepositStatus.FAILED == deposits.iterator().next()
                                                                                        .getDepositStatus()));
        Set<Deposit> deposits = c.getResult();
        assertNull(deposits.iterator().next().getDepositStatusRef());

        verify(errorHandler).handleError(throwableCaptor.capture());
        assertTrue(throwableCaptor.getValue().getCause().getMessage()
                                  .contains(
                                      "Manifest file references file &apos;pdf3.pdf&apos; not included in the zip."));
    }
}
