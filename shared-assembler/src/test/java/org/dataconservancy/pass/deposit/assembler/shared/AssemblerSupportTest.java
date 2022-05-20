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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.CharSequenceInputStream;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.mime.MediaType;
import org.junit.Before;
import org.junit.Test;

public class AssemblerSupportTest {

    private final long sizeL = 1024L;

    private Detector detector;

    private MarkUnsupportedInputStream markUnsupportedIn;

    @Before
    public void setUp() throws Exception {
        detector = new DefaultDetector();
        markUnsupportedIn = new MarkUnsupportedInputStream(sizeL, false);
    }

    @Test
    public void testNullInputStreamBehavior() throws IOException {
        assertFalse(markUnsupportedIn.markSupported());
        assertEquals(0, markUnsupportedIn.getPosition());
        assertEquals(sizeL, markUnsupportedIn.getSize());

        markUnsupportedIn.read();

        assertEquals(1, markUnsupportedIn.getPosition());
    }

    /**
     * When mark(int) is not supported by the InputStream supplied to AssemblerSupport.detectMediaType(...), the
     * stream should not be read, and MediaType.OCTET_STREAM returned by default.
     *
     * I think there are some subtle bugs lurking, for a couple of reasons:
     * 1) InputStream.mark(int) is not required to throw an exception when markSupported() is false.  However, it
     * must be thrown by reset().  This allows for bytes to be read from the stream without being able to reset().
     * 2) We can protect against in AssemblerSupport.detectMediaType by refusing to invoke the Detector unless the
     * supplied stream supports mark(int).  The problem is that the Detector doesn't necessarily have to invoke
     * mark(int).  If AssemblerSupport ever provides Metadata to the Detector, the stream may not need to be read at
     * all, so whether or not mark(int) is supported may not be relevant.
     *
     * Currently AssemblerSupport.detectMediaType provides an empty Metadata, therefore the only code path available to
     * media type detection will require reading bytes from the stream.  Therefore, as long as
     * AssemblerSupport.detectMediaType provides an empty Metadata, mark(int) must be supported by the supplied stream.
     * Streams that don't support mark(int) won't go to the Detector.
     */
    @Test
    public void testResetStreamWhenMarkNotSupported() throws IOException {
        assertFalse(markUnsupportedIn.markSupported());
        Detector detector = mock(Detector.class);

        // supplied InputStream will not have been read b/c mark(int) is not supported.
        // MediaType.OCTET_STREAM is returned by default.

        assertEquals(MediaType.OCTET_STREAM, AssemblerSupport.detectMediaType(markUnsupportedIn, detector));
        verifyZeroInteractions(detector);
    }

    /**
     * When the supplied stream <em>does</em> {@link InputStream#markSupported() support mark(int)}, the supplied stream
     * <em>may</em> be read.  When the stream is read, the stream should be reset() after.
     *
     * @throws IOException
     */
    @Test
    public void testResetStreamWhenMarkSupported() throws IOException {
        Detector spy = spy(detector);
        CharSequence cs = "Hello, world!";
        CharSequenceInputStream markSupportedIn = new CharSequenceInputStream(cs, UTF_8);

        MediaType result = AssemblerSupport.detectMediaType(markSupportedIn, spy);

        assertEquals(MediaType.TEXT_PLAIN, result);

        // supplied should have been read(...) and reset() after.  We should be able to re-read the entire stream
        byte[] b = new byte[cs.length()];
        markSupportedIn.read(b, 0, cs.length());
        assertEquals(cs, new String(b));

        // The underlying detector should be invoked with the supplied InputStream and an *empty* Metadata
        // If the Metadata is not empty (i.e. AssemblerSupport#detectMediaType has been refactored), see the
        // javadoc on testResetStreamWhenMarkNotSupported
        verify(spy).detect(eq(markSupportedIn), argThat(arg -> arg.size() == 0));
    }
}