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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.springframework.core.io.Resource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class CallableStreamWriter<V> implements Callable<V>, StreamWriter {

    private static final String STATE_EXCEPTION = "%s@%s has already been %s, perhaps by another thread.";

    private StreamWriter delegate;

    private ArchiveOutputStream archiveOut;

    private List<DepositFileResource> custodialFiles;

    private AtomicBoolean alreadyStarted = new AtomicBoolean(false);

    private AtomicBoolean alreadyFinished = new AtomicBoolean(false);

    private AtomicBoolean alreadyClosed = new AtomicBoolean(false);

    /**
     * Constructs an {@code CallableStreamWriter} that is supplied with the output stream being written to, the
     * custodial content being packaged, the submission, and other supporting classes.
     *
     * @param delegate
     * @param archiveOut
     * @param custodialFiles
     */
    CallableStreamWriter(StreamWriter delegate,
                         ArchiveOutputStream archiveOut,
                         List<DepositFileResource> custodialFiles) {
        this.delegate = delegate;
        this.archiveOut = archiveOut;
        this.custodialFiles = custodialFiles;
    }

    @Override
    public V call() throws Exception {
        if (alreadyStarted.getAndSet(true) == Boolean.FALSE) {
            delegate.start(custodialFiles, archiveOut);
        } else {
            throw stateException("started");
        }
        return null;
    }

    @Override
    public void start(List<DepositFileResource> custodialFiles, ArchiveOutputStream archiveOut) throws IOException {
        if (alreadyStarted.getAndSet(true) == Boolean.FALSE) {
            delegate.start(custodialFiles, archiveOut);
        } else {
            throw stateException("started");
        }
    }

    @Override
    public PackageStream.Resource writeResource(ResourceBuilder builder, Resource custodialFile)
        throws IOException {
        return delegate.writeResource(builder, custodialFile);
    }

    @Override
    public void finish(DepositSubmission submission, List<PackageStream.Resource> custodialResources)
        throws IOException {
        if (alreadyFinished.getAndSet(true) == Boolean.FALSE) {
            delegate.finish(submission, custodialResources);
        } else {
            throw stateException("finished");
        }
    }

    @Override
    public void close() throws Exception {
        if (alreadyClosed.getAndSet(true) == Boolean.FALSE) {
            delegate.close();
        } else {
            throw stateException("closed");
        }
    }

    private IllegalStateException stateException(String stateViolated) {
        return new IllegalStateException(String.format(STATE_EXCEPTION,
                                                       this.getClass().getName(), toHexString(identityHashCode(this)),
                                                       stateViolated));
    }

}
