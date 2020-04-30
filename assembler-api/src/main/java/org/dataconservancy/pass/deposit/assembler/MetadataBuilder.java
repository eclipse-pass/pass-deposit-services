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

import com.google.gson.JsonObject;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;

/**
 * Allows for various components to contribute to the state of {@link PackageStream.Metadata} without the requirement to
 * share knowledge of the underlying implementation.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface MetadataBuilder {

    /**
     * Sets the {@link PackageStream.Metadata#name()} of the {@code PackageStream}, suitable for use as a filename.
     *
     * @param name the package name
     * @return this builder
     * @see PackageStream.Metadata#name()
     */
    MetadataBuilder name(String name);

    /**
     * Sets the {@link PackageStream.Metadata#spec()} of the {@code PackageStream}.
     *
     * @param spec the package specification
     * @return this builder
     * @see PackageStream.Metadata#spec()
     */
    MetadataBuilder spec(String spec);

    /**
     * Sets the {@link PackageStream.Metadata#mimeType()} of the {@code PackageStream}, as returned by
     * {@link PackageStream#open()}
     *
     * @param mimeType the package mime type
     * @return this builder
     * @see PackageStream.Metadata#mimeType()
     */
    MetadataBuilder mimeType(String mimeType);

    /**
     * Sets the {@link PackageStream.Metadata#sizeBytes()} of the {@code PackageStream}, as returned by
     * {@link PackageStream#open()}
     *
     * @param sizeBytes the size of the package, in bytes
     * @return this builder
     * @see PackageStream.Metadata#sizeBytes()
     */
    MetadataBuilder sizeBytes(long sizeBytes);

    /**
     * Sets the {@link PackageStream.Metadata#compressed()} flag of the {@code PackageStream}.
     *
     * @param compressed flag indicating if the package is compressed
     * @return this builder
     * @see PackageStream.Metadata#compressed()
     */
    MetadataBuilder compressed(boolean compressed);

    /**
     * Sets the {@link PackageStream.Metadata#compression()} used by the {@code PackageStream}, as returned by
     * {@link PackageStream#open()}.
     *
     * @param compression the compression algorithm
     * @return this builder
     * @see PackageStream.Metadata#compression()
     */
    MetadataBuilder compression(Compression.OPTS compression);

    /**
     * Sets the {@link PackageStream.Metadata#archived()} flag of the {@code PackageStream}.
     *
     * @param archived flag indicating if the package is an archive
     * @return this builder
     * @see PackageStream.Metadata#archived()
     */
    MetadataBuilder archived(boolean archived);

    /**
     * Sets the {@link PackageStream.Metadata#archive()} format used by the {@code PackageStream}, as returned by
     * {@link PackageStream#open()}.
     *
     * @param archive the archival format
     * @return this builder
     * @see PackageStream.Metadata#archive()
     */
    MetadataBuilder archive(Archive.OPTS archive);

    /**
     * Adds a {@link PackageStream.Metadata#checksum()} of the {@code PackageStream}.  The first
     * checksum added will be considered the "primary" checksum, returned in response to {@link
     * PackageStream.Metadata#checksum()}.
     *
     * @param checksum a checksum of the package
     * @return this builder
     * @see PackageStream.Metadata#checksum()
     */
    MetadataBuilder checksum(PackageStream.Checksum checksum);

    /**
     * Adds the metadata associated with the PASS Submission resource to the PackageStream.Metadata
     *
     * @param meta the {@code Submission.metadata} serialized as a map
     * @return this builder
     * @see PackageStream.Metadata#submissionMeta()
     */
    MetadataBuilder submissionMeta(JsonObject meta);

    /**
     * Builds the Metadata object from the state set on this builder.
     *
     * @return the Metadata object
     */
    PackageStream.Metadata build();
}
