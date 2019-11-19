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
package org.dataconservancy.pass.deposit.messaging.support.swordv2;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.dataconservancy.pass.deposit.assembler.shared.AuthenticatedResource;
import org.dataconservancy.pass.deposit.messaging.config.repository.AuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.BasicAuthRealm;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver;
import org.dataconservancy.pass.support.messaging.constants.Constants;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2DepositReceiptResponse;
import org.dataconservancy.pass.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import static java.lang.String.format;

/**
 * Attempts to determine the status of a {@link Deposit} by retrieving the Atom Statement associated with the
 * {@code Deposit}, parsing it, and returning a status.
 * <p>
 * Atom Statements are typically obtained by de-referencing the {@link Deposit#getDepositStatusRef()}, or inspecting
 * the {@link Sword2DepositReceiptResponse#getReceipt() SWORDv2 deposit receipt}.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement">SWORDv2 Profile §11</a>
 * @see org.dataconservancy.pass.deposit.messaging.service.DepositTask
 */
public class AtomFeedStatusResolver implements DepositStatusResolver<URI, URI> {

    private static final Logger LOG = LoggerFactory.getLogger(AtomFeedStatusResolver.class);

    private static final String ERR = "Error resolving deposit status URI from SWORD statement <%s>: %s";

    private Parser abderaParser;

    private boolean followRedirects;

    public AtomFeedStatusResolver(Parser abderaParser) {
        this(abderaParser, false);
    }

    public AtomFeedStatusResolver(Parser abderaParser, boolean followRedirects) {
        this.abderaParser = abderaParser;
        this.followRedirects = followRedirects;
    }

    /**
     * Determine the deposit status represented in the referenced Atom statement.
     * <p>
     * Retrieves the Atom statement, parses it, and examines it for the {@link Constants.SWORD#SWORD_STATE} term. If
     * the term exists, return the corresponding {@code URI}.  If the term or the state cannot be determined, return
     * {@code null}.
     * </p>
     *
     * @param atomStatementUri the Atom statement URI
     * @param repositoryConfig the configuration containing an {@code auth-realm} with authentication credentials for
     *                         retrieving the {@code atomStatementUri}
     * @return the state {@code URI}, or {@code null} if one cannot be found
     * @see <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement_predicates_state">SWORDv2 Profile §11.1.2</a>
     */
    public URI resolve(URI atomStatementUri, RepositoryConfig repositoryConfig) {
        if (atomStatementUri == null) {
            throw new IllegalArgumentException("Atom statement URI must not be null.");
        }

        Resource resource = null;

        if (atomStatementUri.getScheme().startsWith("file")) {
            resource = new FileSystemResource(atomStatementUri.getPath());
        } else if (atomStatementUri.getScheme().startsWith("classpath")) {
            if (atomStatementUri.getScheme().startsWith("classpath*")) {
                resource = new ClassPathResource(atomStatementUri.toString().substring("classpath*:".length()));
            } else {
                resource = new ClassPathResource(atomStatementUri.toString().substring("classpath:".length()));
            }
        } else if (atomStatementUri.getScheme().startsWith("http")) {
            resource = matchRealm(atomStatementUri.toString(),
                    repositoryConfig.getTransportConfig().getAuthRealms())
                        .map(realm -> {
                            try {
                                if (realm.getUsername() != null && realm.getUsername().trim().length() > 0) {
                                    if (followRedirects) {
                                        return new AuthenticatedResource(isRedirect(atomStatementUri).orElse(atomStatementUri.toURL()),
                                                realm.getUsername(), realm.getPassword());
                                    } else {
                                        return new AuthenticatedResource(atomStatementUri.toURL(),
                                                realm.getUsername(), realm.getPassword());
                                    }
                                } else {
                                    return new UrlResource(atomStatementUri.toURL());
                                }
                            } catch (MalformedURLException e) {
                                String msg = format(ERR, atomStatementUri, "Statement URI could not be parsed as URL");
                                throw new IllegalArgumentException(msg, e);
                            }
                        }).orElseGet(() -> {
                            LOG.warn("Null AuthRealm used for Atom Statement URI '{}'", atomStatementUri);
                            try {
                                return new UrlResource(atomStatementUri.toURL());
                            } catch (MalformedURLException e) {
                                String msg = format(ERR, atomStatementUri, "Statement URI could not be parsed as URL");
                                throw new IllegalArgumentException(msg, e);
                            }
                        });
        } else if (atomStatementUri.getScheme().startsWith("jar")) {
            try {
                resource = new UrlResource(atomStatementUri);
            } catch (MalformedURLException e) {
                String msg = format(ERR, atomStatementUri, "Statement URI could not be parsed as URL");
                throw new IllegalArgumentException(msg, e);
            }
        }

        if (resource == null) {
            throw new IllegalArgumentException(format(ERR, atomStatementUri,
                    "Statement URI not recognized as a Spring resource"));
        }

        Document<Feed> statementDoc = null;
        try {
            LOG.trace("Retrieving and parsing SWORD statement <{}>", atomStatementUri);
            statementDoc = abderaParser.parse(resource.getInputStream());
            return AtomUtil.parseSwordState(statementDoc);
        } catch (Exception e) {
            String msg = format(ERR, atomStatementUri, "Error resolving or parsing Atom statement: " + e.getMessage());
            throw new RuntimeException(msg, e);
        }
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
    private static Optional<URL> isRedirect(URI original) {
        if (!"https".equalsIgnoreCase(original.getScheme()) && !"http".equalsIgnoreCase(original.getScheme())) {
            return Optional.empty();
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection)new URL(original.toString()).openConnection();
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
                LOG.debug("{} will redirect {} to {}", AtomFeedStatusResolver.class.getSimpleName(), original, location);
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
