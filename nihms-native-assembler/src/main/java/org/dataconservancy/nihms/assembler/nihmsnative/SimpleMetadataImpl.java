/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.nihms.assembler.nihmsnative;

import org.dataconservancy.nihms.assembler.PackageStream;

/**
 * Provides metadata for a {@link PackageStream}.  Includes package-private accessors in addition to
 * read-only {@code Metadata} interface methods.  All fields have reasonable defaults except for {@link #name}.  To set
 * the suggested file name and/or stream length consider using the {@link #SimpleMetadataImpl(String) convenience}
 * {@link #SimpleMetadataImpl(String, long) constructors}.
 */
class SimpleMetadataImpl implements PackageStream.Metadata {

    private String name;

    private long sizeBytes = -1;

    private String spec = "nihms-native";

    private String mimeType = "application/gzip";
    
    private boolean compressed = true;

    private PackageStream.COMPRESSION compression = PackageStream.COMPRESSION.GZIP;

    private boolean archived = true;

    private PackageStream.ARCHIVE archive = PackageStream.ARCHIVE.TAR;

    SimpleMetadataImpl() {

    }

    SimpleMetadataImpl(String name) {
        this.name = name;
    }

    SimpleMetadataImpl(String name, long sizeBytes) {
        this.name = name;
        this.sizeBytes = sizeBytes;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String spec() {
        return spec;
    }

    @Override
    public String mimeType() {
        return mimeType;
    }

    @Override
    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public boolean compressed() {
        return compressed;
    }

    @Override
    public PackageStream.COMPRESSION compression() {
        return compression;
    }

    @Override
    public boolean archived() {
        return archived;
    }

    @Override
    public PackageStream.ARCHIVE archive() {
        return archive;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    String getSpec() {
        return spec;
    }

    void setSpec(String spec) {
        this.spec = spec;
    }

    String getMimeType() {
        return mimeType;
    }

    void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    boolean isCompressed() {
        return compressed;
    }

    void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    PackageStream.COMPRESSION getCompression() {
        return compression;
    }

    void setCompression(PackageStream.COMPRESSION compression) {
        this.compression = compression;
    }

    boolean isArchived() {
        return archived;
    }

    void setArchived(boolean archived) {
        this.archived = archived;
    }

    PackageStream.ARCHIVE getArchive() {
        return archive;
    }

    void setArchive(PackageStream.ARCHIVE archive) {
        this.archive = archive;
    }
}
