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

package org.dataconservancy.pass.deposit.messaging.config.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TransportConfigMappingTest extends AbstractJacksonMappingTest {

    private static final String MINIMAL_SWORD_TRANSPORT_CONFIG = "" +
                                                                 "{\n" +
                                                                 "      \"protocol-binding\": {\n" +
                                                                 "        \"protocol\": \"SWORDv2\"\n" +
                                                                 "      }\n" +
                                                                 "\n" +
                                                                 "    }";

    private static final String MINIMAL_FTP_TRANSPORT_CONFIG = "" +
                                                               "{\n" +
                                                               "      \"protocol-binding\": {\n" +
                                                               "        \"protocol\": \"ftp\"\n" +
                                                               "      }\n" +
                                                               "\n" +
                                                               "    }";

    private static final String MULTIPLE_REALMS_TRANSPORT_CONFIG = "" +
                                                                   "{\n" +
                                                                   "\n" +
                                                                   "      \"auth-realms\": [\n" +
                                                                   "        {\n" +
                                                                   "          \"mech\": \"basic\",\n" +
                                                                   "          \"username\": \"user\",\n" +
                                                                   "          \"password\": \"pass\",\n" +
                                                                   "          \"url\": \"https://jscholarship.library" +
                                                                   ".jhu.edu/\"\n" +
                                                                   "        },\n" +
                                                                   "        {\n" +
                                                                   "          \"mech\": \"basic\",\n" +
                                                                   "          \"username\": \"user\",\n" +
                                                                   "          \"password\": \"pass\",\n" +
                                                                   "          \"url\": \"https://dspace-prod.mse.jhu" +
                                                                   ".edu:8080/\"\n" +
                                                                   "        }\n" +
                                                                   "      ]\n" +
                                                                   "}\n";

    private static final String TRANSPORT_CONFIG_JSON = "" +
                                                        "{\n" +
                                                        "\n" +
                                                        "      \"auth-realms\": [\n" +
                                                        "        {\n" +
                                                        "          \"mech\": \"basic\",\n" +
                                                        "          \"username\": \"user\",\n" +
                                                        "          \"password\": \"pass\",\n" +
                                                        "          \"url\": \"https://jscholarship.library.jhu" +
                                                        ".edu/\"\n" +
                                                        "        },\n" +
                                                        "        {\n" +
                                                        "          \"mech\": \"basic\",\n" +
                                                        "          \"username\": \"user\",\n" +
                                                        "          \"password\": \"pass\",\n" +
                                                        "          \"url\": \"https://dspace-prod.mse.jhu" +
                                                        ".edu:8080/\"\n" +
                                                        "        }\n" +
                                                        "      ],\n" +
                                                        "\n" +
                                                        "      \"protocol-binding\": {\n" +
                                                        "        \"protocol\": \"SWORDv2\",\n" +
                                                        "        \"username\": \"sworduser\",\n" +
                                                        "        \"password\": \"swordpass\",\n" +
                                                        "        \"service-doc\": \"http://${dspace.host}:${dspace" +
                                                        ".port}/swordv2/servicedocument\",\n" +
                                                        "        \"default-collection\": \"http://${dspace" +
                                                        ".host}:${dspace.port}/swordv2/collection/123456789/2\",\n" +
                                                        "        \"on-behalf-of\": null,\n" +
                                                        "        \"deposit-receipt\": true,\n" +
                                                        "        \"user-agent\": \"pass-deposit/x.y.z\",\n" +
                                                        "        \"collection-hints\": {\n" +
                                                        "          \"covid\": \"${dspace" +
                                                        ".baseuri}/swordv2/collection/${dspace.covid.handle}\",\n" +
                                                        "          \"nobel\": \"${dspace" +
                                                        ".baseuri}/swordv2/collection/${dspace.nobel.handle}\"\n" +
                                                        "        }\n" +
                                                        "      }\n" +
                                                        "    }";

    @Test
    public void mapMinimalSwordTransportConfig() throws IOException {
        TransportConfig config = mapper.readValue(MINIMAL_SWORD_TRANSPORT_CONFIG, TransportConfig.class);

        assertNull(config.getAuthRealms());
        assertNotNull(config.getProtocolBinding());
        assertTrue(config.getProtocolBinding() instanceof SwordV2Binding);

        SwordV2Binding binding = (SwordV2Binding) config.getProtocolBinding();

        assertEquals(SwordV2Binding.PROTO, binding.getProtocol());
    }

    @Test
    public void mapMinimalSwordTransportConfigFromJavaRoundTrip() throws IOException {
        TransportConfig config = new TransportConfig();
        config.setProtocolBinding(new SwordV2Binding());

        assertRoundTrip(config, TransportConfig.class);
    }

    @Test
    public void mapMinimalSwordTransportConfigFromJsonRoundTrip() throws IOException {
        TransportConfig config = mapper.readValue(MINIMAL_SWORD_TRANSPORT_CONFIG, TransportConfig.class);

        assertRoundTrip(config, TransportConfig.class);
    }

    @Test
    public void mapMinimalFtpTransportConfig() throws IOException {
        TransportConfig config = mapper.readValue(MINIMAL_FTP_TRANSPORT_CONFIG, TransportConfig.class);

        assertNull(config.getAuthRealms());
        assertNotNull(config.getProtocolBinding());
        assertTrue(config.getProtocolBinding() instanceof FtpBinding);

        FtpBinding binding = (FtpBinding) config.getProtocolBinding();

        assertEquals(FtpBinding.PROTO, binding.getProtocol());
    }

    @Test
    public void mapTransportConfigWithRealms() throws IOException {
        TransportConfig config = mapper.readValue(MULTIPLE_REALMS_TRANSPORT_CONFIG, TransportConfig.class);

        assertNull(config.getProtocolBinding());
        assertNotNull(config.getAuthRealms());
        assertTrue(config.getAuthRealms().size() > 1);

        config.getAuthRealms().forEach(realm -> assertTrue(realm instanceof BasicAuthRealm));
    }

    @Test
    public void mapTransportConfigFromJsonRoundTrip() throws IOException {
        TransportConfig config = mapper.readValue(TRANSPORT_CONFIG_JSON, TransportConfig.class);

        assertRoundTrip(config, TransportConfig.class);
    }

    @Test
    public void mapTransportConfigCollectionHints() throws IOException {
        TransportConfig config = mapper.readValue(TRANSPORT_CONFIG_JSON, TransportConfig.class);

        assertRoundTrip(config, TransportConfig.class);
        Map<String, String> hints = ((SwordV2Binding) config.getProtocolBinding()).getCollectionHints();
        assertTrue(hints.containsKey("covid"));
        assertTrue(hints.containsKey("nobel"));
        assertEquals("${dspace.baseuri}/swordv2/collection/${dspace.covid.handle}", hints.get("covid"));
        assertEquals("${dspace.baseuri}/swordv2/collection/${dspace.nobel.handle}", hints.get("nobel"));
    }

    @Test
    public void mapTransportConfigFromJavaRoundTrip() throws IOException {
        TransportConfig config = new TransportConfig();
        SwordV2Binding swordV2Binding = new SwordV2Binding();
        BasicAuthRealm realm1 = new BasicAuthRealm();
        BasicAuthRealm realm2 = new BasicAuthRealm();

        config.setProtocolBinding(swordV2Binding);
        config.setAuthRealms(Arrays.asList(realm1, realm2));

        swordV2Binding.setUsername("username");
        swordV2Binding.setPassword("password");
        swordV2Binding.setUserAgent("user-agent string");
        swordV2Binding.setServiceDocUrl("http://example.org/servicedoc");
        swordV2Binding.setDefaultCollectionUrl("http://example.org/collection/1");
        swordV2Binding.setDepositReceipt(true);
        swordV2Binding.setOnBehalfOf("moouser");

        String covid = "https://jscholarship.library.jhu.edu/handle/1774.2/58585";
        String nobel = "https://jscholarship.library.jhu.edu/handle/1774.2/33532";
        Map<String, String> hints = new HashMap<String, String>() {
            {
                put("covid", covid);
                put("nobel", nobel);
            }
        };
        swordV2Binding.setCollectionHints(hints);

        realm1.setRealmName("Realm 1");
        realm1.setUsername("foo");
        realm1.setPassword("bar");
        realm1.setBaseUrl("http://example.org/realm/1");

        realm2.setRealmName("Realm 2");
        realm2.setUsername("biz");
        realm2.setPassword("baz");
        realm2.setBaseUrl("http://example.org/realm/2");

        assertRoundTrip(config, TransportConfig.class);
    }
}
