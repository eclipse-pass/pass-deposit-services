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
package org.dataconservancy.pass.deposit.messaging.support.swordv2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URI;

import org.apache.abdera.parser.Parser;
import org.apache.abdera.parser.stax.FOMParserFactory;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.Resource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomFeedStatusResolverTest {

    private Parser abdera;

    private ResourceResolver resolver;

    private AtomFeedStatusResolver underTest;

    private Resource resource;

    @Before
    public void setUp() throws Exception {
        abdera = new FOMParserFactory().getParser();
        resolver = mock(ResourceResolver.class);
        resource = mock(Resource.class);

        when(resolver.resolve(any(), any())).thenReturn(resource);
        InputStream swordStatement = this.getClass().getResourceAsStream(
            "/org/dataconservancy/pass/deposit/messaging/support/swordv2/sword-statement.xml");
        assertNotNull(swordStatement);
        when(resource.getInputStream()).thenReturn(swordStatement);

        underTest = new AtomFeedStatusResolver(abdera, resolver);
    }

    @Test
    public void parseOk() {
        URI status = underTest.resolve(URI.create("http://moo"), new RepositoryConfig());
        assertNotNull(status);
        assertEquals("http://dspace.org/state/inreview", status.toString());
    }
}