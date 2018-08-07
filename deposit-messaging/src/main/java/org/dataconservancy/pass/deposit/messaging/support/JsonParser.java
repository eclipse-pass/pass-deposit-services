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
package org.dataconservancy.pass.deposit.messaging.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;

import static org.dataconservancy.pass.deposit.messaging.support.Constants.Json.ETAG;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.Json.JSON_AT_ID;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.Json.JSON_ID;
import static org.dataconservancy.pass.deposit.messaging.support.Constants.LdpRel.LDP_CONTAINS;

/**
 * Parses values of well-known keys from the JSON that may be received from the Fedora repository.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class JsonParser {

    private ObjectMapper objectMapper;

    @Autowired
    public JsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the {@code id} of a JSON representation of a JMS message emitted by Fedora.
     *
     * @param json JSON payload of a JMS message emitted by Fedora that may contain an {@code id}
     * @return the value of the {@code id}, or {@code null}
     * @throws RuntimeException if {@code json} cannot be parsed
     */
    public String parseId(byte[] json) {
        JsonNode node = toJsonNode(json, objectMapper);
        if (node == null) {
            throw new RuntimeException("Unable to resolve the following to JSON:\n" + new String(json));
        }
        JsonNode value = node.findValue(JSON_ID);
        return (value == null) ? null : value.asText();
    }

    public String parseEtag(byte[] json) {
        JsonNode node = toJsonNode(json, objectMapper);
        if (node == null) {
            throw new RuntimeException("Unable to resolve the following to JSON:\n" + new String(json));
        }
        JsonNode value = node.findValue(ETAG);
        return (value == null) ? null : value.asText();
    }

    /**
     * Parses the {@link Constants.LdpRel#LDP_CONTAINS} relationship from JSON representations of LDP containers in Fedora.
     *
     * @param json JSON representation of a Fedora resource
     * @return the URIs that are the object of the {@code LDP_CONTAINS} relationship, or an empty {@code Collection}
     * @throws RuntimeException if {@code json} cannot be parsed
     */
    public Collection<String> parseRepositoryUris(byte[] json) {
        JsonNode node = toJsonNode(json, objectMapper);
        if (node == null) {
            throw new RuntimeException("Unable to resolve the following to JSON:\n" + new String(json));
        }
        JsonNode contains = node.findValue(LDP_CONTAINS);
        if (contains == null) {
            throw new RuntimeException("JSON is missing '" + LDP_CONTAINS + "': unable to resolve repository URIs from " +
                    "the following JSON:\n" + new String(json));
        }
        ArrayList<String> repoUris = new ArrayList<>();
        contains.iterator().forEachRemaining(n -> repoUris.add(n.findValue(JSON_AT_ID).asText()));

        return repoUris;
    }

    /**
     * Parses the supplied bytes into a {@link JsonNode} using the supplied {@link ObjectMapper}.
     *
     * @param json the raw json
     * @param objectMapper the Jackson {@code ObjectMapper}, configured to resolve JSON
     * @return the {@code JsonNode}
     */
    private static JsonNode toJsonNode(byte[] json, ObjectMapper objectMapper) {
        JsonNode node = null;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Unable to read JSON byte array: " + e.getMessage(), e);
        }
        return node;
    }

}
