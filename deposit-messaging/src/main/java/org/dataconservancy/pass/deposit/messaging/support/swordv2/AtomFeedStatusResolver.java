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

import static java.lang.String.format;

import java.net.URI;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusResolver;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2DepositReceiptResponse;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.support.messaging.constants.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

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

    static final String ERR = "Error resolving deposit status URI from SWORD statement <%s>: %s";

    private static final Logger LOG = LoggerFactory.getLogger(AtomFeedStatusResolver.class);

    private Parser abderaParser;

    private ResourceResolver resourceResolver;

    public AtomFeedStatusResolver(Parser abderaParser, ResourceResolver resourceResolver) {
        this.abderaParser = abderaParser;
        this.resourceResolver = resourceResolver;
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
     * @see
     * <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement_predicates_state">SWORDv2 Profile §11.1.2</a>
     */
    public URI resolve(URI atomStatementUri, RepositoryConfig repositoryConfig) {
        if (atomStatementUri == null) {
            throw new IllegalArgumentException("Atom statement URI must not be null.");
        }

        Resource resource = resourceResolver.resolve(atomStatementUri, repositoryConfig);

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

}
