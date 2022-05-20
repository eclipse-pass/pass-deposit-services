/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.messaging.support.swordv2;

import static org.dataconservancy.pass.deposit.messaging.support.swordv2.ResourceResolverImpl.isRedirect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

import org.dataconservancy.pass.deposit.assembler.shared.AuthenticatedResource;
import org.dataconservancy.pass.deposit.messaging.config.repository.BasicAuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class ResourceResolverImplTest {

    private RepositoryConfig repoConfig;

    private URI uri;

    private URL url;

    private String originalUri;

    private String redirectUrl;

    private HttpURLConnection conn;

    private ResourceResolverImpl redirectingResolver;

    private ResourceResolverImpl resolver;

    @Before
    public void setUp() throws Exception {
        repoConfig = mock(RepositoryConfig.class, RETURNS_DEEP_STUBS);
        url = mock(URL.class);
        uri = mock(URI.class);
        originalUri = "http://some/url";

        redirectUrl = "https://some/url";
        conn = mock(HttpURLConnection.class);

        when(uri.toURL()).thenReturn(url);
        when(uri.toString()).thenReturn(originalUri);
        when(uri.getScheme()).thenReturn("http");
        when(uri.toString()).thenReturn(originalUri);

        when(url.getProtocol()).thenReturn("http");
        when(url.openConnection()).thenReturn(conn);
        when(url.toString()).thenReturn(originalUri);

        when(conn.getResponseCode()).thenReturn(302);
        when(conn.getHeaderField("Location")).thenReturn(redirectUrl);

        BasicAuthRealm realm = mock(BasicAuthRealm.class);
        when(repoConfig.getTransportConfig().getAuthRealms()).thenReturn(Collections.singletonList(realm));
        when(realm.getUsername()).thenReturn("fedoraAdmin");
        when(realm.getPassword()).thenReturn("moo");
        when(realm.getBaseUrl()).thenReturn(originalUri);

        resolver = new ResourceResolverImpl(false);

        redirectingResolver = new ResourceResolverImpl(true);
    }

    @Test
    public void resolveWithRedirect() throws IOException {
        Resource r = redirectingResolver.resolve(uri, repoConfig);

        assertNotNull(r);
        assertEquals(AuthenticatedResource.class, r.getClass());

        assertEquals(redirectUrl, r.getURL().toString());

        verify(conn).getResponseCode();
        verify(conn).getHeaderField("Location");
    }

    @Test
    public void resolveNoRedirect() throws IOException {
        Resource r = resolver.resolve(uri, repoConfig);

        assertNotNull(r);
        assertEquals(AuthenticatedResource.class, r.getClass());

        assertEquals(originalUri, r.getURL().toString());

        verifyNoInteractions(conn);
    }

    @Test
    public void nonHttpOrHttpsUrl() throws MalformedURLException {
        assertEquals(Optional.empty(), isRedirect(new URL("file:///foo/bar/baz")));
        verifyNoInteractions(conn);
    }

    @Test
    public void test302RedirectOk() throws IOException {
        assertEquals(redirectUrl, isRedirect(url).get().toString());
        verify(conn).getResponseCode();
        verify(conn).getHeaderField("Location");
    }

    @Test
    public void test306and304() throws IOException {
        reset(conn);
        when(conn.getResponseCode()).thenReturn(304);

        assertEquals(Optional.empty(), isRedirect(url));
        verify(conn).getResponseCode();
        verify(conn, times(0)).getHeaderField("Location");

        reset(conn);
        when(conn.getResponseCode()).thenReturn(306);

        assertEquals(Optional.empty(), isRedirect(url));
        verify(conn).getResponseCode();
        verify(conn, times(0)).getHeaderField("Location");
    }

    @Test
    public void testNon3xx() throws IOException {
        reset(conn);
        when(conn.getResponseCode()).thenReturn(400);

        assertEquals(Optional.empty(), isRedirect(url));
        verify(conn).getResponseCode();
        verify(conn, times(0)).getHeaderField("Location");

        reset(conn);
        when(conn.getResponseCode()).thenReturn(500);

        assertEquals(Optional.empty(), isRedirect(url));
        verify(conn).getResponseCode();
        verify(conn, times(0)).getHeaderField("Location");

        reset(conn);
        when(conn.getResponseCode()).thenReturn(200);

        assertEquals(Optional.empty(), isRedirect(url));
        verify(conn).getResponseCode();
        verify(conn, times(0)).getHeaderField("Location");
    }

    @Test
    public void withClasspathUri() {
        URI uri = URI.create("classpath:/path/to/resource");
        Resource resource = resolver.resolve(uri, repoConfig);
        assertEquals(ClassPathResource.class, resource.getClass());
    }

    @Test
    @Ignore("Can't be done, '*' is an illegal character for a URI scheme")
    public void withClasspathStarUri() {
        URI uri = URI.create("classpath*:/path/to/resource");
        Resource resource = resolver.resolve(uri, repoConfig);
        assertEquals(ClassPathResource.class, resource.getClass());
    }

    @Test
    public void withFileUri() {
        URI uri = URI.create("file:/path/to/resource");
        Resource resource = resolver.resolve(uri, repoConfig);
        assertEquals(FileSystemResource.class, resource.getClass());
    }

    @Test
    public void withHttpUri() {
        URI uri = URI.create("http://path/to/resource");
        Resource resource = resolver.resolve(uri, repoConfig);
        assertEquals(UrlResource.class, resource.getClass());
        assertNotEquals(AuthenticatedResource.class, resource.getClass());
    }

    @Test
    public void withHttpsUri() {
        URI uri = URI.create("https://path/to/resource");
        Resource resource = resolver.resolve(uri, repoConfig);
        assertEquals(UrlResource.class, resource.getClass());
        assertNotEquals(AuthenticatedResource.class, resource.getClass());
    }

    @Test
    public void withHttpUriMatchingAuthRealm() {
        URI uri = URI.create(originalUri);
        Resource resource = resolver.resolve(uri, repoConfig);
        assertEquals(AuthenticatedResource.class, resource.getClass());
        assertNotEquals(UrlResource.class, resource.getClass());
    }

    @Test
    public void withJarUri() {
        URI uri = URI.create("jar:file:/path/to/file.jar!/path/to/resource");
        Resource resource = resolver.resolve(uri, repoConfig);
        assertEquals(UrlResource.class, resource.getClass());
    }

    @Test
    public void withUnknownScheme() {
        URI uri = URI.create("moo:/path/to/resource");
        Resource resource = resolver.resolve(uri, repoConfig);
        assertNull(resource);
    }
}