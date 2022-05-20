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
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AssemblerConfigMappingTest extends AbstractJacksonMappingTest {

    private static final String ASSEMBLER_CONFIG = "" +
                                                   "{\n" +
                                                   "      \"specification\": \"http://purl" +
                                                   ".org/net/sword/package/METSDSpaceSIP\",\n" +
                                                   "      \"options\": {\n" +
                                                   "        \"archive\": \"ZIP\",\n" +
                                                   "        \"compression\": \"NONE\",\n" +
                                                   "        \"algorithms\": [\n" +
                                                   "          \"sha512\",\n" +
                                                   "          \"md5\"\n" +
                                                   "        ]\n" +
                                                   "      }\n" +
                                                   "    }";

    @Test
    public void mapAssemblerConfig() throws IOException {
        AssemblerConfig config = mapper.readValue(ASSEMBLER_CONFIG, AssemblerConfig.class);

        assertEquals("http://purl.org/net/sword/package/METSDSpaceSIP", config.getSpec());

        AssemblerOptions options = config.getOptions();
        assertEquals("ZIP", options.getArchive());
        assertEquals("NONE", options.getCompression());
        assertEquals(2, options.getAlgorithms().size());
        assertTrue(options.getAlgorithms().contains("sha512"));
        assertTrue(options.getAlgorithms().contains("md5"));
    }
}