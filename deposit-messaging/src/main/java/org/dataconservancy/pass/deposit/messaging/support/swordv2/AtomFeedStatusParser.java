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
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusParser;
import org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus;
import org.dataconservancy.pass.deposit.messaging.support.Constants;
import org.dataconservancy.pass.deposit.transport.sword2.Sword2DepositReceiptResponse;
import org.dataconservancy.pass.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;
import java.net.URI;

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
public class AtomFeedStatusParser implements DepositStatusParser<URI, SwordDspaceDepositStatus> {

    private static final Logger LOG = LoggerFactory.getLogger(AtomFeedStatusParser.class);

    private Parser abderaParser;

    private String swordUsername;

    private String swordPassword;

    public AtomFeedStatusParser(Parser abderaParser) {
        this.abderaParser = abderaParser;
    }

    /**
     * Determine the deposit status represented in the referenced Atom statement.
     * <p>
     * Retrieves the Atom statement, parses it, and examines it for the {@link Constants.SWORD#SWORD_STATE} term. If
     * the term exists, return the corresponding {@link SwordDspaceDepositStatus}.  If the term or the state cannot be
     * determined, return {@code null}.
     * </p>
     *
     * @param atomStatementUri the Atom statement URI
     * @return the {@code SwordDspaceDepositStatus}, or {@code null} if one cannot be found
     * @see <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement_predicates_state">SWORDv2 Profile §11.1.2</a>
     */
    public SwordDspaceDepositStatus parse(URI atomStatementUri) {
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
            try {
                resource = new AuthenticatedResource(atomStatementUri.toURL(), swordUsername, swordPassword);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Atom statement could not be parsed as URL '" + atomStatementUri +
                        "':" + e.getMessage(), e);
            }
        } else if (atomStatementUri.getScheme().startsWith("jar")) {
            try {
                resource = new UrlResource(atomStatementUri);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Atom statement could not be parsed as URL '" + atomStatementUri +
                        "':" + e.getMessage(), e);
            }
        }

        if (resource == null) {
            throw new IllegalArgumentException("Atom statement URI could not be parsed as a Spring resource: '" +
                    atomStatementUri + "'");
        }

        Document<Feed> statementDoc = null;
        try {
            LOG.trace("Retrieving SWORD Statement from: {}", atomStatementUri);
            statementDoc = abderaParser.parse(resource.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Error parsing Atom resource '" + resource + "' (resolved from '" +
                    atomStatementUri + "'): " + e.getMessage(), e);
        }

        return AtomUtil.parseAtomStatement(statementDoc);
    }

    public String getSwordUsername() {
        return swordUsername;
    }

    public void setSwordUsername(String swordUsername) {
        this.swordUsername = swordUsername;
    }

    public String getSwordPassword() {
        return swordPassword;
    }

    public void setSwordPassword(String swordPassword) {
        this.swordPassword = swordPassword;
    }
}
