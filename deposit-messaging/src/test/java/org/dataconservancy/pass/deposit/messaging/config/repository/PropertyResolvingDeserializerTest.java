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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PropertyResolvingDeserializerTest extends RepositoryConfigMappingTest {

    @Autowired
    private Environment env;

    @Override
    public void setUpObjectMapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new SpringEnvironmentDeserializer(env));
        mapper.registerModule(module);
        this.mapper = mapper;
    }

    @Test
    public void noPropertyResolutionTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RepositoryConfig config = mapper.readValue(SWORD_REPOSITORY_JSON, RepositoryConfig.class);
        assertTrue(config.getTransportConfig().getProtocolBinding().getProtocol().equals(SwordV2Binding.PROTO));
        SwordV2Binding swordV2Binding = (SwordV2Binding) config.getTransportConfig().getProtocolBinding();
        assertTrue(swordV2Binding.getDefaultCollectionUrl().contains("${dspace.host}"));
    }

    @Test
    public void resolvePropertiesTest() throws Exception {
        RepositoryConfig config = mapper.readValue(SWORD_REPOSITORY_JSON, RepositoryConfig.class);
        assertTrue(config.getTransportConfig().getProtocolBinding().getProtocol().equals(SwordV2Binding.PROTO));
        SwordV2Binding swordV2Binding = (SwordV2Binding) config.getTransportConfig().getProtocolBinding();
        assertFalse(swordV2Binding.getDefaultCollectionUrl().contains("${dspace.host}"));
    }
}
