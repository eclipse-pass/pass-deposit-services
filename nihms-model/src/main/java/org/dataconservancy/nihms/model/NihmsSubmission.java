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
 * Encapsulates a submission to the NIHMS system, including the manuscript and supplemental files, metadata describing
 * the manuscript, authors, and the journal of publication, and a manifest cataloging every file in the submission.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsSubmission {

    /**
     * Internal, submission engine, identifier
     */
    private String id;

    /**
     * Manifest containing an entry for each file in the submission
     */
    private NihmsManifest manifest;

    /**
     * Metadata describing the contents of the submission
     */
    private NihmsMetadata metadata;

    /**
     * The files uploaded by the user, including the manuscript and supplemental files.
     */
    private List<NihmsFile> files;

}
