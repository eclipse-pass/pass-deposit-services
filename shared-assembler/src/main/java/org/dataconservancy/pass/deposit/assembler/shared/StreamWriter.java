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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Provides methods for writing a stream of bytes representing a package.  The caller has collected the content to be
 * included in the package as a {@code List} of {@link DepositFileResource}s.  Implementations of this interface are
 * responsible for mapping each {@code DepositFileResource} as a {@link PackageStream.Resource}
 * <p>
 * Callers will invoke {@link #start(List, ArchiveOutputStream) start(...)} to initialize any state
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface StreamWriter extends AutoCloseable {

    void start(List<DepositFileResource> custodialFiles, ArchiveOutputStream archiveOut) throws IOException;

    PackageStream.Resource buildResource(ResourceBuilder builder, Resource custodialFile) throws IOException;

    void writeResource(ArchiveOutputStream archiveOut, ArchiveEntry archiveEntry, InputStream archiveEntryIn) throws IOException;

    void finish(DepositSubmission submission, List<PackageStream.Resource> custodialResources) throws IOException;

}
