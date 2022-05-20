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

import java.io.IOException;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.springframework.core.io.Resource;

/**
 * Provides methods for writing a stream of bytes representing a package.  The caller has collected the content to be
 * included in the package as a {@code List} of {@link DepositFileResource}s.  Implementations of this interface are
 * responsible for mapping each {@code DepositFileResource} as a {@link PackageStream.Resource}
 * <p>
 * Callers will invoke {@link #start(List, ArchiveOutputStream) start(...)} to initialize any state
 * </p>
 * <p>
 * Note that this interface should be considered private, and is not very well thought out, especially its use by
 * {@link ArchivingPackageStream}.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
interface StreamWriter extends AutoCloseable {

    /**
     * Lifecycle method which initializes any necessary state for writing to the supplied {@code ArchiveOutputStream}.
     *
     * @param custodialFiles the custodial content of the package to be written
     * @param archiveOut     the {@code OutputStream} to be written to
     * @throws IOException if there are any errors encountered initializing state
     */
    void start(List<DepositFileResource> custodialFiles, ArchiveOutputStream archiveOut) throws IOException;

    /**
     * Writes the {@code Resource} and returns metadata describing the {@code custodialFile}.
     * <p>
     * Note that in a streaming implementation many attributes of {@link PackageStream.Resource} (e.g. checksum,
     * mime type) may not be known <em>until the {@code custodialFile} is written</em>.
     * </p>
     *
     * @param builder       interface used by implementations to build the {@link PackageStream.Resource}
     * @param custodialFile custodial content to be written to the stream
     * @return metadata describing the {@code custodialFile}
     * @throws IOException if there are any errors building or writing the {@code PackageStream.Resource}
     */
    PackageStream.Resource writeResource(ResourceBuilder builder, Resource custodialFile) throws IOException;

    /**
     * Lifecycle method which releases or cleans up any resources after writing all resources.
     *
     * @param submission         the original submission
     * @param custodialResources the resources written to the stream
     * @throws IOException if there are any errors cleaning up state
     */
    void finish(DepositSubmission submission, List<PackageStream.Resource> custodialResources) throws IOException;

}
