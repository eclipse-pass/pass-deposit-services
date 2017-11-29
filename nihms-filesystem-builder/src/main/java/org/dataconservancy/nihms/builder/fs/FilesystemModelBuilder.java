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

package org.dataconservancy.nihms.builder.fs;

import org.dataconservancy.nihms.builder.InvalidModel;
import org.dataconservancy.nihms.builder.NihmsBuilderPropertyNames;
import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.nihms.model.NihmsFile;
import org.dataconservancy.nihms.model.NihmsMetadata;
import org.dataconservancy.nihms.model.NihmsSubmission;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Builds a NIHMS submission from a file on a locally mounted filesystem.  The file contains key-value pairs that would
 * loosely mimic the form data provided by the view layer.
 *
 * This implementation is for a bare-bones demo only - in particular we can have just one Person, and just one File
 */
public class FilesystemModelBuilder implements SubmissionBuilder {

    @Override
    public NihmsSubmission build(String formDataUrl) throws InvalidModel {
        Properties properties = new Properties();
        InputStream is = null;
        NihmsSubmission submission = new NihmsSubmission();
        NihmsFile file = new NihmsFile();
        NihmsMetadata metadata = new NihmsMetadata();
        NihmsMetadata.Person person = new NihmsMetadata.Person();

        try {
            is = getClass().getClassLoader().getResourceAsStream(formDataUrl);
            if (is == null) {
                System.out.println("Sorry, unable to find submission properties file " + formDataUrl);
                return null;
            }

            properties.load(is);

            Enumeration<?> e = properties.propertyNames();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                String value = properties.getProperty(key);

                switch (key) {
                    //the id for the submission
                    case NihmsBuilderPropertyNames.NIHMS_SUBMISSION_ID:
                        submission.setId(value);
                        break;

                    //file properties
                    case NihmsBuilderPropertyNames.NIHMS_FILE_NAME:
                        file.setName(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_FILE_LABEL:
                        file.setLabel(value);
                        break;

                    //journal metadata
                    case NihmsBuilderPropertyNames.NIHMS_JOURNAL_ID:
                        metadata.getJournalMetadata().setJournalId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_JOURNAL_ISSN:
                        metadata.getJournalMetadata().setIssn(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_JOURNAL_TITLE:
                        metadata.getJournalMetadata().setJournalTitle(value);
                        break;

                    //manuscript metadata
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_ID:
                        metadata.getManuscriptMetadata().setNihmsId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PBMEDID:
                        metadata.getManuscriptMetadata().setPubmedId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PUBMEDCENTRALID:
                        metadata.getManuscriptMetadata().setPubmedCentralId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_URL:
                        metadata.getManuscriptMetadata().setManuscriptUrl(new URL(value));
                        break;

                    //person metadata
                    case NihmsBuilderPropertyNames.NIHMS_PERSON_AUTHOR:
                        person.setAuthor(Boolean.parseBoolean(value));
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_PERSON_CORRESPONDINGPI:
                        person.setCorrespondingPi(Boolean.parseBoolean(value));
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_PERSON_EMAIL:
                        person.setEmail(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_PERSON_FIRSTNAME:
                        person.setFirstName(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_PERSON_MIDDLENAME:
                        person.setMiddleName(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_PERSON_LASTNAME:
                        person.setLastName(value);
                        break;
                }
            }

            //now populate the submission
            submission.getFiles().add(file);
            metadata.getPersons().add(person);
            submission.setMetadata(metadata);
            submission.getManifest().getFiles().add(file);

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }

        return submission;
    }

}
