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
package org.dataconservancy.pass.deposit.messaging.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.model.Submission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class JsonParserTest {

    @Rule
    public TestName testName = new TestName();

    private JsonParser underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new JsonParser(new ObjectMapper());
    }


    @Test
    public void parseId() throws Exception {
        String expectedId = "http://fcrepo:8080/fcrepo/rest/submissions/92/42/2a/d3/92422ad3-6384-46cf-98ff" +
                "-332ff151000b";
        String resourceName = this.getClass().getSimpleName() + "-" + testName.getMethodName() + ".json";
        URL jsonUrl = this.getClass().getResource(resourceName);
        assertNotNull("Cannot resolve " + resourceName + " on the classpath.", jsonUrl);

        byte[] body = IOUtils.toByteArray(jsonUrl.openStream());
        assertNotNull("Null resource " + jsonUrl, body);
        assertTrue("Empty resource " + jsonUrl, body.length > 0);

        String submissionUri = underTest.parseId(body);
        assertNotNull("Failed to parse an id from resource " + jsonUrl, submissionUri);

        assertEquals(expectedId, submissionUri);
    }

    @Test
    public void parseRepositories() throws Exception {
        String resourceName = this.getClass().getSimpleName() + "-" + testName.getMethodName() + ".json";
        URL jsonUrl = this.getClass().getResource(resourceName);
        assertNotNull("Cannot resolve " + resourceName + " on the classpath.", jsonUrl);

        byte[] body = IOUtils.toByteArray(jsonUrl.openStream());
        assertNotNull("Null resource " + jsonUrl, body);
        assertTrue("Empty resource " + jsonUrl, body.length > 0);

        List<String> expectedUris = Arrays.asList("http://192.168.99.100:8080/fcrepo/rest/repositories/7a/59/0c/55" +
                "/7a590c55-b431-458f-a30f-3bcff312f9e3",
                "http://192.168.99.100:8080/fcrepo/rest/repositories/44/23/5c/60/44235c60-4dea-4838-84fa-c62aa6d13316" +
                        "", "http://192.168.99.100:8080/fcrepo/rest/repositories/dc/35/36/96/dc353696-3245-4969-a9b6" +
                        "-5edc70c91be0");

        Collection<String> repoUris = underTest.parseRepositoryUris(body);

        assertEquals(expectedUris.size(), repoUris.size());

        expectedUris.forEach(uri -> assertTrue(repoUris.contains(uri)));

    }

    @Test
    public void parseRepositories2() throws Exception {
        String resourceName = this.getClass().getSimpleName() + "-" + testName.getMethodName() + ".json";
        URL jsonUrl = this.getClass().getResource(resourceName);
        assertNotNull("Cannot resolve " + resourceName + " on the classpath.", jsonUrl);

        byte[] body = IOUtils.toByteArray(jsonUrl.openStream());
        assertNotNull("Null resource " + jsonUrl, body);
        assertTrue("Empty resource " + jsonUrl, body.length > 0);

        List<String> expectedUris = Arrays.asList("http://192.168.99.100:8080/fcrepo/rest/repositories/js",
                "http://192.168.99.100:8080/fcrepo/rest/repositories/nih");

        Collection<String> repoUris = underTest.parseRepositoryUris(body);

        assertEquals(expectedUris.size(), repoUris.size());

        expectedUris.forEach(uri -> assertTrue(repoUris.contains(uri)));

    }
}