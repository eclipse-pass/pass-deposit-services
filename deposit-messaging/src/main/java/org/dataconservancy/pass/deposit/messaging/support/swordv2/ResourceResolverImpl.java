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

import static java.lang.String.format;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomFeedStatusResolver.ERR;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import org.dataconservancy.pass.deposit.assembler.shared.AuthenticatedResource;
import org.dataconservancy.pass.deposit.messaging.config.repository.AuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.BasicAuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class ResourceResolverImpl implements ResourceResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceResolverImpl.class);

    private boolean followRedirects;

    public ResourceResolverImpl(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    @Override
    public Resource resolve(URI uri, RepositoryConfig repositoryConfig) {
        LOG.debug("Attempting resolution of SWORD status URI <{}> (followRedirects: {})", uri, followRedirects);

        Resource resource = null;

        if (uri.getScheme().startsWith("file")) {
            resource = new FileSystemResource(uri.getPath());
        } else if (uri.getScheme().startsWith("classpath")) {
            if (uri.getScheme().startsWith("classpath*")) {
                resource = new ClassPathResource(uri.toString().substring("classpath*:".length()));
            } else {
                resource = new ClassPathResource(uri.toString().substring("classpath:".length()));
            }
        } else if (uri.getScheme().startsWith("http")) {
            resource = matchRealm(uri.toString(),
                                  repositoryConfig.getTransportConfig().getAuthRealms())
                .map(realm -> {
                    try {
                        if (realm.getUsername() != null && realm.getUsername().trim().length() > 0) {
                            if (followRedirects) {
                                return new AuthenticatedResource(isRedirect(uri.toURL()).orElse(uri.toURL()),
                                                                 realm.getUsername(), realm.getPassword());
                            } else {
                                return new AuthenticatedResource(uri.toURL(),
                                                                 realm.getUsername(), realm.getPassword());
                            }
                        } else {
                            return new UrlResource(uri.toURL());
                        }
                    } catch (MalformedURLException e) {
                        String msg = format(ERR, uri, "Statement URI could not be parsed as URL");
                        throw new IllegalArgumentException(msg, e);
                    }
                }).orElseGet(() -> {
                    LOG.warn("Null AuthRealm used for Atom Statement URI '{}'", uri);
                    try {
                        return new UrlResource(uri.toURL());
                    } catch (MalformedURLException e) {
                        String msg = format(ERR, uri, "Statement URI could not be parsed as URL");
                        throw new IllegalArgumentException(msg, e);
                    }
                });
        } else if (uri.getScheme().startsWith("jar")) {
            try {
                resource = new UrlResource(uri);
            } catch (MalformedURLException e) {
                String msg = format(ERR, uri, "Statement URI could not be parsed as URL");
                throw new IllegalArgumentException(msg, e);
            }
        }

        return resource;
    }

    private static Optional<BasicAuthRealm> matchRealm(String url, Collection<AuthRealm> authRealms) {
        if (authRealms == null || authRealms.isEmpty()) {
            return Optional.empty();
        }

        return authRealms
            .stream()
            .filter(realm -> realm instanceof BasicAuthRealm)
            .map(realm -> (BasicAuthRealm) realm)
            .filter(realm -> url.startsWith(realm.getBaseUrl().toString()))
            .max(Comparator.comparingInt(realm -> realm.getBaseUrl().length()));
    }

    /**
     * Determines if the supplied URI will be redirected when opened, returning the redirect URL if so.
     * <p>
     * Returns an empty Optional if the scheme is not http or https.
     * Performs a HEAD request on the original URI.
     * If the response is 300, 301, 302, 303, 305, or 307 (<em>not</em> 306 or 304), the value of the Location header is
     * returned as a URL.
     * Otherwise an empty Optional is returned.
     * If any exceptions occur, they are swallowed and logged at WARN level, and an empty Optional is returned.
     * </p>
     *
     * @param original the URI, which may be of any scheme.
     * @return an Optional containing the redirect URL, or an empty Optional if the original URI is not redirected
     */
    static Optional<URL> isRedirect(URL original) {
        if (!"https".equalsIgnoreCase(original.getProtocol()) && !"http".equalsIgnoreCase(original.getProtocol())) {
            return Optional.empty();
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) original.openConnection();
        } catch (IOException e) {
            LOG.warn("Unable to determine if {} would be redirected, connection could not be opened.", original, e);
            return Optional.empty();
        }

        try {
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            if (code >= 300 && code <= 307 && code != 306 &&
                code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                URL location = URI.create(conn.getHeaderField("Location")).toURL();
                LOG.debug("{} will redirect {} to {}", AtomFeedStatusResolver.class.getSimpleName(), original,
                          location);
                return Optional.of(location);
            }
        } catch (IOException e) {
            LOG.warn("Unable to determine if {} would be redirected, an i/o error occurred", original, e);
        } finally {
            conn.disconnect();
        }

        return Optional.empty();
    }
}
