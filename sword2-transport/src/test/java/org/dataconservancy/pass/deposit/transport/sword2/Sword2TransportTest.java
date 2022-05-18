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

import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_AUTHMODE;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_USERNAME;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_COLLECTION_URL;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_ON_BEHALF_OF_USER;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_SERVICE_DOC_URL;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.dataconservancy.pass.deposit.transport.Transport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.swordapp.client.AuthCredentials;
import org.swordapp.client.ProtocolViolationException;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDClientException;
import org.swordapp.client.ServiceDocument;

public class Sword2TransportTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final String SERVICE_DOC_URL = "http://localhost:8080/swordv2/servicedocument";

    private static final String COLLECTION_URL = "http://localhost:8080/swordv2/collection/1";

    private static final String USERNAME = "sworduser";

    private static final String PASSWORD = "swordpassword";

    private static final String ON_BEHALF_OF = "another_user";

    private static final Map<String, String> TRANSPORT_HINTS = Collections.unmodifiableMap(
        new HashMap<String, String>() {
            {
                put(SWORD_COLLECTION_URL, COLLECTION_URL);
                put(SWORD_ON_BEHALF_OF_USER, ON_BEHALF_OF);
                put(SWORD_SERVICE_DOC_URL, SERVICE_DOC_URL);
                put(TRANSPORT_AUTHMODE, Transport.AUTHMODE.userpass.name());
                put(TRANSPORT_USERNAME, USERNAME);
                put(TRANSPORT_PASSWORD, PASSWORD);
                put(TRANSPORT_PROTOCOL, Transport.PROTOCOL.SWORDv2.name());
            }
        });

    private SWORDClient swordClient;

    private Sword2Transport underTest;

    @Before
    public void setUp() throws Exception {
        ServiceDocument serviceDocument = mock(ServiceDocument.class);
        swordClient = mock(SWORDClient.class);
        Sword2ClientFactory clientFactory = mock(Sword2ClientFactory.class);

        when(swordClient.getServiceDocument(any(), any())).thenReturn(serviceDocument);
        when(clientFactory.newInstance(anyMap())).thenReturn(swordClient);

        underTest = new Sword2Transport(clientFactory);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullFactory() throws Exception {
        new Sword2Transport(null);
    }

    @Test
    public void testOpenMissingAuthUsernameKey() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(String.format(Sword2Transport.MISSING_REQUIRED_HINT, TRANSPORT_USERNAME));
        underTest.open(removeKey(TRANSPORT_USERNAME, TRANSPORT_HINTS));
    }

    @Test
    public void testOpenMissingAuthPasswordKey() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(String.format(Sword2Transport.MISSING_REQUIRED_HINT, TRANSPORT_PASSWORD));
        underTest.open(removeKey(TRANSPORT_PASSWORD, TRANSPORT_HINTS));
    }

    @Test
    public void testOpenMissingAuthModeKey() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("only supports AUTHMODE");
        underTest.open(removeKey(TRANSPORT_AUTHMODE, TRANSPORT_HINTS));
    }

    @Test
    public void testOpenUnsupportedAuthMode() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("only supports AUTHMODE");
        underTest.open(replaceKey(TRANSPORT_AUTHMODE, "fooAuthMode", TRANSPORT_HINTS));
    }

    @Test
    public void testOpenAuthenticationCredentials() throws Exception {
        Sword2TransportSession session = underTest.open(TRANSPORT_HINTS);
        assertNotNull(session.getAuthCreds());
        AuthCredentials authCredentials = session.getAuthCreds();

        assertEquals(USERNAME, authCredentials.getUsername());
        assertEquals(PASSWORD, authCredentials.getPassword());
        assertEquals(ON_BEHALF_OF, authCredentials.getOnBehalfOf());
    }

    @Test
    public void testGetServiceDocumentThrowsRuntimeException() throws Exception {
        expectedException.expect(RuntimeException.class);
        expectedException.expectCause(isA(RuntimeException.class));
        expectedException.expectMessage("http");

        when(swordClient.getServiceDocument(any(), any())).thenThrow(new RuntimeException());

        underTest.open(TRANSPORT_HINTS);
    }

    @Test
    public void testGetServiceDocumentThrowsSWORDClientException() throws Exception {
        expectedException.expect(RuntimeException.class);
        expectedException.expectCause(isA(SWORDClientException.class));

        when(swordClient.getServiceDocument(any(), any())).thenThrow(mock(SWORDClientException.class));

        underTest.open(TRANSPORT_HINTS);
    }

    @Test
    public void testGetServiceDocumentThrowsProtocolViolationException() throws Exception {
        expectedException.expect(RuntimeException.class);
        expectedException.expectCause(isA(ProtocolViolationException.class));

        when(swordClient.getServiceDocument(any(), any())).thenThrow(mock(ProtocolViolationException.class));

        underTest.open(TRANSPORT_HINTS);
    }

    /**
     * Returns a new map that omits the supplied {@code key} from {@code map}.
     *
     * @param key a key that may occur in {@code map}, to be omitted from the returned map.
     * @param map a map that may contain the supplied {@code key}
     * @return a new {@code Map} that does not contain {@code key}
     */
    private static Map<String, String> removeKey(String key, Map<String, String> map) {
        return map.entrySet()
                  .stream()
                  .filter((entry) -> !entry.getKey().equals(key))
                  .collect(
                      Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns a new map that replaces the supplied {@code key} in {@code map}.  If the {@code key} does not exist
     * in {@code map}, it is added in the returned {@code Map}.
     *
     * @param key      a key that may occur in {@code map}, whose value is replaced in the returned {@code Map}
     * @param newValue the new value of {@code key}
     * @param map      a map that may contain the supplied {@code key}
     * @return a new {@code Map} that contains {@code key} mapped to {@code newValue}
     */
    private static Map<String, String> replaceKey(String key, String newValue, Map<String, String> map) {
        Map<String, String> result = removeKey(key, map);
        result.put(key, newValue);

        return result;
    }
}