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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.dataconservancy.pass.deposit.assembler.PackageStream;

/**
 * Default implementation of a Package resource.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class ResourceImpl implements PackageStream.Resource {

    private long sizeBytes;

    private String mimeType;

    private String name;

    private List<PackageStream.Checksum> checksums = new ArrayList<>(1);

    @Override
    public long sizeBytes() {
        return sizeBytes;
    }

    @Override
    public String mimeType() {
        return mimeType;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public PackageStream.Checksum checksum() {
        if (checksums != null && checksums.size() > 0) {
            return checksums.get(0);
        }

        return null;
    }

    @Override
    public Collection<PackageStream.Checksum> checksums() {
        return checksums;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PackageStream.Checksum> getChecksums() {
        return checksums;
    }

    public void setChecksums(List<PackageStream.Checksum> checksums) {
        this.checksums = checksums;
    }

    public void addChecksum(PackageStream.Checksum checksum) {
        this.checksums.add(checksum);
    }

    @Override
    public String toString() {
        return "ResourceImpl{" + "sizeBytes=" + sizeBytes + ", mimeType='" + mimeType + '\'' + ", name='" + name +
               '\'' + ", checksums=" + checksums + '}';
    }
}
