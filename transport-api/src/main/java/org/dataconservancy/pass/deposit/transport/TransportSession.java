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

package org.dataconservancy.pass.deposit.transport;

import java.util.Map;

import org.dataconservancy.pass.deposit.assembler.PackageStream;

/**
 * Represents an open connection, or the promise of a successful connection, with a service or system that will accept
 * the bytes of a package.  Instances of {@code TransportSession} that are immediately returned from {@link
 * Transport#open(Map)} ought to be open (that is, {@link #closed()} should return {@code false}).  If the underlying
 * implementation allows a {@code TransportSession} to be re-used (i.e. to {@code send(...)} multiple files), {@code
 * closed()} can be used to check the health of the underlying connection, and whether or not this {@code
 * TransportSession} is still viable.
 * <p>
 * This interface extends {@code AutoCloseable} so it can be used in a {@code try-with-resources} block.
 * </p>
 */
public interface TransportSession extends AutoCloseable {

    /**
     * Transfer the bytes of the supplied package to the remote system.  Metadata can be optionally supplied which may
     * help the underlying transport correctly configure itself for the transfer.
     * <p>
     * Note the {@code PackageStream} carries metadata about the package itself.  However, the supplied {@code metadata}
     * could be used to <em>augment</em> the {@code PackageStream} metadata, in addition to carrying transport-related
     * metadata.
     * </p>
     *
     * @param packageStream the package and package metadata
     * @param metadata      transport-related metadata, or any "extra" package metadata
     * @return a response indicating success or failure of the transfer
     */
    TransportResponse send(PackageStream packageStream, Map<String, String> metadata);

    boolean closed();

}
