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
import java.util.Enumeration;
import java.util.Properties;

/**
 * Builds a NIHMS submission from a file on a locally mounted filesystem.  The file contains key-value pairs that would
 * loosely mimic the form data provided by the view layer.
 */
public class FilesystemModelBuilder implements SubmissionBuilder {

    @Override
    public NihmsSubmission build(String formDataUrl) throws InvalidModel {
        Properties properties = new Properties();
        InputStream is = null;
        NihmsSubmission submission = new NihmsSubmission();
        NihmsFile file = new NihmsFile();
        NihmsMetadata metadata = new NihmsMetadata();

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
                    case NihmsBuilderPropertyNames.NIHMS_FILE_NAME:
                        file.setName(value);
                        break;
                    case NihmsBuilderPropertyNames.NIHMS_FILE_LABEL:
                        file.setLabel(value);
                        break;

                    case NihmsBuilderPropertyNames.NIHMS_JOURNAL_ID:



                }
               // System.out.println("Key : " + key + ", Value : " + value);
            }

            //now populate the submission
            submission.getFiles().add(file);

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
