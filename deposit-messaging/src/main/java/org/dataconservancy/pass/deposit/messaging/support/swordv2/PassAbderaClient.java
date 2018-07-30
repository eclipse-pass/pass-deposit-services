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

package org.dataconservancy.pass.deposit.messaging.support.swordv2;

import org.apache.abdera.Abdera;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.abdera.protocol.client.cache.Cache;
import org.apache.commons.httpclient.HttpClient;

public class PassAbderaClient extends AbderaClient {

    private String repositoryKey;

    public PassAbderaClient() {

    }

    public PassAbderaClient(String useragent) {
        super(useragent);
    }

    public PassAbderaClient(Abdera abdera, String useragent) {
        super(abdera, useragent);
    }

    public PassAbderaClient(Abdera abdera, String useragent, Cache cache) {
        super(abdera, useragent, cache);
    }

    public PassAbderaClient(HttpClient client) {
        super(client);
    }

    public PassAbderaClient(Abdera abdera, HttpClient client) {
        super(abdera, client);
    }

    public PassAbderaClient(Abdera abdera, HttpClient client, Cache cache) {
        super(abdera, client, cache);
    }

    public PassAbderaClient(Abdera abdera) {
        super(abdera);
    }

    public PassAbderaClient(Abdera abdera, Cache cache) {
        super(abdera, cache);
    }

    public String getRepositoryKey() {
        return repositoryKey;
    }

    public void setRepositoryKey(String repositoryKey) {
        this.repositoryKey = repositoryKey;
    }
}
