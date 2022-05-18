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

import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;

import com.google.gson.JsonObject;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.model.DepositSubmission;

/**
 * A streamable serialized form of a submission package.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface PackageStream {

    /**
     * Opens the package in its entirety, and streams back the bytes as specified by the archive and compression
     * settings used when creating the package.
     *
     * @return the stream for reading the entire package
     */
    InputStream open();

    /**
     * Opens the named resource, and streams back the bytes of the resource.  Implementations are able to retrieve a
     * resource from within a package (e.g. a file from within a ZIP archive).  To open the package as a whole, use
     * {@link #open()}.
     *
     * @param packageResource the identifier for a resource within the package
     * @return the stream for reading a singular resource within the package
     */
    InputStream open(String packageResource);

    /**
     * Returns an iterator over the resources in the package.
     *
     * @return an Iterator over the resources in the package
     */
    Iterator<Resource> resources();

    /**
     * Returns metadata describing this {@code PackageStream}
     *
     * @return package metadata
     */
    Metadata metadata();

    /**
     * Metadata describing the package.
     */
    interface Metadata {

        /**
         * A suggested name for this package.  The {@link #spec() specification} used for
         * {@link Assembler#assemble(DepositSubmission, java.util.Map) assembling} a package may place requirements
         * on the name of the
         * package file in the target system.  For example, BagIt recommends that the name of the package file be based
         * on the name of the base directory of the bag.  Submission components responsible for streaming {@link
         * PackageStream this package} to target systems can use the name returned by this method as the name of the
         * packaged file.
         *
         * @return a suggested name for the package, aligning with any recommendations from the
         * {@link #spec() packaging specification}
         */
        String name();

        /**
         * The specification adhered to by the package serialization returned by {@link #open()}.  Examples include
         * BagIt, NIHMS native, BOREM, etc.
         *
         * @return the identifier for the specification
         */
        String spec();

        /**
         * The mime type of the package serialization returned by {@link #open()}.
         *
         * @return the mime type of the package
         */
        String mimeType();

        /**
         * Total size of the package, in bytes.  Equivalent to the length of the stream returned by {@link #open()}.
         * Influenced by serialization format, compression.
         *
         * @return total size of the package in bytes, -1 if unknown
         */
        long sizeBytes();

        /**
         * If the package stream returned by {@link #open()} is compressed.  If {@code false}, then {@link
         * #compression()} should return {@link Compression.OPTS#NONE}.
         *
         * @return true if the package stream returned by {@link #open()} is compressed
         */
        boolean compressed();

        /**
         * The compression algorithm, if {@link #compression()} is used.
         *
         * @return the compression used
         */
        Compression.OPTS compression();

        /**
         * If the package uses to an archive format, such as tar.  If {@code false}, then {@link #archive()} should
         * return {@link Archive.OPTS#NONE}.
         *
         * @return true if the package stream returned by {@link #open()} is using a supported archival format
         */
        boolean archived();

        /**
         * The archive form, if the package is {@link #archived()}.
         *
         * @return the archive form
         */
        Archive.OPTS archive();

        /**
         * The primary or preferred checksum of the package serialization as returned by {@link #open()}
         *
         * @return the preferred checksum
         */
        Checksum checksum();

        /**
         * All available checksums of the package serialization as returned by {@link #open()}.  The primary or
         * preferred checksum will be at the head of the list.
         *
         * @return all available checksums
         */
        Collection<Checksum> checksums();

        /**
         * The {@code Submission.metadata} for this deposit, serialized as a Map.
         *
         * @return the Submission metadata blob
         */
        JsonObject submissionMeta();

    }

    /**
     * Metadata describing a resource inside of a package.
     */
    interface Resource {

        /**
         * The size of the {@link #name() named} resource, uncompressed.
         *
         * @return the size of the resource in bytes, -1 if unknown
         */
        long sizeBytes();

        /**
         * The mime type of the {@link #name() named} resource
         *
         * @return the mime type
         */
        String mimeType();

        /**
         * The unambiguous name of the resource within the package.
         *
         * @return the resource name
         */
        String name();

        /**
         * The primary or preferred checksum of the resource
         *
         * @return the preferred checksum
         */
        Checksum checksum();

        /**
         * All available checksums of the resource
         *
         * @return all available checksums
         */
        Collection<Checksum> checksums();

    }

    /**
     * Represents a checksum: the algorithm used to compute the checksum, and its value.
     */
    interface Checksum {

        /**
         * Algorithm used to compute the checksum
         *
         * @return the checksum algorithm
         */
        PackageOptions.Checksum.OPTS algorithm();

        /**
         * The value of the checksum, as a byte array
         *
         * @return the checksum value, according to the {@link #algorithm()}
         */
        byte[] value();

        /**
         * The value of the checksum, encoded as base64
         *
         * @return the checksum value, according to the {@link #algorithm()}, encoded as base 64
         */
        String asBase64();

        /**
         * The value of the checksum, encoded as hexadecimal
         *
         * @return the checksum value, according to the {@link #algorithm()}, encoded as hexadecimal
         */
        String asHex();

    }

}
