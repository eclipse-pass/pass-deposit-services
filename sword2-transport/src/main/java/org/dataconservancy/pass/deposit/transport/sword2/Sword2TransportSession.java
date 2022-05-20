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
package org.dataconservancy.pass.deposit.transport.sword2;

import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.COLLECTIONS_HINT_KEY;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.HINT_KEY;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.HINT_TUPLE_SEPARATOR;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.HINT_URL_SEPARATOR;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_COLLECTION_HINTS;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_COLLECTION_URL;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.google.gson.JsonElement;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Checksum;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swordapp.client.AuthCredentials;
import org.swordapp.client.Deposit;
import org.swordapp.client.DepositReceipt;
import org.swordapp.client.ProtocolViolationException;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDError;
import org.swordapp.client.ServiceDocument;

/**
 * Encapsulates a session with a SWORDv2 endpoint authenticated using the transport hints supplied on {@link
 * Sword2Transport#open(Map)}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class Sword2TransportSession implements TransportSession {

    private static final Logger LOG = LoggerFactory.getLogger(Sword2TransportSession.class);

    private static final String SPEC_URL_CREATING_SWORD_BINARY =
        "http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#protocoloperations_creatingresource_binary";

    private static final String WARN_MISSING_SHOULD = "SWORD v2 deposit request is missing HTTP request header '%s' " +
                                                      "recommended as SHOULD by %s";

    private boolean closed = false;

    private SWORDClient client;

    private ServiceDocument serviceDocument;

    private AuthCredentials authCreds;

    public Sword2TransportSession(SWORDClient client, ServiceDocument serviceDocument, AuthCredentials authCreds) {
        if (client == null) {
            throw new IllegalArgumentException("SWORDClient must not be null.");
        }

        if (serviceDocument == null) {
            throw new IllegalArgumentException("ServiceDocument must not be null.");
        }

        if (authCreds == null) {
            throw new IllegalArgumentException("AuthCredentials must not be null.");
        }

        this.client = client;
        this.serviceDocument = serviceDocument;
        this.authCreds = authCreds;
    }

    /**
     * <pre>
     * // Collection URI?  How does the client select the collection?  Hard-coded property?  Why would all submissions
     * // go to the same collection?  Some logic for looking at the package and selecting the appropriate collection?
     * // TODO: hints must carry (at least initially) hard-coded url to the SWORD collection being deposited to
     * Deposit deposit = new Deposit();
     * deposit.setFile(new FileInputStream(myFile)); // content stream
     * deposit.setMimeType("application/zip"); // metadata - obtained from PackageInputStream, fallback to
     * TRANSPORT_MIME_TYPE property
     * deposit.setFilename("example.zip"); // destination resource? or obtain from PackageInputStream, fall back to
     * 'SWORDV2_FILENAME'?  Used for content-disposition headers
     * deposit.setPackaging(UriRegistry.PACKAGE_SIMPLE_ZIP); // metadata - obtained from PackageInputStream.spec,
     * fall back to 'SWORDV2_PACKAGE_SPEC';
     * deposit.setMd5(fileMD5);  // metadata, or from package stream
     * deposit.setInProgress(true); // ??  false if we are submitting a package
     * deposit.setSuggestedIdentifier("abcdefg"); // ??  Slug, same as deposit.setFilename?
     * </pre>
     *
     * @param packageStream {@inheritDoc}
     * @param metadata      {@inheritDoc}
     * @return {@inheritDoc}
     * @throws IllegalStateException if this session has been {@link #close() closed}
     * @see
     * <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#protocoloperations_creatingresource_binary">
     *     SWORD v2 Profile</a>
     */
    @Override
    public TransportResponse send(PackageStream packageStream, Map<String, String> metadata) {
        if (closed) {
            throw new IllegalStateException("SWORDv2 transport session has been closed.");
        }

        Deposit swordDeposit = new Deposit();
        PackageStream.Metadata streamMetadata = packageStream.metadata();

        // Satisfy MUSTs from
        // http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#protocoloperations_creatingresource_binary

        if (streamMetadata.name() == null || streamMetadata.name().length() == 0) {
            throw new IllegalStateException("PackageStream MUST have a name() per " + SPEC_URL_CREATING_SWORD_BINARY);
        } else {
            swordDeposit.setFilename(streamMetadata.name());
        }

        // Log violations of SHOULD from
        // http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#protocoloperations_creatingresource_binary

        if (streamMetadata.mimeType() == null || streamMetadata.mimeType().length() == 0) {
            LOG.warn(String.format(WARN_MISSING_SHOULD, "Content-Type", SPEC_URL_CREATING_SWORD_BINARY));
        } else {
            swordDeposit.setMimeType(streamMetadata.mimeType());
        }

        streamMetadata
            .checksums()
            .stream()
            .filter(sum -> Checksum.OPTS.MD5 == sum.algorithm())
            .findFirst().ifPresent(md5 -> swordDeposit.setMd5(md5.asHex()));

        if (swordDeposit.getMd5() == null) {
            LOG.warn(String.format(WARN_MISSING_SHOULD, "Content-MD5", SPEC_URL_CREATING_SWORD_BINARY));
        }

        if (streamMetadata.spec() == null || streamMetadata.spec().length() == 0) {
            LOG.warn(String.format(WARN_MISSING_SHOULD, "Packaging", SPEC_URL_CREATING_SWORD_BINARY));
        } else {
            swordDeposit.setPackaging(streamMetadata.spec());
        }

        // Other headers

        if (streamMetadata.sizeBytes() > -1) {
            swordDeposit.setContentLength(streamMetadata.sizeBytes());
        }

        swordDeposit.setInProgress(false);

        DepositReceipt receipt = null;

        try (InputStream stream = packageStream.open()) {
            swordDeposit.setFile(stream);
            receipt = client.deposit(selectCollection(serviceDocument, packageStream.metadata(), metadata),
                                     swordDeposit, authCreds);
        } catch (SWORDError e) {
            return new Sword2ErrorResponse(e);
        } catch (ProtocolViolationException | InvalidCollectionUrl e) {
            return new Sword2ThrowableResponse(e);
        } catch (IOException e) {
            return new Sword2ThrowableResponse(
                new RuntimeException("Error closing PackageStream: " + e.getMessage(), e));
        } catch (Exception e) {
            return new Sword2ThrowableResponse(new RuntimeException("Error depositing SWORD package to '" +
                                                                    selectCollection(serviceDocument,
                                                                                     packageStream.metadata(),
                                                                                     metadata).getHref()
                                                                                              .toASCIIString() +
                                                                    "': " + e.getMessage(), e));
        }

        return new Sword2DepositReceiptResponse(receipt);
    }

    @Override
    public boolean closed() {
        return this.closed;
    }

    @Override
    public void close() throws Exception {
        if (this.closed()) {
            return;
        }

        this.closed = true;
    }

    /**
     * Selects the APP Collection that the SWORD deposit is being submitted to.
     * <p>
     * The {@code metadata} property {@link Sword2TransportHints#SWORD_COLLECTION_HINTS} will be consulted when a hint
     * exists in the {@code packageMetadata}.  If the hint present in {@code packageMetadata} matches a hint supplied
     * in the {@link Sword2TransportHints#SWORD_COLLECTION_HINTS SWORD collection hints}, then the SWORD collection
     * for that hint will be used for deposit.
     * </p>
     * <p>
     * If the hint present in {@code packageMetadata} does not match any hint in the SWORD collection hints, or if no
     * hints are present in the {@code packageMetadata}, then the collection from
     * {@link Sword2TransportHints#SWORD_COLLECTION_URL}
     * will be used.
     * </p>
     * <p>
     * With this logic in place, if the UI does not provide a hint, deposits will go to the default collection as
     * configured by the {@code default-collection} parameter in the Deposit Services config (this maintains existing
     * behavior).  If the UI does provide a hint, and that hint is a key in the {@code collection-hints} Deposit
     * Service config, then the hint is used to resolve the collection.  Otherwise the the {@code default-collection}
     * is used.
     * </p>
     * <p>
     * Regardless of how the collection URL is resolved (via hints or declared), the collection URL must be advertised
     * in the SWORD service document.
     * </p>
     *
     * @param serviceDoc
     * @param packageMetadata
     * @param configurationMetadata
     * @return
     */
    SWORDCollection selectCollection(ServiceDocument serviceDoc, PackageStream.Metadata packageMetadata,
                                     Map<String, String> configurationMetadata) {
        String collectionUrl = configurationMetadata.get(SWORD_COLLECTION_URL);
        String configuredHints = configurationMetadata.get(SWORD_COLLECTION_HINTS);

        if (collectionUrl == null || collectionUrl.trim().length() == 0) {
            throw new RuntimeException("Missing required transport hint '" + SWORD_COLLECTION_URL + "'");
        }

        // If the submission metadata contains hints, check to see if any of them are present in the deposit service
        // configuration.  If so, update the deposit collection url
        // TODO what if multiple hints are present and map to multiple collection URLs?  Do we make multiple deposits?
        if (packageMetadata.submissionMeta() != null &&
            packageMetadata.submissionMeta().has(HINT_KEY) &&
            packageMetadata.submissionMeta().getAsJsonObject(HINT_KEY).has(COLLECTIONS_HINT_KEY)) {
            if (configuredHints != null && configuredHints.trim().length() > 0) {
                String[] tuples = configuredHints.split(HINT_TUPLE_SEPARATOR);
                FOUND:
                for (String tuple : tuples) {
                    if (tuple.contains(HINT_URL_SEPARATOR)) {
                        String splitRegex = "\\" + HINT_URL_SEPARATOR;
                        if (tuple.split(splitRegex).length == 2) {
                            String configuredHint = tuple.split(splitRegex)[0];
                            String configuredUrl = tuple.split(splitRegex)[1];
                            for (JsonElement submissionHint : packageMetadata.submissionMeta().getAsJsonObject(HINT_KEY)
                                                                             .getAsJsonArray(COLLECTIONS_HINT_KEY)) {
                                if (submissionHint.getAsString().equalsIgnoreCase(configuredHint)) {
                                    collectionUrl = configuredUrl;
                                    // TODO we find the first match; don't handle the case where multiple hints match
                                    break FOUND;
                                }
                            }
                        }
                    }
                }
            }
        }

        final String cUrl = collectionUrl;

        SWORDCollection collection = serviceDoc.getWorkspaces()
                   .stream()
                   .flatMap(workspace -> workspace.getCollections().stream())
                   // TODO is collectionUrl encoded (fingers crossed)?  Just concerned
                   //  that a user-encoded
                   //   collection URL in the hints mapping may not match what is returned by the service document
                   .filter(collectionCandidate -> collectionCandidate.getHref().toString()
                                                                     .equals(cUrl))
                   .findAny()
                   .orElseThrow(() ->
                                    new InvalidCollectionUrl(
                                        "SWORD Collection with URL '" + cUrl + "' not found."));

        return collection;
    }

    /**
     * Exposed for unit testing only.
     *
     * @return the configured SWORD authentication credentials
     */
    AuthCredentials getAuthCreds() {
        return authCreds;
    }
}
