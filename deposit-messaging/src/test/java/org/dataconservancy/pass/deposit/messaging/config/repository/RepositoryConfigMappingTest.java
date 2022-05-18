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

import java.util.Collections;

import org.junit.Test;

public class RepositoryConfigMappingTest extends AbstractJacksonMappingTest {

    static final String SWORD_REPOSITORY_JSON = "" +
                                                "{\n" +
                                                "\n" +
                                                "    \"deposit-config\": {\n" +
                                                "\n" +
        /*
         * Empty elements aren't supported without additional mapping configuration:
         * com.fasterxml.jackson.databind.exc.InvalidDefinitionException: No serializer found for class org
         * .dataconservancy.pass.deposit.messaging.config.repository.DepositProcessing and no properties discovered
         * to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through
         * reference chain: org.dataconservancy.pass.deposit.messaging.config.repository
         * .RepositoryConfig["deposit-config"]->org.dataconservancy.pass.deposit.messaging.config.repository
         * .RepositoryDepositConfig["processing"])
         */
//            "      \"processing\": {\n" +
//            "\n" +
//            "      },\n" +
                                                "\n" +
                                                "      \"mapping\": {\n" +
                                                "        \"http://dspace.org/state/archived\": \"http://oapass" +
                                                ".org/status/deposit#accepted\",\n" +
                                                "        \"http://dspace.org/state/withdrawn\": \"http://oapass" +
                                                ".org/status/deposit#rejected\",\n" +
                                                "        \"default-mapping\": \"http://oapass" +
                                                ".org/status/deposit#submitted\"\n" +
                                                "      }\n" +
                                                "    },\n" +
                                                "\n" +
                                                "    \"assembler\": {\n" +
                                                "      \"specification\": \"http://purl" +
                                                ".org/net/sword/package/METSDSpaceSIP\"\n" +
                                                "    },\n" +
                                                "\n" +
                                                "    \"transport-config\": {\n" +
                                                "      \"auth-realms\": [\n" +
                                                "        {\n" +
                                                "          \"mech\": \"basic\",\n" +
                                                "          \"username\": \"user\",\n" +
                                                "          \"password\": \"pass\",\n" +
                                                "          \"url\": \"https://jscholarship.library.jhu.edu/\"\n" +
                                                "        },\n" +
                                                "        {\n" +
                                                "          \"mech\": \"basic\",\n" +
                                                "          \"username\": \"user\",\n" +
                                                "          \"password\": \"pass\",\n" +
                                                "          \"url\": \"https://dspace-prod.mse.jhu.edu:8080/\"\n" +
                                                "        }\n" +
                                                "      ],\n" +
                                                "\n" +
                                                "      \"protocol-binding\": {\n" +
                                                "        \"protocol\": \"SWORDv2\",\n" +
                                                "        \"username\": \"sworduser\",\n" +
                                                "        \"password\": \"swordpass\",\n" +
                                                "        \"server-fqdn\": \"${dspace.host}\",\n" +
                                                "        \"server-port\": \"${dspace.port}\",\n" +
                                                "        \"service-doc\": \"http://${dspace.host}:${dspace" +
                                                ".port}/swordv2/servicedocument\",\n" +
                                                "        \"default-collection\": \"http://${dspace.host}:${dspace" +
                                                ".port}/swordv2/collection/123456789/2\",\n" +
                                                "        \"on-behalf-of\": null,\n" +
                                                "        \"deposit-receipt\": true,\n" +
                                                "        \"user-agent\": \"pass-deposit/x.y.z\"\n" +
                                                "      }\n" +
                                                "    }\n" +
                                                "  }";

    @Test
    public void mapRepositoryConfigFromJsonRoundTrip() throws Exception {
        RepositoryConfig config = mapper.readValue(SWORD_REPOSITORY_JSON, RepositoryConfig.class);
        assertRoundTrip(config, RepositoryConfig.class);
    }

    @Test
    public void mapRepositoryConfigFromJavaRoundTrip() throws Exception {
        RepositoryConfig repoConfig = new RepositoryConfig();
        TransportConfig tsConfig = new TransportConfig();
        SwordV2Binding swordV2Binding = new SwordV2Binding();
        BasicAuthRealm realm = new BasicAuthRealm();
        RepositoryDepositConfig repositoryDepositConfig = new RepositoryDepositConfig();
        StatusMapping statusMapping = new StatusMapping();

        realm.setBaseUrl("http://repository.org/");
        realm.setRealmName("Repository");
        realm.setUsername("user");
        realm.setPassword("pass");

        statusMapping.addStatusEntry("http://dspace.org/state/archived", "http://oapass.org/status/deposit#accepted");
        statusMapping.addStatusEntry("http://dspace.org/state/withdrawn", "http://oapass.org/status/deposit#rejected");
        statusMapping.setDefaultMapping("http://oapass.org/status/deposit#submitted");
        repositoryDepositConfig.setStatusMapping(statusMapping);

        swordV2Binding.setDefaultCollectionUrl("http://repository.org/swordv2/collection/1");
        swordV2Binding.setDepositReceipt(true);
        swordV2Binding.setOnBehalfOf("submitter");
        swordV2Binding.setServiceDocUrl("http://repository.org/swordv2/servicedoc");
        swordV2Binding.setUserAgent("sword-user-agent");
        swordV2Binding.setUsername("sworduser");
        swordV2Binding.setPassword("swordpass");

        tsConfig.setAuthRealms(Collections.singletonList(realm));
        tsConfig.setProtocolBinding(swordV2Binding);

        repoConfig.setRepositoryDepositConfig(repositoryDepositConfig);
        repoConfig.setTransportConfig(tsConfig);
        repoConfig.setRepositoryKey("J10P");

        assertRoundTrip(repoConfig, RepositoryConfig.class);
    }
}