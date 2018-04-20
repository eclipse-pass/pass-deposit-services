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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PassJsonFedoraAdapterTest {

    private String SAMPLE_DATA_FILE = "SampleSubmissionData.json";
    private URL sampleDataUrl;
    private PassJsonFedoraAdapter reader;

    @Before
    public void setup() {
        sampleDataUrl = FilesystemModelBuilderTest.class.getClassLoader().getResource(SAMPLE_DATA_FILE);
        reader = new PassJsonFedoraAdapter();
    }

    //@Test
    public void roundTrip() {
        try {
            // Upload the sample data to the Fedora repo.
            InputStream is = new FileInputStream(sampleDataUrl.getPath());
            URI submissionUri = reader.jsonToFcrepo(is);
            is.close();

            // Download the data from the server to a temporary JSON file
            File tempFile = File.createTempFile("fcrepo", ".json");
            tempFile.deleteOnExit();
            String tempFilePath = tempFile.getCanonicalPath();
            FileOutputStream fos = new FileOutputStream(tempFile);
            reader.fcrepoToJson(submissionUri, fos);
            fos.close();

            // Read the two files into JSON models
            is = new FileInputStream(sampleDataUrl.getPath());
            String origString = IOUtils.toString(is, Charset.defaultCharset());
            JsonArray origJson = new JsonParser().parse(origString).getAsJsonArray();
            is.close();
            is = new FileInputStream(tempFilePath);
            String resultString = IOUtils.toString(is, Charset.defaultCharset());
            JsonArray resultJson = new JsonParser().parse(resultString).getAsJsonArray();
            is.close();

            // Compare the two files.  Array contents may be in a different order, and URIs have changed,
            // so find objects with same @type field and compare the values of their first properties.
            assertEquals(origJson.size(), resultJson.size());
            for (JsonElement origElement : origJson) {
                boolean found = false;
                JsonObject origObj = origElement.getAsJsonObject();
                String origType = origObj.get("@type").getAsString();
                String firstPropName = origObj.keySet().iterator().next();
                for (JsonElement resultElement : resultJson) {
                    JsonObject resObj = resultElement.getAsJsonObject();
                    if (origType.equals(resObj.get("@type").getAsString()) &&
                        origObj.get(firstPropName).getAsString().equals(resObj.get(firstPropName).getAsString())) {
                        found = true;
                        break;
                    }
                }
                assertTrue("Could not find source object in result array.", found);
            }

        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not close the sample data file");
        }
    }

}