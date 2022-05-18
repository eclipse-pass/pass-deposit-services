/*
 * Copyright 2020 Johns Hopkins University
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
package org.dataconservancy.pass.deposit.transport.sword2;

import static java.util.Collections.singletonList;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.HINT_TUPLE_SEPARATOR;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.HINT_URL_SEPARATOR;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_COLLECTION_HINTS;
import static org.dataconservancy.pass.deposit.transport.sword2.Sword2TransportHints.SWORD_COLLECTION_URL;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.abdera.i18n.iri.IRI;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.junit.Before;
import org.junit.Test;
import org.swordapp.client.AuthCredentials;
import org.swordapp.client.SWORDClient;
import org.swordapp.client.SWORDCollection;
import org.swordapp.client.SWORDWorkspace;
import org.swordapp.client.ServiceDocument;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class Sword2TransportSessionTest {

    /**
     * Authentication credentials used by the client when communicating with a SWORD endpoint.  This object encapsulates
     * the On-Behalf-Of semantics..
     */
    private AuthCredentials authCreds;

    /**
     * SWORD Service document
     */
    private ServiceDocument serviceDoc;

    /**
     * Metadata about the Package; includes the Submission.metadata serialized as a JsonObject.
     */
    private PackageStream.Metadata packageMd;

    /**
     * Instantiates mocks for tests that must be configured in individual test methods.
     */
    @Before
    public void setUp() {
        serviceDoc = mock(ServiceDocument.class);
        packageMd = mock(PackageStream.Metadata.class);
        authCreds = mock(AuthCredentials.class);
    }

    /**
     * Insures that when a configured collection hint matches a hint in the Submission.metadata, that collection is
     * selected for deposit.
     */
    @Test
    public void testDepositWithCollectionHints() {
        // Metadata mapping used to configure the transport (the default SWORD collection url and configured hints
        // mapping)
        Map<String, String> transportMd = new HashMap<>();

        // Set up a default collection URL, used in case no configured hints match any of the hints supplied in the
        // package metadata
        String defaultCollectionUrl = "http://moo.cow/bar";
        transportMd.put(SWORD_COLLECTION_URL, defaultCollectionUrl);

        // Set up a configured hint and URL, normally provided by the deposit services configuration file
        String configuredUrl = "http://covid.collection/baz";
        String configuredHints = String.format("%s%s%s", "covid", HINT_URL_SEPARATOR, configuredUrl);
        transportMd.put(SWORD_COLLECTION_HINTS, configuredHints);

        // Set up the metadata coming in with the Submission, and attach it to the package metadata
        String submissionMetaStr = "{\n" +
                                   "    \"$schema\": \"https://oa-pass.github.io/metadata-schemas/jhu/global.json\"," +
                                   "\n" +
                                   "    \"title\": \"The title of the article\",\n" +
                                   "    \"journal-title\": \"A Terrific Journal\",\n" +
                                   "    \"hints\": {\n" +
                                   "        \"collection-tags\": [\n" +
                                   "            \"covid\",\n" +
                                   "            \"nobel\"\n" +
                                   "        ]\n" +
                                   "    }\n" + "}";

        JsonObject submissionMeta = new JsonParser().parse(submissionMetaStr).getAsJsonObject();
        when(packageMd.submissionMeta()).thenReturn(submissionMeta);

        // Decorate the serviceDoc with SWORDCollections to answer to, being careful use it in our test methods below
        ServiceDocument doc = swordServiceDocument(serviceDoc, defaultCollectionUrl, configuredUrl);

        Sword2TransportSession underTest = new Sword2TransportSession(mock(SWORDClient.class), doc, authCreds);

        SWORDCollection selectedCollection = underTest.selectCollection(doc, packageMd, transportMd);

        assertEquals(configuredUrl, selectedCollection.getHref().toString());
    }

    /**
     * Verifies that if multiple hints match, only the first match is selected for deposit.  Multiple deposits do not
     * occur and additional collection tags are ignored.
     */
    @Test
    public void testDepositWithMultipleConfiguredCollectionHintsOnlyFirstMatches() {
        // Metadata mapping used to configure the transport (the default SWORD collection url and configured hints
        // mapping)
        Map<String, String> transportMd = new HashMap<>();

        // Set up a default collection URL, used in case no configured hints match any of the hints supplied in the
        // package metadata
        String defaultCollectionUrl = "http://moo.cow/bar";
        transportMd.put(SWORD_COLLECTION_URL, defaultCollectionUrl);

        // Set up two configured hints and URLs, normally provided by the deposit services configuration file
        String configuredUrlOne = "http://covid.collection/baz";
        String configuredUrlTwo = "http://nobellaureates.collection/biz";
        String configuredHints = String.format("%s%s%s%s%s%s%s", "covid", HINT_URL_SEPARATOR, configuredUrlOne,
                                               HINT_TUPLE_SEPARATOR, "nobel", HINT_URL_SEPARATOR, configuredUrlTwo);
        transportMd.put(SWORD_COLLECTION_HINTS, configuredHints);

        // Set up the metadata coming in with the Submission, and attach it to the package metadata
        String submissionMetaStr = "{\n" +
                                   "    \"$schema\": \"https://oa-pass.github.io/metadata-schemas/jhu/global.json\"," +
                                   "\n" +
                                   "    \"title\": \"The title of the article\",\n" +
                                   "    \"journal-title\": \"A Terrific Journal\",\n" +
                                   "    \"hints\": {\n" +
                                   "        \"collection-tags\": [\n" +
                                   "            \"covid\",\n" +
                                   "            \"nobel\"\n" +
                                   "        ]\n" +
                                   "    }\n" + "}";

        JsonObject submissionMeta = new JsonParser().parse(submissionMetaStr).getAsJsonObject();
        when(packageMd.submissionMeta()).thenReturn(submissionMeta);

        // Decorate the serviceDoc with SWORDCollections to answer to, being careful use it in our test methods below
        ServiceDocument doc = swordServiceDocument(serviceDoc,
                                                   defaultCollectionUrl, configuredUrlOne, configuredUrlTwo);

        Sword2TransportSession underTest = new Sword2TransportSession(mock(SWORDClient.class), doc, authCreds);

        SWORDCollection selectedCollection = underTest.selectCollection(doc, packageMd, transportMd);

        assertEquals(configuredUrlOne, selectedCollection.getHref().toString());
    }

    /**
     * Verifies that if no configured hints are provided, the selected collection reverts to the default collection.
     */
    @Test
    public void testDepositWithNoConfiguredHints() {
        // Metadata mapping used to configure the transport (the default SWORD collection url and configured hints
        // mapping)
        Map<String, String> transportMd = new HashMap<>();

        // Set up a default collection URL, used in case no configured hints match any of the hints supplied in the
        // package metadata
        String defaultCollectionUrl = "http://moo.cow/bar";
        transportMd.put(SWORD_COLLECTION_URL, defaultCollectionUrl);

        // No configured hints are provided for the SWORD_COLLECTIONS_HINTS key, only the default is provided above

        // Set up the metadata coming in with the Submission, and attach it to the package metadata
        String submissionMetaStr = "{\n" +
                                   "    \"$schema\": \"https://oa-pass.github.io/metadata-schemas/jhu/global.json\"," +
                                   "\n" +
                                   "    \"title\": \"The title of the article\",\n" +
                                   "    \"journal-title\": \"A Terrific Journal\",\n" +
                                   "    \"hints\": {\n" +
                                   "        \"collection-tags\": [\n" +
                                   "            \"covid\",\n" +
                                   "            \"nobel\"\n" +
                                   "        ]\n" +
                                   "    }\n" + "}";

        JsonObject submissionMeta = new JsonParser().parse(submissionMetaStr).getAsJsonObject();
        when(packageMd.submissionMeta()).thenReturn(submissionMeta);

        // Decorate the serviceDoc with SWORDCollections to answer to, being careful use it in our test methods below
        ServiceDocument doc = swordServiceDocument(serviceDoc, defaultCollectionUrl);

        Sword2TransportSession underTest = new Sword2TransportSession(mock(SWORDClient.class), doc, authCreds);

        SWORDCollection selectedCollection = underTest.selectCollection(doc, packageMd, transportMd);

        assertEquals(defaultCollectionUrl, selectedCollection.getHref().toString());
    }

    // Mocks a service document providing access to the following collections in a single SWORDWorkspace
    private static ServiceDocument swordServiceDocument(ServiceDocument doc, String... collectionUrls) {
        SWORDWorkspace workspace = mock(SWORDWorkspace.class);
        List<SWORDCollection> collections = new ArrayList<>();
        for (String url : collectionUrls) {
            SWORDCollection collection = mock(SWORDCollection.class);
            when(collection.getHref()).thenReturn(new IRI(url));
            collections.add(collection);
        }

        when(workspace.getCollections()).thenReturn(collections);

        when(doc.getWorkspaces()).thenReturn(singletonList(workspace));
        return doc;
    }

}