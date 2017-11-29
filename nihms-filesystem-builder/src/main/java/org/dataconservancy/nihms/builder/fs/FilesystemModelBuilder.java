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
import org.dataconservancy.nihms.builder.SubmissionBuilder;
import org.dataconservancy.nihms.model.NihmsFile;
import org.dataconservancy.nihms.model.NihmsMetadata;
import org.dataconservancy.nihms.model.NihmsSubmission;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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

        //The submission object to populate
        NihmsSubmission submission = new NihmsSubmission();

        //The file to add, and its list to add to submission
        NihmsFile file = new NihmsFile();
        List<NihmsFile> files = new ArrayList<>();

        //The metadata object, and components to add to it ...
        NihmsMetadata metadata = new NihmsMetadata();
        NihmsMetadata.Journal journal = new NihmsMetadata.Journal();
        NihmsMetadata.Manuscript manuscript = new NihmsMetadata.Manuscript();

        //... including the person object and its list
        NihmsMetadata.Person person = new NihmsMetadata.Person();
        List<NihmsMetadata.Person> persons = new ArrayList<>();

        try (InputStream is = new FileInputStream(formDataUrl)){
            if (is == null) {
                throw new InvalidModel("Sorry, unable to find submission properties file " + formDataUrl);
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
                    case NihmsBuilderPropertyNames.NIHMS_FILE_LOCATION:
                        file.setLocation(value);
                        break;

                    //journal metadata
                    case NihmsBuilderPropertyNames.NIHMS_JOURNAL_ID:
                        journal.setJournalId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_JOURNAL_ISSN:
                        journal.setIssn(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_JOURNAL_TITLE:
                        journal.setJournalTitle(value);
                        break;

                    //manuscript metadata
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_DOI:
                        manuscript.setDoi(URI.create(value));
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_ID:
                        manuscript.setNihmsId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PUBMEDID:
                        manuscript.setPubmedId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_PUBMEDCENTRALID:
                        manuscript.setPubmedCentralId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_URL:
                        manuscript.setManuscriptUrl(new URL(value));
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
                    case NihmsBuilderPropertyNames.NIHMS_PERSON_PI:
                        person.setPi(Boolean.parseBoolean(value));
                        break;
                }
            }

            //now populate the submission
            files.add(file);
            submission.setFiles(files);

            persons.add(person);
            metadata.setPersons(persons);

            metadata.setJournalMetadata(journal);
            metadata.setManuscriptMetadata(manuscript);

            submission.setMetadata(metadata);

        } catch (IOException ioe) {
            throw new InvalidModel(ioe.getMessage(), ioe);
        }

        return submission;
    }

}
