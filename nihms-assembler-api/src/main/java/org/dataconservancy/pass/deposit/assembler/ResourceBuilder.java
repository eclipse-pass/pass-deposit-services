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
package org.dataconservancy.pass.deposit.assembler;

/**
 * Allows for various components to contribute to the state of {@link PackageStream.Resource}s without the requirement
 * to share knowledge of the underlying implementation.  Clients of this builder must call {@link #reset()} to clear
 * the internal state of this builder, allowing for a new {@link PackageStream.Resource}
 * to be built.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface ResourceBuilder {

    /**
     * Adds a {@link PackageStream.Resource#checksum()} of the {@code Resource}.  The first
     * checksum added will be considered the "primary" checksum, returned in response to {@link
     * PackageStream.Resource#checksum()}.
     *
     * @param checksum a checksum of the resource
     * @return this builder
     * @see PackageStream.Resource#checksum()
     */
    ResourceBuilder checksum(PackageStream.Checksum checksum);

    /**
     * Sets the {@link PackageStream.Resource#mimeType()} of the {@code Resource}.
     *
     * @param mimeType the mime type of the resource
     * @return this builder
     * @see PackageStream.Resource#mimeType()
     */
    ResourceBuilder mimeType(String mimeType);

    /**
     * Sets the {@link PackageStream.Resource#name()} of the {@code Resource}, suitable for use as a filename.
     *
     * @param name the resource name
     * @return this builder
     * @see PackageStream.Resource#name()
     */
    ResourceBuilder name(String name);

    /**
     * Sets the {@link PackageStream.Resource#sizeBytes()} of the {@code Resource}.
     *
     * @param sizeBytes the size of the resource, in bytes
     * @return this builder
     * @see PackageStream.Resource#sizeBytes()
     */
    ResourceBuilder sizeBytes(long sizeBytes);

    /**
     * Builds the Resource object from the state set on this builder.  This is an idempotent operation.  To clear the
     * internal state of this builder and create a new {@code Resource}, {@link #reset()} must be invoked.
     *
     * @return the Resource object
     */
    PackageStream.Resource build();

    /**
     * Reset the internal state of this builder, allowing it to be re-used for building a new Resource.
     */
    void reset();

}
