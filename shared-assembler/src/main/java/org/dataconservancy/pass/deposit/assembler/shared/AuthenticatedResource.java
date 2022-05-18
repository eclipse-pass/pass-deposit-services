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
package org.dataconservancy.pass.deposit.assembler.shared;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Base64.getEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.UrlResource;
import org.springframework.util.ResourceUtils;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AuthenticatedResource extends UrlResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticatedResource.class);

    private URL url;

    private String username;

    private String password;

    /**
     * Preemptively supplies Basic authentication credentials when the URL is accessed.  Redirects are not followed
     * when accessing the URL.
     *
     * @param url      the URL of the resource requiring authentication
     * @param username the username used to authenticate to the resource, may be empty or {@code null}
     * @param password the password used to authenticate to the resource, may be empty or {@code null}
     */
    public AuthenticatedResource(URL url, String username, String password) {
        super(url);
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        URLConnection con = this.url.openConnection();
        ResourceUtils.useCachesIfNecessary(con);
        customizeConnection(con);
        try {
            return con.getInputStream();
        } catch (IOException ex) {
            // Close the HTTP connection (if applicable).
            if (con instanceof HttpURLConnection) {
                ((HttpURLConnection) con).disconnect();
            }
            throw new IOException(String.format("Unable to connect or read from to %s", this.url), ex);
        }
    }

    @Override
    protected void customizeConnection(HttpURLConnection con) throws IOException {
        LOG.trace("Customizing {}@{}", con.getClass().getName(), toHexString(identityHashCode(con)));
        byte[] bytes = String.format("%s:%s", username, password).getBytes();
        con.setRequestProperty("Authorization", "Basic " + getEncoder().encodeToString(bytes));
    }

}
