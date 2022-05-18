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

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.pass.deposit.assembler.shared.ArchivingPackageStream.STREAMING_IO_LOG;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-throws the {@code Throwable} set by {@link #setWriterEx(Throwable)} when any {@code public} or {@code protected}
 * method of {@link PipedInputStream} is invoked.
 * <p>
 * Upon invocation of any {@code public} or {@code protected} methods of {@code PipedInputStream}, the presence of a
 * {@link Throwable} (stored in a member {@code volatile} variable) is checked.  If a {@code Throwable} is present, it
 * indicates that the <em>writing</em> side of the pipe encountered an exception.  The writer is executing in a
 * separate thread, and cannot report exceptions "up the stack" to the caller.  Instead, the writer (via a {@link
 * Thread.UncaughtExceptionHandler}) sets any caught exceptions on the <em>reading</em> side of the pipe, and the
 * reading side of the pipe will re-throw them to readers if {@link #setWriterEx(Throwable)} is called with a non-{@code
 * null Throwable}.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class ExHandingPipedInputStream extends PipedInputStream {

    private static final Logger LOG = LoggerFactory.getLogger(ExHandingPipedInputStream.class);

    /**
     * If non-null, represents an exception that was thrown on the <em>writing</em> side of the pipe.  It should be
     * re-thrown to callers of {@link PipedInputStream} {@code public} or {@code protected} methods.
     */
    private volatile Throwable writerEx;

    public ExHandingPipedInputStream(int pipeSize) {
        super(pipeSize);
    }

    @Override
    public void connect(PipedOutputStream src) throws IOException {
        handleEx();
        super.connect(src);
    }

    @Override
    protected synchronized void receive(int b) throws IOException {
        handleEx();
        super.receive(b);
    }

    @Override
    public synchronized int read() throws IOException {
        handleEx();
        return super.read();
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        handleEx();
        return super.read(b, off, len);
    }

    @Override
    public synchronized int available() throws IOException {
        handleEx();
        return super.available();
    }

    @Override
    public void close() throws IOException {
        // Close the stream, regardless of whether or not there is an exception waiting for us
        STREAMING_IO_LOG.debug("{}@{} close() invoked: ", this.getClass().getSimpleName(),
                               toHexString(identityHashCode(this)), new Exception("close() invoked"));
        try {
            super.close();
        } finally {
            handleEx();
        }
    }

    /**
     * Obtain the {@code Throwable} that presumably occurred on the <em>writing</em> side of this pipe.  It will be re-
     * thrown as an {@link IOException} the next time a {@code public} or {@code protected} method of {@link
     * PipedInputStream} is invoked.
     *
     * @return a {@code Throwable} that occurred while writing to the pipe, or {@code null} if no exception has occurred
     */
    public Throwable getWriterEx() {
        return writerEx;
    }

    /**
     * Set the {@code Throwable} that presumably occurred on the <em>writing</em> side of this pipe.  It will be re-
     * thrown as an {@link IOException} the next time a {@code public} or {@code protected} method of {@link
     * PipedInputStream} is invoked.
     *
     * @param writerEx a {@code Throwable} that occurred while writing to the pipe
     */
    public void setWriterEx(Throwable writerEx) {
        this.writerEx = writerEx;
    }

    /**
     * Checks for a non-null {@link #writerEx}, and re-throws it as an {@link IOException}.
     *
     * @throws IOException the wrapped {@link #writerEx}
     */
    private void handleEx() throws IOException {
        if (writerEx == null) {
            return;
        }

        throw new IOException("The writing side of this PipedInputStream encountered an exception: " +
                              writerEx.getMessage(), writerEx);
    }
}
