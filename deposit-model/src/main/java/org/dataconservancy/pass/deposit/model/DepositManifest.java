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

/**
 * Accounts for every file in a submission.  Each entry in this manifest includes:
 * <ol>
 *     <li>the {@link DepositFile#name name} of the file</li>
 *     <li>the {@link DepositFileType semantic type} of the file</li>
 *     <li>a label for the file, <em>required</em> for {@link DepositFileType#figure figures},
 *     {@link DepositFileType#table tables}, and {@link DepositFileType#supplement supplements}</li>
 * </ol>
 */
public class DepositManifest {

    /**
     * List of files in this manifest
     */
    private List<DepositFile> files;

    public List<DepositFile> getFiles() {
        return files;
    }

    public void setFiles(List<DepositFile> files) {
        this.files = files;
    }
}