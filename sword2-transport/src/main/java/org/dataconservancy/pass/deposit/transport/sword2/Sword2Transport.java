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

import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_SERVICE_DOC_URL;

import java.util.Map;

import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.swordapp.client.AuthCredentials;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.ServiceDocument;

/**
 * Encapsulates a provider for SWORD protocol version 2 transport sessions.  {@link #open(Map) Opening} a SWORDv2
 * transport session entails the following:
 * <ol>
 *     <li>Instantiating a {@link SWORDClient}</li>
 *     <li>Logging into the SWORD endpoint using the credentials provided by the {@link Transport#TRANSPORT_USERNAME},
 *         {@link Transport#TRANSPORT_PASSWORD}, and {@link Sword2TransportHints#SWORD_ON_BEHALF_OF_USER} hints</li>
 *     <li>Obtaining the SWORD service document from the URL provided by the
 *         {@link Sword2TransportHints#SWORD_SERVICE_DOC_URL} hint</li>
 * </ol>
 * In other words, a caller executing a {@link Sword2Transport#open(Map)} will receive a {@link Sword2TransportSession}
 * that is configured with a {@code SWORDClient}, working authentication credentials (potentially acting on behalf of
 * a user), and the {@code ServiceDocument} located at the {@link Sword2TransportHints#SWORD_SERVICE_DOC_URL service
 * document URL}.
 *
 * Hints accepted by this transport are:
 * <dl>
 *     <dt>{@link Transport#TRANSPORT_USERNAME}</dt>
 *     <dd>The username to authenticate as</dd>
 *     <dt>{@link Transport#TRANSPORT_PASSWORD}</dt>
 *     <dd>The password to authenticate with</dd>
 *     <dt>{@link Transport#TRANSPORT_AUTHMODE}</dt>
 *     <dd>The authentication mode, which must be set to {@link Transport.AUTHMODE#userpass}</dd>
 *     <dt>{@link Sword2TransportHints#SWORD_SERVICE_DOC_URL}</dt>
 *     <dd>A URL to the SWORD version 2 service document</dd>
 *     <dt>{@link Sword2TransportHints#SWORD_COLLECTION_URL}</dt>
 *     <dd>A URL to the SWORD version 2 collection that packages will be deposited to upon
 *         {@link Sword2TransportSession#send(PackageStream, Map) send}</dd>
 *     <dt><em>Optional:</em> {@link Sword2TransportHints#SWORD_ON_BEHALF_OF_USER}</dt>
 *     <dd>The username this session is being opened for</dd>
 *     <dt><em>Optional:</em> {@link Sword2TransportHints#SWORD_CLIENT_USER_AGENT}</dt>
 *     <dd>A string used to identify the user agent when opening transport sessions</dd>
 *     <dt><em>Optional:</em> {@link Sword2TransportHints#SWORD_DEPOSIT_RECEIPT_FLAG}</dt>
 *     <dd>A boolean flag indicating whether or not a deposit receipt is required</dd>
 * </dl>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class Sword2Transport implements Transport {

    static final String MISSING_REQUIRED_HINT = "Missing required transport hint '%s'";

    private Sword2ClientFactory clientFactory;

    @Autowired
    public Sword2Transport(Sword2ClientFactory clientFactory) {
        if (clientFactory == null) {
            throw new IllegalArgumentException("SWORD client factory must not be null.");
        }
        this.clientFactory = clientFactory;
    }

    @Override
    public PROTOCOL protocol() {
        return PROTOCOL.SWORDv2;
    }

    /**
     * Hints <em>must</em> carry:
     * <ul>
     *     <li>Service document URL</li>
     *     <li>Username and pass for retrieving service doc</li>
     * </ul>
     * Hints may carry:
     * <ul>
     *     <li>on-behalf-of user</li>
     * </ul>
     *
     * @param hints transport hints used to configure the transport session
     * @return a SWORDv2 transport session, ready to be used
     */
    @Override
    public Sword2TransportSession open(Map<String, String> hints) {
        SWORDClient client = clientFactory.newInstance(hints);
        String serviceDocUrl = getServiceDocUrl(hints);

        if (!AUTHMODE.userpass.name().equals(hints.get(TRANSPORT_AUTHMODE))) {
            throw new IllegalArgumentException("This transport only supports AUTHMODE " + AUTHMODE.userpass.name() +
                                               " (was: '" + hints.get(TRANSPORT_AUTHMODE) + "'");
        }

        if (hints.get(TRANSPORT_USERNAME) == null || hints.get(TRANSPORT_USERNAME).trim().length() == 0) {
            throw new IllegalArgumentException(String.format(MISSING_REQUIRED_HINT, TRANSPORT_USERNAME));
        }

        if (hints.get(TRANSPORT_PASSWORD) == null || hints.get(TRANSPORT_PASSWORD).trim().length() == 0) {
            throw new IllegalArgumentException(String.format(MISSING_REQUIRED_HINT, TRANSPORT_PASSWORD));
        }

        ServiceDocument serviceDocument = null;
        AuthCredentials authCreds = null;
        try {
            if (hints.containsKey(Sword2TransportHints.SWORD_ON_BEHALF_OF_USER) &&
                (hints.get(Sword2TransportHints.SWORD_ON_BEHALF_OF_USER) != null) &&
                (hints.get(Sword2TransportHints.SWORD_ON_BEHALF_OF_USER).trim().length() > 0)) {
                authCreds = new AuthCredentials(hints.get(TRANSPORT_USERNAME), hints.get(TRANSPORT_PASSWORD),
                                                hints.get(Sword2TransportHints.SWORD_ON_BEHALF_OF_USER));
            } else {
                authCreds = new AuthCredentials(hints.get(TRANSPORT_USERNAME), hints.get(TRANSPORT_PASSWORD));
            }

            serviceDocument = client.getServiceDocument(serviceDocUrl, authCreds);
        } catch (Exception e) {
            throw new RuntimeException("Error reading or parsing SWORD service document '" + serviceDocUrl + "'", e);
        }

        return new Sword2TransportSession(client, serviceDocument, authCreds);
    }

    /**
     * Obtains the SWORD Service Document URL from the supplied hints, or throws a {@code RuntimeException}.
     *
     * @param hints the transport configuration hints
     * @return the SWORD service document URL
     * @throws RuntimeException if the hints do not contain the service document url
     */
    private String getServiceDocUrl(Map<String, String> hints) {
        if (hints.get(SWORD_SERVICE_DOC_URL) == null || hints.get(SWORD_SERVICE_DOC_URL).trim().length() == 0) {
            throw new IllegalArgumentException(String.format(MISSING_REQUIRED_HINT, SWORD_SERVICE_DOC_URL));
        }

        return hints.get(SWORD_SERVICE_DOC_URL);
    }
}
