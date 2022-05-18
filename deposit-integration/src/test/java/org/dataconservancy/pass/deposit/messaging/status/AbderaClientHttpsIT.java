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
package org.dataconservancy.pass.deposit.messaging.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.protocol.client.AbderaClient;
import org.apache.abdera.protocol.client.ClientResponse;
import org.apache.commons.httpclient.Credentials;
import org.dataconservancy.pass.deposit.messaging.config.spring.DrainQueueConfig;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Insures that the Apache {@code AbderaClient} can connect to https URLs, specifically the ability to trust the root
 * of the certificate chain for the JScholarship SSL certificate.
 * <h4>Implementation Notes</h4>
 * As of 10/11/2018, the root of the certificate chain for https://jscholarship.library.jhu.edu is the <em>AddTrust
 * External CA Root</em>, expiring 5/30/2020, with SHA-1 fingerprint {@code 02 FA F3 E2 91 43 54 68 60 78 57 69 4D F5
 * E4 5B 68 85 18 68}.  We care about JScholarship and this certificate because it terminates the SSL connection to
 * the SWORD endpoint used for deposit by Deposit Services.  The {@code AbderaClient} must be able to negotiate an SSL
 * connection to this server in order to resolve SWORD service-related documents.
 * </p>
 * <p>
 * As it happens, more recent JREs already trust the <em>AddTrust</em> CA.  The Alpine Linux image that runs Deposit
 * Services has the <em>AddTrust</em> CA present in {@code /usr/lib/jvm/java-1.8-openjdk/jre/lib/security/cacerts}.
 * Therefore nothing special needs to be done in order to use SSL and TLS with the SWORD endpoint.
 * </p>
 * <p>
 * The SWORD endpoint is protected by username and password.  The SWORD endpoint used by this test is the <em>production
 * </em> endpoint, because that's the endpoint ultimately being contacted by Deposit Services.  The staging SWORD
 * endpoint uses a different URL, but also requires authentication and is not available outside of the JHU network.  If
 * the staging endpoint was publicly available, and if there were a secure mechanism for storing authentication
 * credentials, then this test could be executed automatically every time ITs are run.  However, because this test
 * contacts the production endpoint, and because it requires username and password authentication, this test is
 * {@code @Ignore}ed by default, and must be executed manually on the command line:
 * </p>
 * <pre>$ mvn clean verify -Dit.test=AbderaClientHttpsIT -Dftp.skip -Ddspace.skip -Dpostgres.skip -Dfcrepo.skip
 * -Dsword.user=<username> -Dsword.pass=<password></pre>
 * </p>
 * <p>
 * Future implementations of this test should probably use a lower-level library to verify SSL/TLS connectivity without
 * relying on Abdera. There is no need to login and resolve the service document to prove SSL/TLS connectivity, but for
 * now it was expedient.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Import(DrainQueueConfig.class)
@DirtiesContext
@Ignore("To be run manually, see class Javadoc.")
public class AbderaClientHttpsIT {

    @Autowired
    private AbderaClient underTest;

    private String httpsSwordServiceDocUrl = "https://jscholarship.library.jhu.edu/swordv2/servicedocument";

    @Value("${sword.user}")
    private String swordUser;

    @Value("${sword.pass}")
    private String swordPass;

    @Before
    public void setUp() throws Exception {
        assertTrue("Expected the SWORD service document URL to begin with 'https://'!",
                   httpsSwordServiceDocUrl.startsWith("https://"));

        assertNotNull("Expected a value for the 'sword.user' property, but was 'null'", swordUser);
        assertNotNull("Expected a value for the 'sword.pass' property, but was 'null'", swordPass);

        Credentials creds = new org.apache.commons.httpclient.UsernamePasswordCredentials(swordUser, swordPass);
        underTest.addCredentials(httpsSwordServiceDocUrl, null, null, creds);
    }

    @Test
    public void httpsConnection() throws Exception {
        ClientResponse res = underTest.get(httpsSwordServiceDocUrl);
        assertNotNull("Expected a non-null ClientResponse!", res);
        String msg = "Received unexpected response code %s retrieving %s: %s";
        assertEquals(String.format(msg, res.getStatus(), httpsSwordServiceDocUrl, res.getStatusText()),
                     200, res.getStatus());
        Document<Feed> serviceDoc = res.getDocument();
        assertNotNull("Expected a non-null service document!", serviceDoc);
        assertEquals(httpsSwordServiceDocUrl, serviceDoc.getBaseUri().toString());
    }
}
