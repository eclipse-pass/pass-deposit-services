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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.env.Environment;

import java.io.IOException;

public class SpringEnvironmentDeserializer extends StringDeserializer {

    private Environment env;

    public SpringEnvironmentDeserializer(Environment env) {
        this.env = env;
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext ctx) throws IOException, JsonProcessingException {
        String result = super.deserialize(parser, ctx);
        return (result != null && !result.trim().equals("")) ? env.resolveRequiredPlaceholders(result) : result;

//        ObjectCodec codec = parser.getCodec();
//        JsonNode rootNode = codec.readTree(parser);
//
//        rootNode.iterator()
//                .forEachRemaining(node -> node.fieldNames()
//                        .forEachRemaining(field -> {
//                                    if (!node.get(field).isTextual()) {
//                                        return;
//                                    }
//
//                                    String fieldValue = node.get(field).textValue();
//
//                                    ((ObjectNode) node).put(field, env.resolveRequiredPlaceholders(fieldValue));
//                                }
//                            )
//                );
//
//
//
//
    }
}
