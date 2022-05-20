/*
 * Copyright 2019 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.integration.shared;

import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.COMPLETE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.client.PassJsonAdapter;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the Elastic Search Query DSL to perform a search for RepositoryCopies with a copy status of
 * "complete" and an access URL that begins with "file:/packages/*".  As it is currently implemented, the
 * PassClient cannot perform this kind of query (wildcard searches are not supported).
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class RepositoryCopyPackageQuery {

    private RepositoryCopyPackageQuery() {
    }

    private static Logger LOG = LoggerFactory.getLogger(RepositoryCopyPackageQuery.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String QUERY = "{\n" +
                                        "  \"size\": \"10000\",\n" +
                                        "  \"query\": {\n" +
                                        "    \"bool\" : {\n" +
                                        "      \"must\": [\n" +
                                        "        { \"term\" : { \"@type\" :  \"RepositoryCopy\" } },\n" +
                                        "        { \"term\" : { \"copyStatus\" : \"" + COMPLETE.name()
                                                                                               .toLowerCase() + "\" }" +
                                        " },\n" +
                                        "        { \"wildcard\": {\"accessUrl\": \"file:/packages/*\" } }\n" +
                                        "      ]\n" +
                                        "    }\n" +
                                        "  }\n" +
                                        "}";

    private static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

    static Collection<RepositoryCopy> execute(OkHttpClient client, PassJsonAdapter responseAdapter,
                                              String searchEndpoint) {
        try {
            return parseResponse(client.newCall(packageQuery(searchEndpoint)).execute(), responseAdapter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Request packageQuery(String searchEndpoint) {
        return new Request.Builder()
            .url(searchEndpoint)
            .post(RequestBody.create(APPLICATION_JSON, QUERY))
            .build();
    }

    private static Collection<RepositoryCopy> parseResponse(Response res, PassJsonAdapter adapter) {
        if (res.body() == null) {
            throw new RuntimeException("Error performing ES search: response code " + res.code() +
                                       " (response body was null)");
        }

        JsonNode rootNode;

        try (InputStream in = res.body().byteStream()) {
            if (res.code() != 200) {
                throw new RuntimeException("Error performing ES search: response code " + res.code() + ", body:\n" +
                                           IOUtils.toString(in, StandardCharsets.UTF_8));
            }
            rootNode = OBJECT_MAPPER.readTree(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Set<RepositoryCopy> results = new HashSet<>();
        JsonParser parser = rootNode.traverse(OBJECT_MAPPER);
        JsonToken currToken = null;

        try {
            while ((currToken = parser.nextToken()) != null) {
                LOG.trace("Parsing token {}", currToken);
                if ("FIELD_NAME".equals(currToken.name()) && "_source".equals(parser.getText())) {
                    TreeNode node = parser.readValueAsTree().get("_source");
                    String asString = node.toString();
                    LOG.trace("Adding _source: \n{}", asString);
                    results.add(adapter.toModel(asString.getBytes(StandardCharsets.UTF_8), RepositoryCopy.class));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return results;
    }

}
