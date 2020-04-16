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

import com.google.gson.JsonObject;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;

/**
 * Allows for various components to contribute to the state of PackageStream.Metadata without the requirement to share
 * knowledge of the underlying {@link PackageStream.Metadata} implementation.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class MetadataBuilderImpl implements MetadataBuilder {

    private SimpleMetadataImpl metadata;

    @Override
    public MetadataBuilder name(String name) {
        checkState();
        metadata.setName(name);
        return this;
    }

    @Override
    public MetadataBuilder spec(String spec) {
        checkState();
        metadata.setSpec(spec);
        return this;
    }

    @Override
    public MetadataBuilder mimeType(String mimeType) {
        checkState();
        metadata.setMimeType(mimeType);
        return this;
    }

    @Override
    public MetadataBuilder sizeBytes(long sizeBytes) {
        checkState();
        metadata.setSizeBytes(sizeBytes);
        return this;
    }

    @Override
    public MetadataBuilder compressed(boolean compressed) {
        checkState();
        metadata.setCompressed(compressed);
        return this;
    }

    @Override
    public MetadataBuilder compression(Compression.OPTS compression) {
        checkState();
        metadata.setCompression(compression);
        return this;
    }

    @Override
    public MetadataBuilder archived(boolean archived) {
        checkState();
        metadata.setArchived(archived);
        return this;
    }

    @Override
    public MetadataBuilder archive(Archive.OPTS archive) {
        checkState();
        metadata.setArchive(archive);
        return this;
    }

    @Override
    public MetadataBuilder checksum(PackageStream.Checksum checksum) {
        checkState();
        metadata.addChecksum(checksum);
        return this;
    }

    @Override
    public MetadataBuilder submissionMeta(JsonObject meta) {
        checkState();
        metadata.setSubmissionMeta(meta);
        return this;
    }

    @Override
    public PackageStream.Metadata build() {
        checkState();
        return metadata;
    }

    private void checkState() {
        if (this.metadata == null) {
            this.metadata = new SimpleMetadataImpl();
        }
    }

    public void reset() {
        this.metadata = null;
    }
}
