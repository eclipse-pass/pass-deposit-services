/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.nihms.submission;

import org.apache.commons.io.input.NullInputStream;
import org.dataconservancy.nihms.assembler.Assembler;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.builder.InvalidModel;
import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.nihms.model.NihmsSubmission;
import org.dataconservancy.nihms.transport.Transport;
import org.dataconservancy.nihms.transport.TransportResponse;
import org.dataconservancy.nihms.transport.TransportSession;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SubmissionEngineTest {

    private SubmissionBuilder builder;

    private Assembler assembler;

    private Transport transport;

    private SubmissionEngine engine;

    @Before
    public void setUp() throws Exception {
        builder = mock(SubmissionBuilder.class);
        assembler = mock(Assembler.class);
        transport = mock(Transport.class);

        engine = new SubmissionEngine(builder, assembler, transport);
    }

    /**
     * Insure that:
     * <ol>
     *     <li>the form data url supplied to {@link SubmissionEngine#submit(String)} is used to build the model</li>
     *     <li>the name from the package stream metadata is used to name the destination resource</li>
     * </ol>
     *
     * @throws Exception
     */
    @Test
    public void testSubmitOk() throws Exception {
        String expectedFormDataUrl = "file:///tmp/submission.json";
        String expectedPackageName = "package.tar.gz";
        NullInputStream contentStream = new NullInputStream(2 ^ 20);
        NihmsSubmission submission = mock(NihmsSubmission.class);
        TransportSession session = mock(TransportSession.class);
        PackageStream packageStream = mock(PackageStream.class);
        PackageStream.Metadata md = mock(PackageStream.Metadata.class);
        TransportResponse response = mock(TransportResponse.class);

        when(builder.build(anyString())).thenReturn(submission);
        when(transport.open(anyMap())).thenReturn(session);
        when(assembler.assemble(any(NihmsSubmission.class))).thenReturn(packageStream);
        when(packageStream.metadata()).thenReturn(md);
        when(md.name()).thenReturn(expectedPackageName);
        when(packageStream.open()).thenReturn(contentStream);
        when(session.send(any(PackageStream.class), anyMap())).thenReturn(response);
        when(response.success()).thenReturn(true);

        engine.submit(expectedFormDataUrl);

        verify(builder).build(eq(expectedFormDataUrl));
        verify(session).send(eq(packageStream), anyMap());
        verify(response).success();
    }

    /**
     * Insure that when the submission builder throws an exception, a {@link SubmissionFailure} is thrown, properly chained.
     *
     * @throws Exception
     */
    @Test
    public void testModelBuilderCheckedExceptionFailure() throws Exception {
        IOException expectedCause = new IOException("Error reading form data url.");
        InvalidModel expectedException = new InvalidModel("IOE reading form data url.", expectedCause);

        when(builder.build(anyString())).thenThrow(expectedException);

        submitAndVerifyExceptionChain(expectedCause, expectedException);
    }

    /**
     * Insure that when the submission builder throws an exception, a {@link SubmissionFailure} is thrown, properly chained.
     *
     * @throws Exception
     */
    @Test
    public void testModelBuilderRuntimeExceptionFailure() throws Exception {
        RuntimeException expectedCause = new RuntimeException("A runtime exception.");
        InvalidModel expectedException = new InvalidModel("RTE caused this.", expectedCause);

        when(builder.build(anyString())).thenThrow(expectedException);

        submitAndVerifyExceptionChain(expectedCause, expectedException);
    }

    /**
     * Insure that when the assembler throws an exception, a {@link SubmissionFailure} is thrown, properly chained, and that the transport session is closed.
     *
     * @throws Exception
     */
    @Test
    public void testAssemblerRuntimeExceptionFailure() throws Exception {
        IOException expectedCause = new IOException("Error assembling package.");
        RuntimeException expectedException = new RuntimeException("IOE assembling package", expectedCause);

        TransportSession session = mock(TransportSession.class);
        when(builder.build(anyString())).thenReturn(mock(NihmsSubmission.class));
        when(transport.open(anyMap())).thenReturn(session);
        when(assembler.assemble(any(NihmsSubmission.class))).thenThrow(expectedException);

        submitAndVerifyExceptionChain(expectedCause, expectedException);

        verify(session).close();
        verify(builder).build(anyString());
        verify(transport).open(anyMap());
        verify(assembler).assemble(any(NihmsSubmission.class));
    }

    private void submitAndVerifyExceptionChain(Exception expectedCause, Exception expectedException) {
        try {
            engine.submit("any url");
            fail("Submission failure expected.");
        } catch (SubmissionFailure submissionFailure) {
            assertEquals(expectedException, submissionFailure.getCause());
            assertEquals(expectedCause, submissionFailure.getCause().getCause());
        }
    }
}