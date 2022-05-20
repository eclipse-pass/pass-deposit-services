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

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;

public abstract class AbstractJacksonMappingTest {

    protected ObjectMapper mapper;

    @Before
    public void setUpObjectMapper() throws Exception {
        mapper = new ObjectMapper();
    }

    protected <T> void assertRoundTrip(T instance, Class<T> type) throws IOException {
        assertEquals(instance, mapper.readValue(mapper.writeValueAsString(instance), type));
    }
}
