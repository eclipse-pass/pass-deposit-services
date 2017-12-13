/*
 * Copyright 2017 Johns Hopkins University
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
package org.dataconservancy.nihms.cli;

import org.apache.commons.codec.binary.Base64InputStream;
import org.dataconservancy.nihms.assembler.nihmsnative.NihmsAssembler;
import org.dataconservancy.nihms.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.nihms.submission.SubmissionEngine;
import org.dataconservancy.nihms.transport.ftp.DefaultFtpClientFactory;
import org.dataconservancy.nihms.transport.ftp.FtpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.format;
import static org.dataconservancy.nihms.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.dataconservancy.nihms.transport.ftp.FtpTransportHints.BASE_DIRECTORY;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsSubmissionApp {

    private static final String MISSING_TRANSPORT_HINTS = "No classpath resource found for transport configuration " +
            "key '%s': '%s' not found on class path.";

    private static final String ERR_LOADING_TRANSPORT_HINTS = "Error loading classpath resource '%s': %s";

    private static final String SETTING_TRANSPORT_HINT = "Transport hint key: '%s' -> Setting '%s' to '%s'";

    private static final String LOCAL_FTP_SERVER_KEY = "LOCAL_FTP_SERVER";

    private static final Logger LOG = LoggerFactory.getLogger(NihmsSubmissionApp.class);

    private File propertiesFile;

    private String transportKey;



    NihmsSubmissionApp(File propertiesFile, String transportKey) {
        this.propertiesFile = propertiesFile;
        this.transportKey = transportKey;
    }

    void run() throws NihmsCliException {
        try {
            SubmissionEngine engine = new SubmissionEngine(
                    new FilesystemModelBuilder(),
                    new NihmsAssembler(),
                    new FtpTransport(new DefaultFtpClientFactory()));

            engine.setTransportHints(() -> resolveTransportHints(transportKey));

            run(engine);
        } catch (Exception e) {
            if (e instanceof NihmsCliException) {
                throw e;
            }
            throw new NihmsCliException(e.getMessage(), e);
        }
    }

    void run(SubmissionEngine engine) throws NihmsCliException {
        try {
            engine.submit(propertiesFile.getCanonicalPath());
        } catch (Exception e) {
            throw new NihmsCliException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, String> resolveTransportHints(String transportKey) {
        if (transportKey == null) {
            return null;
        }

        String resource = "/" + transportKey + ".properties";

        InputStream resourceStream = this.getClass().getResourceAsStream(resource);

        if (resourceStream == null) {
            throw new RuntimeException(new NihmsCliException(
                    format(MISSING_TRANSPORT_HINTS, transportKey, resource)));
        }

        Properties transportProperties = new Properties();
        try {
            transportProperties.load(new Base64InputStream(resourceStream));
            if (!transportProperties.containsKey(TRANSPORT_SERVER_FQDN)) {
                if ("nih".equals(transportKey)) {
                    String nihFtpHost = "ftp-private.ncbi.nlm.nih.gov";
                    LOG.debug(format(SETTING_TRANSPORT_HINT, transportKey, TRANSPORT_SERVER_FQDN, nihFtpHost));
                    transportProperties.put(TRANSPORT_SERVER_FQDN, nihFtpHost);
                }
                if ("local".equals(transportKey)) {
                    String localFtpHost;
                    if ((localFtpHost = System.getenv(LOCAL_FTP_SERVER_KEY)) == null) {
                        localFtpHost = System.getProperty(LOCAL_FTP_SERVER_KEY, "localhost");
                    }

                    LOG.debug(format(SETTING_TRANSPORT_HINT, transportKey, TRANSPORT_SERVER_FQDN, localFtpHost));
                    transportProperties.put(TRANSPORT_SERVER_FQDN, localFtpHost);
                }
            }

            if (!transportProperties.containsKey(BASE_DIRECTORY)) {
                LOG.debug(format(SETTING_TRANSPORT_HINT, transportKey, BASE_DIRECTORY, SubmissionEngine.BASE_DIRECTORY));
                transportProperties.put(BASE_DIRECTORY, SubmissionEngine.BASE_DIRECTORY);
            }
        } catch (IOException e) {
            throw new RuntimeException(new NihmsCliException(
                    format(ERR_LOADING_TRANSPORT_HINTS, resource, e.getMessage()), e));
        }

        return ((Map<String, String>) (Map) transportProperties);
    }

}

