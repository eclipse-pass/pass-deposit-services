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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for copying Elastic Search indexes.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class IndexUtil {

    private static final Logger LOG = LoggerFactory.getLogger(IndexUtil.class);

    private static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

    private static final String ALIAS_ENDPOINT = "/_aliases";

    private static final String REINDEX_ENDPOINT = "/_reindex";

    private static final String REINDEX = "" +
                                          "{\n" +
                                          "  \"source\": {\n" +
                                          "     \"index\": \"%s\"\n" +
                                          "  },\n" +
                                          "  \"dest\": {\n" +
                                          "     \"index\": \"%s\"\n" +
                                          "  }\n" +
                                          "}\n";

    private static final String ALIAS = "" +
                                        "{\n" +
                                        "  \"actions\": [\n" +
                                        "    { \"remove_index\": { \"index\": \"%s\" } },\n" +
                                        "    { \"add\": { \"index\": \"%s\", \"alias\": \"%s\" } }\n" +
                                        "  ]\n" +
                                        "}\n";

    private ObjectMapper objectMapper;

    private OkHttpClient okHttp;

    public IndexUtil(ObjectMapper objectMapper, OkHttpClient okHttp) {
        this.objectMapper = objectMapper;
        this.okHttp = okHttp;
    }

    /**
     * Copies an index from one location to another, the destination should not exist.  The source and destination
     * URLs must point to the same host/port.  The destination will use the same configuration as the source.
     *
     * @param source
     * @param dest
     * @throws IOException
     */
    public void copyIndex(URL source, URL dest) throws IOException {
        copyIndex(source, dest, Function.identity());
    }

    /**
     * Copies an index from one location to another, the destination should not exist.  The source and destination
     * URLs must point to the same host/port.  The supplied function will be applied to the source index configuration
     * prior to being used as the destination index configuration.
     *
     * @param source
     * @param dest
     * @param configTransform
     * @throws IOException
     */
    public void copyIndex(URL source, URL dest, Function<JsonNode, JsonNode> configTransform) throws IOException {
        if (!source.getHost().equals(dest.getHost()) || source.getPort() != dest.getPort()) {
            throw new RuntimeException("Source url and destination url must be on the same server " +
                                       "(source was: " + source + " dest was: " + dest + ")");
        }

        verifyIndexUrl(source);
        verifyIndexExists(source);

        createIndex(dest, configTransform.
            andThen(jsonNode -> {
                JsonNode index = jsonNode.findValue("index");
                if (index.has("provided_name")) {
                    ((ObjectNode) index).remove("provided_name");
                }
                if (index.has("creation_date")) {
                    ((ObjectNode) index).remove("creation_date");
                }
                if (index.has("uuid")) {
                    ((ObjectNode) index).remove("uuid");
                }
                if (index.has("version")) {
                    ((ObjectNode) index).remove("version");
                }
                return jsonNode;
            }).
            apply(getIndexConfig(source)));

        String sourceIndexName = indexName(source);
        String destIndexName = indexName(dest);
        LOG.debug("Copying {} to {}", sourceIndexName, destIndexName);
        reindex(source, sourceIndexName, destIndexName);

        verifyIndexExists(dest);

        // Remove the original index, and alias the new index to the old name.

        URL aliasCommand = new URL(source, ALIAS_ENDPOINT);

        String aliasRequestBody = String.format(ALIAS, sourceIndexName, destIndexName, sourceIndexName);
        LOG.trace("POSTing to {}: \n{}", aliasCommand, aliasRequestBody);
        try (Response response =
                 okHttp.newCall(new Request.Builder().url(aliasCommand)
                                                     .post(RequestBody.create(APPLICATION_JSON, aliasRequestBody))
                                                     .build()).execute()) {
            assertEquals("Re-aliasing (" + aliasCommand + ") must return a 200 (was: " + response.code() +
                         ", " + response.body().string() + ")", 200, response.code());
        }
    }

    /**
     * Creates an index by issuing a PUT to {@code indexUrl} with the {@code config} as the body.
     *
     * @param indexUrl
     * @param config
     * @throws IOException
     */
    protected void createIndex(URL indexUrl, JsonNode config) throws IOException {
        LOG.trace("Creating index at {} with config {}", indexUrl, config);
        byte[] body = objectMapper.writeValueAsBytes(config);
        try (Response response =
                 okHttp.newCall(new Request.Builder().url(indexUrl)
                                                     .put(RequestBody.create(APPLICATION_JSON, body)).build())
                       .execute()) {
            assertEquals("Index creation must return a 200 (was: " + response.code() + ")",
                         200, response.code());
        }
    }

    /**
     * Re-indexes the source index to the destination index by issuing a POST to the base URL (derived from the {@code
     * indexUrl}) with the reindex command.
     *
     * @param indexUrl
     * @param sourceIndexName
     * @param destIndexName
     * @throws IOException
     */
    protected void reindex(URL indexUrl, String sourceIndexName, String destIndexName) throws IOException {
        URL reindexCommand = new URL(indexUrl, REINDEX_ENDPOINT);
        assertNotEquals("Source index name must not equal destination index name.",
                        sourceIndexName, destIndexName);
        String body = String.format(REINDEX, sourceIndexName, destIndexName);
        try (Response response =
                 okHttp.newCall(new Request.Builder().url(reindexCommand)
                                                     .post(RequestBody.create(APPLICATION_JSON, body)).build())
                       .execute()) {
            assertEquals("Re-indexing to " + reindexCommand + " must return a 200 (was: " + response.code() +
                         ", " + response.body().string() + ")", 200, response.code());
        }
    }

    private void verifyIndexUrl(URL indexUrl) {
        assertTrue("Index URL (" + indexUrl + ") must end with a slash.", indexUrl.toString().endsWith("/"));
    }

    private void verifyIndexExists(URL indexUrl) throws IOException {
        try (Response response = okHttp.newCall(new Request.Builder().url(indexUrl).build()).execute()) {
            assertEquals("Index URL (" + indexUrl + ") must return a 200 (was: " + response.code() + ")",
                         200, response.code());
        }
    }

    /**
     * Retrieves the configuration of an index by issuing a GET to the {@code indexUrl} and returning the response
     * containing the configuration as a JsonNode.
     *
     * @param indexUrl
     * @return
     * @throws IOException
     */
    private JsonNode getIndexConfig(URL indexUrl) throws IOException {
        try (Response response = okHttp.newCall(new Request.Builder().url(indexUrl).build()).execute()) {
            assertEquals("Index URL must return a 200 (was: " + response.code() + ")",
                         200, response.code());
            JsonNode config = objectMapper.readTree(response.body().byteStream());
            LOG.trace("Retrieved config from {}: {}", indexUrl, config);
            return config;
        }
    }

    /**
     * Given a URL to an ElasticSearch index, determines the name of the index from the last path component.
     *
     * @param source url to an ES index, e.g. http://pass.local:9200/pass or http://pass.local:9200/pass/
     * @return
     */
    protected String indexName(URL source) {
        String path = source.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path.substring(path.lastIndexOf("/") + 1);
    }

}
