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

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.abdera.protocol.client.ClientResponse;
import org.apache.http.ParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.URI;

import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_ARCHIVED;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INPROGRESS;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_INREVIEW;
import static org.dataconservancy.pass.deposit.messaging.status.SwordDspaceDepositStatus.SWORD_STATE_WITHDRAWN;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomFeedStatusParserTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private AbderaClient abderaClient;

    private Parser abderaParser;

    private AtomFeedStatusParser underTest;

    @Before
    public void setUp() throws Exception {
        abderaClient = mock(AbderaClient.class);
        abderaParser = mock(Parser.class);
        underTest = new AtomFeedStatusParser(abderaClient, abderaParser);
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
     * @throws Exception
     */
    @Test
    public void mapArchived() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed("AtomStatusParser-archived.xml");
        Assert.assertEquals(SWORD_STATE_ARCHIVED, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapInProgress() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed("AtomStatusParser-inprogress.xml");
        assertEquals(SWORD_STATE_INPROGRESS, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapInReview() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed("AtomStatusParser-inreview.xml");
        assertEquals(SWORD_STATE_INREVIEW, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapMissing() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed("AtomStatusParser-missing.xml");
        assertEquals(null, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapMultiple() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed("AtomStatusParser-multiple.xml");
        assertEquals(SWORD_STATE_ARCHIVED, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapUnknown() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed("AtomStatusParser-unknown.xml");
        assertEquals(null, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void mapWithdrawn() throws Exception {
        Document<Feed> feed = AtomTestUtil.parseFeed("AtomStatusParser-withdrawn.xml");
        assertEquals(SWORD_STATE_WITHDRAWN, AtomUtil.parseAtomStatement(feed));
    }

    @Test
    public void parseWithRuntimeException() throws Exception {
        RuntimeException expected = new RuntimeException("Expected exception.");
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Expected exception.");
        when(abderaClient.get(anyString())).thenThrow(expected);

        underTest.parse(URI.create("http://url/to/atom/statement"));
    }

    @Test
    public void parseWithParseException() throws Exception {
        ParseException expected = new ParseException("Expected exception.");
        expectedException.expect(ParseException.class);
        expectedException.expectMessage("Expected exception.");

        ClientResponse res = mock(ClientResponse.class);
        when(abderaClient.get(any())).thenReturn(res);
        when(res.getDocument()).thenThrow(expected);

        underTest.parse(URI.create("http://url/to/atom/statement"));
    }
}