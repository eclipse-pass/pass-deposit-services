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
package org.dataconservancy.pass.deposit.model;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import com.google.gson.JsonObject;
import org.joda.time.DateTime;

/**
 * Encapsulates a submission to the target system, including the manuscript and supplement files, metadata describing
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
     * Date the Submission resource was submitted to the PASS repository.
     *
     * Set by Ember when the user clicks the Submit button
     */
    private DateTime submissionDate;

    /**
     * Manifest containing an entry for each file in the submission
     */
    private DepositManifest manifest;

    /**
     * Metadata describing the contents of the submission
     */
    private DepositMetadata metadata;

    /**
     * The files uploaded by the user, including the manuscript and supplement files.
     */
    private List<DepositFile> files;

    /**
     * Short, human-readable, name of the submission.  Used to generate the file name for the package file.
     */
    private String name;

    /**
     * The PASS Submission.metadata serialized as a JsonObject
     */
    private JsonObject submissionMeta;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DepositManifest getManifest() {
        return manifest;
    }

    public void setManifest(DepositManifest manifest) {
        this.manifest = manifest;
    }

    public DepositMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(DepositMetadata metadata) {
        this.metadata = metadata;
    }

    public List<DepositFile> getFiles() {
        return files;
    }

    public void setFiles(List<DepositFile> files) {
        this.files = files;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Date the Submission resource was submitted to the PASS repository.
     *
     * Set by Ember when the user clicks the Submit button
     */
    public DateTime getSubmissionDate() {
        return submissionDate;
    }

    /**
     * Date the Submission resource was submitted to the PASS repository.
     *
     * Set by Ember when the user clicks the Submit button
     */
    public void setSubmissionDate(DateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public JsonObject getSubmissionMeta() {
        return submissionMeta;
    }

    public void setSubmissionMeta(JsonObject submissionMeta) {
        this.submissionMeta = submissionMeta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DepositSubmission that = (DepositSubmission) o;
        return Objects.equals(id, that.id) && Objects.equals(submissionDate, that.submissionDate)
               && Objects.equals(manifest, that.manifest) && Objects.equals(metadata, that.metadata)
               && Objects.equals(files, that.files) && Objects.equals(name, that.name)
               && Objects.equals(submissionMeta, that.submissionMeta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, submissionDate, manifest, metadata, files, name, submissionMeta);
    }

    @Override
    public String toString() {
        return new StringJoiner("\n  ", DepositSubmission.class.getSimpleName()
            + "[", "]").add("id='" + id + "'").add("submissionDate="
            + submissionDate).add("manifest=" + manifest).add("metadata=" + metadata)
            .add("files=" + files)
            .add("name='" + name + "'")
            .add("submissionMeta=" + submissionMeta).toString();
    }
}