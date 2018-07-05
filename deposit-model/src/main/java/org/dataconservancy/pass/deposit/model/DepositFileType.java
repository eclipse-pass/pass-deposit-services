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

/**
 * The semantic type of a {@link DepositFile file}.
 */
public enum DepositFileType {

    /**
     * Metadata required by the submission process.
     */
    bulksub_meta_xml,

    /**
     * Manuscript file uploaded by an end-user.
     */
    manuscript,

    /**
     * Supplemental data uploaded by an end-user; not a {@link #manuscript}, {@link #table}, or {@link #figure}.
     */
    supplement,

    /**
     * Manuscript figure uploaded by an end-user.
     */
    figure,

    /**
     * Manuscript table uploaded by an end-user.
     */
    table

}
