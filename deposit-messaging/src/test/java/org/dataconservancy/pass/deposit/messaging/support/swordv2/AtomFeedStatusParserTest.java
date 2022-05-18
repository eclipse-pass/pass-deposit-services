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

import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_ARCHIVED;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INREVIEW;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_WITHDRAWN;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.ARCHIVED_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.INPROGRESS_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.INREVIEW_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.MISSING_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.MULTIPLE_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.UNKNOWN_STATUS_RESOURCE;
import static org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources.WITHDRAWN_STATUS_RESOURCE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static resources.SharedResourceUtil.findStreamByName;
import static resources.SharedResourceUtil.findUriByName;

import java.io.InputStream;
import java.net.URI;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.http.ParseException;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.core.io.Resource;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomFeedStatusParserTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Parser abderaParser;

    private RepositoryConfig repositoryConfig;

    private AtomFeedStatusResolver underTest;

    private ResourceResolver resourceResolver;

    @Before
    public void setUp() throws Exception {
        abderaParser = mock(Parser.class);
        repositoryConfig = mock(RepositoryConfig.class);
        resourceResolver = mock(ResourceResolver.class);
        underTest = new AtomFeedStatusResolver(abderaParser, resourceResolver);
    }

    // Test cases
    //   - Deposit with malformed status ref (can't, cause method requires URI)
    //   - Deposit with status ref that doesn't exist (test of AbderaClient)
    //   - Deposit with status ref that times out (test of AbderaClient)
    //   - Deposit status is mapped to a non-terminal status (should be left alone)
    //   - Deposit status is mapped to null
    //   - Deposit with AbderaClient that throws an exception
    //   - Parse a Document<Feed> with missing or incorrect Category
    //   - Parse a Document<Feed> with correct Category but unknown value


    /**
     * An Atom Statement containing a <sword:state> of http://dspace.org/state/archived should be parsed
     *
     * @throws Exception
     */
    @Test
    public void mapArchived() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(ARCHIVED_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_ARCHIVED.asUri(), AtomUtil.parseSwordState(feed));
    }

    @Test
    public void mapInProgress() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(INPROGRESS_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_INPROGRESS.asUri(), AtomUtil.parseSwordState(feed));
    }

    @Test
    public void mapInReview() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(INREVIEW_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_INREVIEW.asUri(), AtomUtil.parseSwordState(feed));
    }

    @Test
    public void mapMissing() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(MISSING_STATUS_RESOURCE, AtomResources.class));
        assertEquals(null, AtomUtil.parseSwordState(feed));
    }

    @Test
    public void mapMultiple() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(MULTIPLE_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_ARCHIVED.asUri(), AtomUtil.parseSwordState(feed));
    }

    @Test
    public void mapUnknown() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(UNKNOWN_STATUS_RESOURCE, AtomResources.class));
        assertEquals(URI.create("http://dspace.org/state/moo"), AtomUtil.parseSwordState(feed));
    }

    @Test
    public void mapWithdrawn() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed(findStreamByName(WITHDRAWN_STATUS_RESOURCE, AtomResources.class));
        assertEquals(SWORD_STATE_WITHDRAWN.asUri(), AtomUtil.parseSwordState(feed));
    }

    @Test
    public void parseWithRuntimeException() throws Exception {
        URI uri = findUriByName(ARCHIVED_STATUS_RESOURCE, AtomResources.class);

        Resource resource = mock(Resource.class);
        when(resource.getInputStream()).thenReturn(mock(InputStream.class));

        RuntimeException expected = new RuntimeException("Expected exception.");
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Expected exception.");
        expectedException.expectMessage("AtomStatusParser-archived.xml");

        when(resourceResolver.resolve(eq(uri), any(RepositoryConfig.class))).thenReturn(resource);
        when(abderaParser.parse(any(InputStream.class))).thenThrow(expected);

        underTest.resolve(uri, repositoryConfig);
    }

    @Test
    public void parseWithParseException() throws Exception {
        URI uri = findUriByName(ARCHIVED_STATUS_RESOURCE, AtomResources.class);

        Resource resource = mock(Resource.class);
        when(resource.getInputStream()).thenReturn(mock(InputStream.class));

        ParseException expectedCause = new ParseException("Expected cause.");
        expectedException.expect(RuntimeException.class);
        expectedException.expectCause(is(expectedCause));
        expectedException.expectMessage("Expected cause.");
        expectedException.expectMessage("AtomStatusParser-archived.xml");

        when(resourceResolver.resolve(eq(uri), any(RepositoryConfig.class))).thenReturn(resource);
        when(abderaParser.parse(any(InputStream.class))).thenThrow(expectedCause);

        underTest.resolve(uri, repositoryConfig);
    }
}