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
package org.dataconservancy.nihms.model;

import java.util.List;

/**
 * Encapsulates a submission to the target system, including the manuscript and supplemental files, metadata describing
 * the manuscript, authors, and the journal of publication, and a manifest cataloging every file in the submission.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositSubmission {

    /**
     * Internal, submission engine, identifier
     */
    private String id;

    /**
     * Manifest containing an entry for each file in the submission
     */
    private DepositManifest manifest;

    /**
     * Metadata describing the contents of the submission
     */
    private DepositMetadata metadata;

    /**
     * The files uploaded by the user, including the manuscript and supplemental files.
     */
    private List<DepositFile> files;

    /**
     * Short, human-readable, name of the submission.  Used to generate the file name for the package file.
     */
    private String name;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public DepositManifest getManifest() {
        return manifest;
    }

    public void setManifest(final DepositManifest manifest) {
        this.manifest = manifest;
    }

    public DepositMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(final DepositMetadata metadata) {
        this.metadata = metadata;
    }

    public List<DepositFile> getFiles() {
        return files;
    }

    public void setFiles(final List<DepositFile> files) {
        this.files = files;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DepositSubmission that = (DepositSubmission) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (manifest != null ? !manifest.equals(that.manifest) : that.manifest != null) return false;
        if (metadata != null ? !metadata.equals(that.metadata) : that.metadata != null) return false;
        if (files != null ? !files.equals(that.files) : that.files != null) return false;
        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (manifest != null ? manifest.hashCode() : 0);
        result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
        result = 31 * result + (files != null ? files.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DepositSubmission{" +
                "id='" + id + '\'' +
                ", manifest=" + manifest +
                ", metadata=" + metadata +
                ", files=" + files +
                ", name='" + name + '\'' +
                '}';
    }
}
