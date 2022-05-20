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
package org.dataconservancy.pass.deposit.builder.fedora;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.dataconservancy.deposit.util.spring.EncodingClassPathResource;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class ClasspathResourceIT {

    private static Logger LOG = LoggerFactory.getLogger(ClasspathResourceIT.class);

    @Test
    public void unencodedNoSpace() throws Exception {
        verify(asClasspathResource("/dektol.jpg"));
    }

    @Test
    public void unencodedNoSpaceAsUrl() throws Exception {
        verify(asUrlResource("/dektol.jpg"));
    }

    @Test
    public void unencodedNoSpaceAsEncodedResource() throws Exception {
        verify(asEncodedClasspathResource("/dektol.jpg"));
    }

    @Test
    public void unencodedWithSpace() throws Exception {
        verify(asClasspathResource("/ilford panf.pdf"));
    }

    @Test
    @Ignore("Fails, test remains as documentation")
    public void encodedWithSpace() throws Exception {
        verify(asClasspathResource("/ilford%20panf.pdf"));
    }

    @Test
    @Ignore("Fails, test remains as documentation")
    public void encodedWithSpaceAsUrl() throws Exception {
        verify(asUrlResource("/ilford%20panf.pdf"));
    }

    @Test
    public void encodedWithSpaceAsEncodedResource() throws Exception {
        verify(asEncodedClasspathResource("/ilford%20panf.pdf"));
    }

    @Test
    public void unencodedWithSpaceAsUrl() throws Exception {
        verify(asUrlResource("/ilford panf.pdf"));
    }

    @Test
    public void unencodedWithSpaceAsEncodedResource() throws Exception {
        verify(asEncodedClasspathResource("/ilford panf.pdf"));
    }

    @Test
    @Ignore("Passes locally, but not portable.  Remains for documentation.")
    public void verifyHardcodedEncodedUrlWithSpace() throws Exception {
        UrlResource u = new UrlResource("file:/Users/esm/workspaces/pass/nihms-submission/nihms-integration/target" +
                                        "/test-classes/org/dataconservancy/pass/deposit/builder/fedora/ilford%20panf" +
                                        ".pdf");
        verify(u);
    }

    private static void verify(Resource r) throws IOException {
        assertNotNull(r.getURL());
        assertTrue(r.exists());
        if (r instanceof ClassPathResource) {
            assertNotNull(((ClassPathResource) r).getPath());
        }
        try (InputStream inputStream = r.getInputStream()) {
            assertNotNull(inputStream);
        }
    }

    private static ClassPathResource asClasspathResource(String resource) {
        String resourcePath = ClasspathResourceIT.class.getPackage().getName()
                                                       .replace(".", "/") + resource;
        LOG.debug("Instantiating ClassPathResource({})", resourcePath);
        return new ClassPathResource(resourcePath);
    }

    private static EncodingClassPathResource asEncodedClasspathResource(String resource) {
        String resourcePath = ClasspathResourceIT.class.getPackage().getName()
                                                       .replace(".", "/") + resource;
        LOG.debug("Instantiating ClassPathResource({})", resourcePath);
        return new EncodingClassPathResource(resourcePath);
    }

    private static UrlResource asUrlResource(String resource) {
        String resourcePath = "/org/dataconservancy/pass/deposit/builder/fedora" + resource;
        URL u = ClasspathResourceIT.class.getResource(resourcePath);
        LOG.debug("Instantiating UrlResource({})", u);
        return new UrlResource(u);
    }

}
