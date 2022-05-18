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

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static org.dataconservancy.pass.deposit.assembler.shared.ArchivingPackageStream.STREAMING_IO_LOG;

import java.io.File;
import java.io.IOException;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DebuggingArchiveOutputStream extends ArchiveOutputStream {

    private ArchiveOutputStream delegate;

    public DebuggingArchiveOutputStream(ArchiveOutputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public void putArchiveEntry(ArchiveEntry entry) throws IOException {
        STREAMING_IO_LOG.debug("{}@{} putting entry: '{}', {} bytes (directory: {})",
                               this.getClass().getSimpleName(), toHexString(identityHashCode(this)),
                               entry.getName(), entry.getSize(), entry.isDirectory());
        delegate.putArchiveEntry(entry);
    }

    @Override
    public void closeArchiveEntry() throws IOException {
        STREAMING_IO_LOG.debug("{}@{} closing entry", this.getClass().getSimpleName(),
                               toHexString(identityHashCode(this)));
        delegate.closeArchiveEntry();
    }

    @Override
    public void finish() throws IOException {
        STREAMING_IO_LOG.debug("{}@{} finish() invoked: ",
                               this.getClass().getSimpleName(), toHexString(identityHashCode(this)),
                               new Exception("finish() invoked"));
        delegate.finish();
    }

    @Override
    public ArchiveEntry createArchiveEntry(File inputFile, String entryName) throws IOException {
        STREAMING_IO_LOG.debug("{}@{} creating entry: '{}' from '{}'", this.getClass().getSimpleName(),
                               toHexString(identityHashCode(this)), entryName, inputFile);
        return delegate.createArchiveEntry(inputFile, entryName);
    }

    @Override
    public void close() throws IOException {
        STREAMING_IO_LOG.debug("{}@{} close() invoked: ", this.getClass().getSimpleName(),
                               toHexString(identityHashCode(this)), new Exception("close() invoked"));
        delegate.close();
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

//    @Override
//    protected void count(int written) {
//        delegate.count(written);
//    }
//
//    @Override
//    protected void count(long written) {
//        delegate.count(written);
//    }

    @Override
    public int getCount() {
        return delegate.getCount();
    }

    @Override
    public long getBytesWritten() {
        return delegate.getBytesWritten();
    }

    @Override
    public boolean canWriteEntryData(ArchiveEntry archiveEntry) {
        return delegate.canWriteEntryData(archiveEntry);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }
}
