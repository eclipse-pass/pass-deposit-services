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
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositFileType;
import org.dataconservancy.nihms.model.DepositManifest;
import org.dataconservancy.nihms.model.DepositMetadata;
import org.dataconservancy.nihms.model.DepositSubmission;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
    public DepositSubmission build(String formDataUrl) throws InvalidModel {
        Properties properties = new Properties();
      
        //The submission object to populate
        DepositSubmission submission = new DepositSubmission();

        //The file to add, and its list to add to submission
        DepositFile file = new DepositFile();
        List<DepositFile> files = new ArrayList<>();

        //The metadata object, and components to add to it ...
        DepositMetadata metadata = new DepositMetadata();
        DepositMetadata.Journal journal = new DepositMetadata.Journal();
        DepositMetadata.Manuscript manuscript = new DepositMetadata.Manuscript();
        DepositMetadata.Article article = new DepositMetadata.Article();
        DepositManifest manifest = new DepositManifest();

        //... including the person object and its list
        DepositMetadata.Person person = new DepositMetadata.Person();
        List<DepositMetadata.Person> persons = new ArrayList<>();

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
                    //submission properties
                    case NihmsBuilderPropertyNames.NIHMS_SUBMISSION_ID:
                        submission.setId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_SUBMISSION_NAME:
                        submission.setName(value);
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
                    case NihmsBuilderPropertyNames.NIHMS_FILE_TYPE:
                        file.setType(DepositFileType.valueOf(value));
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

                    //article metadata
                    case NihmsBuilderPropertyNames.NIHMS_ARTICLE_DOI:
                        article.setDoi(URI.create(value));
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_ARTICLE_PUBMEDCENTRALID:
                        article.setPubmedCentralId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_ARTICLE_PUBMEDID:
                        article.setPubmedId(value);
                        break;

                    //manuscript metadata
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_ID:
                        manuscript.setNihmsId(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_URL:
                        manuscript.setManuscriptUrl(new URL(value));
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_MANUSCRIPT_TITLE:
                        manuscript.setTitle(value);

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
            manifest.setFiles(files);

            persons.add(person);
            metadata.setPersons(persons);

            metadata.setJournalMetadata(journal);
            metadata.setManuscriptMetadata(manuscript);
            metadata.setArticleMetadata(article);

            submission.setMetadata(metadata);
            submission.setManifest(manifest);

        } catch (IOException ioe) {
            throw new InvalidModel(ioe.getMessage(), ioe);
        }
      
        return submission;
    }

}
