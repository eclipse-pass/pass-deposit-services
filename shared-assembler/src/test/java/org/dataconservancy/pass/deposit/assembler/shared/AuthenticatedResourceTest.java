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

package org.dataconservancy.pass.deposit.assembler.shared;

import static java.util.Base64.getEncoder;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Before;
import org.junit.Test;

public class AuthenticatedResourceTest {

    private URL resourceUrl;

    private HttpURLConnection urlConn;

    private InputStream in;

    private String user = "cow";

    private String pass = "moo";

    private AuthenticatedResource underTest;

    @Before
    public void setUp() throws Exception {
        resourceUrl = mock(URL.class);
        in = mock(InputStream.class);
        urlConn = mock(HttpURLConnection.class);

        when(resourceUrl.openConnection()).thenReturn(urlConn);
        when(urlConn.getInputStream()).thenReturn(in);

        underTest = new AuthenticatedResource(resourceUrl, user, pass);
    }

    /**
     * User and password on the AuthenticatedResource should be supplied as Basic auth
     */
    @Test
    public void customizeConnBasicAuth() throws IOException {
        underTest.getInputStream();
        assertTrue(user.trim().length() > 0);
        assertTrue(pass.trim().length() > 0);
        verify(urlConn).setRequestProperty(eq("Authorization"), anyString());
    }

    /**
     * null strings are allowed as a user name and password
     */
    @Test
    public void customConnBasicAuthNullUserAndPass() throws IOException {
        underTest = new AuthenticatedResource(resourceUrl, null, null);
        underTest.getInputStream();
        verify(urlConn).setRequestProperty(eq("Authorization"),
                                           eq("Basic " + getEncoder().encodeToString(
                                               String.format("%s:%s", null, null).getBytes())));
    }

    /**
     * Empty strings are allowed as a user name and password
     */
    @Test
    public void customConnBasicAuthEmptyUserAndPass() throws IOException {
        underTest = new AuthenticatedResource(resourceUrl, "", "");
        underTest.getInputStream();
        verify(urlConn).setRequestProperty(eq("Authorization"),
                                           eq("Basic " + getEncoder().encodeToString(":".getBytes())));
    }
}