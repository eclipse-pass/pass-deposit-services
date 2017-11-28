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

/**
 * Represents a file that was uploaded by a user into PASS; e.g. a manuscript or supplemental material for a specific
 * submission.
 */
public class NihmsFile {

    /**
     * The type of file
     */
    private NihmsFileType type;

    /**
     * The name of the file in the archive
     */
    private String name;

    /**
     * Differentiates between files of the same type
     * <p>
     * Required field for {@link NihmsFileType#figure}, {@link NihmsFileType#table}, and {@link NihmsFileType#supplement} file types
     * </p>
     */
    private String label;

    /**
     * The location of the bytes for the file
     */
    private String location;

    public NihmsFileType getType() {
        return type;
    }

    public void setType(NihmsFileType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NihmsFile nihmsFile = (NihmsFile) o;

        if (type != nihmsFile.type) return false;
        if (name != null ? !name.equals(nihmsFile.name) : nihmsFile.name != null) return false;
        if (label != null ? !label.equals(nihmsFile.label) : nihmsFile.label != null) return false;
        return location != null ? location.equals(nihmsFile.location) : nihmsFile.location == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (label != null ? label.hashCode() : 0);
        result = 31 * result + (location != null ? location.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NihmsFile{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", label='" + label + '\'' +
                ", location='" + location + '\'' +
                '}';
    }

}
