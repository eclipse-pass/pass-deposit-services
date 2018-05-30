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

package org.dataconservancy.pass.deposit.messaging.model;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.PassJsonAdapter;
import org.dataconservancy.pass.deposit.messaging.support.JsonParser;
import org.dataconservancy.pass.model.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Consults the Fedora repository to enumerate available {@code Repository}s.
 */
@Component
public class FcrepoRepositoriesSource implements RepositoriesSource {

    private static final Logger LOG = LoggerFactory.getLogger(FcrepoRepositoriesSource.class);

    private OkHttpClient okHttpClient;

    private PassJsonAdapter jsonAdapter;

    @Value("${pass.fedora.baseurl}")
    private String fedoraBaseUrl;

    private JsonParser jsonParser;

    private PassClient passClient;

    @Autowired
    public FcrepoRepositoriesSource(OkHttpClient okHttpClient, PassJsonAdapter jsonAdapter, JsonParser jsonParser, PassClient passClient) {
        this.okHttpClient = okHttpClient;
        this.jsonAdapter = jsonAdapter;
        this.jsonParser = jsonParser;
        this.passClient = passClient;
    }

    @Override
    public Collection<Repository> repositories() {
        Request.Builder builder = new Request.Builder();
        Request req = builder.get().url(String.format("%srepositories", fedoraBaseUrl))
                .addHeader("Prefer", "return=representation; include=\"http://fedora.info/definitions/v4/repository#EmbedResources\"")
                .build();
        byte[] json = null;
        try (Response res = okHttpClient.newCall(req).execute()) {
            ResponseBody body = res.body();
            if (res.code() < 200 || res.code() > 299) {
                throw new RuntimeException("Unexpected response code " + res.code() + " from " + req.method() + " " + req.url());
            }
            if (body != null) {
                json = body.bytes();
                LOG.trace(">>>> Response from {} {} ({}):\n{}", req.method(), req.url(), res.code(), new String(json));
            } else {
                throw new NullPointerException("Null ResponseBody for request " + req);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error executing request " + req + ": " + e.getMessage(), e);
        }

        return jsonParser.parseRepositoryUris(json)
                .stream()
                .map(repoUri -> passClient.readResource(URI.create(repoUri), Repository.class))
                .collect(Collectors.toList());
    }
}
