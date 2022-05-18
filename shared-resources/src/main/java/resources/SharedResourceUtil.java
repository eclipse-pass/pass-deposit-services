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
package resources;

import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.utils.ClasspathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides access to test resources found on the classpath.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SharedResourceUtil {

    private SharedResourceUtil() {
        //never calles
    }

    private static final Logger LOG = LoggerFactory.getLogger(SharedResourceUtil.class);

    /**
     * Locates a test resource on the classpath by its name.
     * Caller is responsible for closing the returned stream.
     *
     * @param resourceName the classpath resource name
     * @return the input stream
     */
    public static InputStream findStreamByName(String resourceName) {
        return findStreamByName(resourceName, null);
    }

    /**
     * Locates a test resource on the classpath by its name.
     * Caller is responsible for closing the returned stream.
     *
     * @param resourceName the classpath resource name
     * @param baseClass    the base class to scan from
     * @return the input stream
     */
    public static InputStream findStreamByName(String resourceName, Class<?> baseClass) {
        Set<SharedResourceUtil.ElementPathPair> seen = new HashSet<>();
        AtomicReference<URL> resourceUrl = new AtomicReference<>();
        AtomicReference<InputStream> resource = new AtomicReference<>();
        FastClasspathScanner scanner = scannerForBaseClass(baseClass);

        scanner.matchFilenamePath(resourceName, (cpElt, relativePath, in, length) -> {
            LOG.trace("Matching '{}', '{}'", relativePath, cpElt);

            if (SharedResourceUtil.seen(seen, cpElt, relativePath)) {
                return;
            }

            URL foundUrl = ClasspathUtils.getClasspathResourceURL(cpElt, relativePath);

            if (resource.get() != null) {
                LOG.warn("Ignoring resource at '{}', already found '{}'", foundUrl, resourceUrl.get());
            } else {
                LOG.trace("Found resource '{}' matching name '{}'", foundUrl, resourceName);
                // open the stream ourselves, as the supplied stream is closed by the caller
                resource.set(foundUrl.openStream());
                resourceUrl.set(foundUrl);
            }
        });

        scanner.scan();

        assertFound(resourceName, resource);
        return resource.get();
    }

    /**
     * Finds the URI of a test resource using the resource's name.
     *
     * @param resourceName the classpath resource name
     * @return the uri of the resource
     */
    public static URI findUriByName(String resourceName) {
        return findUriByName(resourceName, null);
    }

    /**
     * Finds the URI of a test resource using the resource's name.
     *
     * @param resourceName the classpath resource name
     * @param baseClass    the base class to scan from
     * @return the uri of the resource
     */
    public static URI findUriByName(String resourceName, Class<?> baseClass) {
        Set<SharedResourceUtil.ElementPathPair> seen = new HashSet<>();
        AtomicReference<URL> resourceUrl = new AtomicReference<>();
        AtomicReference<URI> resource = new AtomicReference<>();
        FastClasspathScanner scanner = scannerForBaseClass(baseClass);

        scanner.matchFilenamePath(resourceName, (cpElt, relativePath, in, length) -> {
            LOG.trace("Matching '{}', '{}'", relativePath, cpElt);

            if (SharedResourceUtil.seen(seen, cpElt, relativePath)) {
                return;
            }

            URL foundUrl = ClasspathUtils.getClasspathResourceURL(cpElt, relativePath);

            if (resource.get() != null) {
                LOG.warn("Ignoring resource at '{}', already found '{}'", foundUrl, resourceUrl.get());
            } else {
                LOG.trace("Found resource '{}' matching name '{}'", foundUrl, resourceName);
                try {
                    resource.set(foundUrl.toURI());
                    resourceUrl.set(foundUrl);
                } catch (URISyntaxException e) {
                    LOG.warn("Unable to compose a URI from resource URL '" + foundUrl + "'");
                }
            }
        });

        scanner.scan();

        assertFound(resourceName, resource);

        return resource.get();
    }

    private static FastClasspathScanner scannerForBaseClass(Class<?> baseClass) {
        FastClasspathScanner scanner = null;
        if (baseClass == null) {
            scanner = new FastClasspathScanner("");
        } else {
            scanner = new FastClasspathScanner(baseClass.getPackage().getName());
        }
        return scanner;
    }

    private static void assertFound(String resourceName, AtomicReference<?> resource) {
        assertNotNull("Unable to find a resource named '" + resourceName + "'", resource.get());
    }

    /**
     * Convenience method which checks for the presence of the {@code classpathElement}/{@code relativePath} pair in the
     * {@code seen} {@code Set}.
     *
     * @param seen             a {@code Set} which contains all of the {@code ElementPathPair}s seen so far
     * @param classpathElement a classpath element, which combined with a relative path, may have been seen
     *                         before
     * @param relativePath     a relative path, when combined with a classpath element, may have been seen before
     * @return true if the {@code classpathElement}/{@code relativePath} has been seen before
     * @see ElementPathPair
     */
    public static boolean seen(Set<ElementPathPair> seen, java.io.File classpathElement, String relativePath) {
        ElementPathPair pathPair = new ElementPathPair(classpathElement, relativePath);
        if (seen.contains(pathPair)) {
            return true;
        }
        seen.add(pathPair);
        return false;
    }

    /**
     * Pairs a Classpath element with the relative path of the Class for file matched by a {@link FastClasspathScanner}
     * scan. <p> For some reason, some classpath elements can appear on the classpath twice (e.g. IDEA and Maven,
     * actually, put `target/test-classes` on the classpath twice).  The {@link ElementPathPair} is used to insure that
     * the same Classpath resource is not considered twice.  This allows the matching logic to warn users if a duplicate
     * Submission URI is found. </p>
     */
    public static class ElementPathPair {
        private java.io.File classpathElement;
        private String relativePath;

        public ElementPathPair(java.io.File classpathElement, String relativePath) {
            this.classpathElement = classpathElement;
            this.relativePath = relativePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ElementPathPair that = (ElementPathPair) o;

            if (classpathElement != null ? !classpathElement.equals(that.classpathElement) : that.classpathElement !=
                                                                                             null) {
                return false;
            }
            return relativePath != null ? relativePath.equals(that.relativePath) : that.relativePath == null;
        }

        @Override
        public int hashCode() {
            int result = classpathElement != null ? classpathElement.hashCode() : 0;
            result = 31 * result + (relativePath != null ? relativePath.hashCode() : 0);
            return result;
        }
    }
}
