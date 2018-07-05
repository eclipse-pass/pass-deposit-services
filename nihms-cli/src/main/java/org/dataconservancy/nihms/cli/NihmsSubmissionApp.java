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
import org.dataconservancy.pass.deposit.assembler.shared.DefaultMetadataBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.shared.DefaultResourceBuilderFactory;
import org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative.NihmsAssembler;
import org.dataconservancy.pass.deposit.builder.fs.FilesystemModelBuilder;
import org.dataconservancy.nihms.submission.SubmissionEngine;
import org.dataconservancy.pass.deposit.transport.ftp.DefaultFtpClientFactory;
import org.dataconservancy.pass.deposit.transport.ftp.FtpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.format;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints.BASE_DIRECTORY;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class NihmsSubmissionApp {

    private static String MISSING_TRANSPORT_HINTS = "No classpath resource found for transport configuration " +
            "key '%s': '%s' not found on class path.";

    private static String ERR_LOADING_TRANSPORT_HINTS = "Error loading classpath resource '%s': %s";

    private static String SETTING_TRANSPORT_HINT = "Transport hint key: '%s' -> Setting '%s' to '%s'";

    private static String LOCAL_FTP_SERVER_KEY = "LOCAL_FTP_SERVER";

    private static Logger LOG = LoggerFactory.getLogger(NihmsSubmissionApp.class);

    /**
     * Reference to a File that contains the sample data used to compose the submission
     */
    private File sampleDataFile;

    /**
     * Key used to resolve FTP transport configuration hints
     */
    private String transportKey;

    /**
     * Map containing FTP transport configuration hints
     */
    private Map<String, String> transportHints;


    NihmsSubmissionApp(File sampleDataFile, String transportKey) {
        this.sampleDataFile = sampleDataFile;
        this.transportKey = transportKey;
    }

    NihmsSubmissionApp(File sampleDataFile, Map<String, String> transportHints) {
        this.sampleDataFile = sampleDataFile;
        this.transportHints = transportHints;
    }

    void run() throws NihmsCliException {
        try {
            SubmissionEngine engine = new SubmissionEngine(
                    new FilesystemModelBuilder(),
                    new NihmsAssembler(new DefaultMetadataBuilderFactory(), new DefaultResourceBuilderFactory()),
                    new FtpTransport(new DefaultFtpClientFactory()));

            // Prefer the use of the Map<String, String> transport hints.  If they aren't available, use the
            // transport key to resolve the transport hints
            if (transportHints == null) {
                engine.setTransportHints(() -> {
                    Map<String, String> hints = resolveTransportHints(transportKey);
                    if (LOG.isDebugEnabled()) {
                        hints.forEach((k, v) -> LOG.debug("Resolved transport key '{}' property '{}' to '{}'",
                                transportKey, k, v));
                    }
                    return hints;
                });
            } else {
                engine.setTransportHints(() -> {
                    if (LOG.isDebugEnabled()) {
                        transportHints.forEach((k, v) -> LOG.debug("Using supplied transport property '{}' = '{}'",
                                k, v));

                    }
                    return transportHints;
                });
            }

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
            if (sampleDataFile != null) {
                engine.submit(sampleDataFile.getCanonicalPath());
            } else {
                throw new NihmsCliException("No data was supplied for the submission!");
            }
        } catch (Exception e) {
            throw new NihmsCliException(e.getMessage(), e);
        }
    }

    /**
     * Resolves the configuration properties of the FTP transport using the supplied {@code transportKey}.
     * <p>
     * A classpath resource is derived from the {@code transportKey} formatted as: {@code /<transportKey>.properties}.
     * As such, the {@code transportKey} should be suitable for use as a path component (e.g. don't use characters that
     * may not be easily expressed in a path component when using or deciding on transport keys).
     * </p>
     * <p>
     * This method will attempt to resolve the classpath resource {@code /<transportKey>.properties}, convert the
     * properties to a {@code Map<String, String>}, and may employ logic to set default values for the properties based
     * on the transport key used, and/or the presence or absence of environment variables.
     * </p>
     * @param transportKey the key, converted to a classpath resource, used to resolve properties that will be used to
     *                     configure the FTP transport
     * @return the properties (as a {@code Map<String, String>} used to configure the FTP transport
     */
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

        return resolvePropertiesFromClasspathResource(transportKey, resource, resourceStream);
    }

    /**
     * Attempts to load the {@code resourceStream} containing properties into a {@code Map<String, String>}, with
     * special handling of the {@code nihms.transport.server-fqdn} and {@code nihms.ftp.basedir} properties.
     * <p>
     * The value of the {@code nihms.transport.server-fqdn} property is resolved based on the value of the provided
     * {@code transportKey}.  If the {@code transportKey} value is {@code nih}, then the NIH's production FTP server
     * will be used as the value of {@code nihms.transport.server-fqdn}.  If the {@code transportKey} value is
     * {@code local}, then: the value of the environment variable {@code LOCAL_FTP_SERVER} is consulted.  If an
     * environment variable of that name does not exist or is set to {@code null}, the system property named {@code
     * LOCAL_FTP_SERVER} is consulted.  If the system property {@code LOCAL_FTP_SERVER} is {@code null} or is not
     * present, the default value of {@code localhost} is returned.
     * </p>
     * <p>
     * The value of the {@code nihms.ftp.basedir} property is resolved based on the presence or absence of the
     * environment variable named {@code NIHMS_FTP_BASEDIR}.  If the environment variable {@code NIHMS_FTP_BASEDIR} is
     * present and non-null, the value of the environment variable is used, overriding the presence of the system
     * property {@code nihms.ftp.basedir} in the {@code resourceStream}.  If the environment variable {@code
     * NIHMS_FTP_BASEDIR} is not present, the value of the {@code nihms.ftp.basedir} property is obtained from the
     * {@code resourceStream}.  If that value is {@code null}, then the default value from {@link
     * SubmissionEngine#BASE_DIRECTORY} is used.
     * </p>
     *
     * @param transportKey used to set the FTP server used for submission
     * @param resource the name of the resource, typically a location on the classpath
     * @param resourceStream the {@code resource}, resolved to an {@code InputStream} from the classpath
     * @return the resolved properties as a map, with the value of {@code nihms.transport.server-fqdn} and
     *         {@code nihms.ftp.basedir} resolved as documented above
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> resolvePropertiesFromClasspathResource(String transportKey, String resource,
                                                                       InputStream resourceStream) {
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
                LOG.debug(format(SETTING_TRANSPORT_HINT, transportKey,
                        BASE_DIRECTORY, SubmissionEngine.BASE_DIRECTORY));
                transportProperties.put(BASE_DIRECTORY, SubmissionEngine.BASE_DIRECTORY);
            }
        } catch (IOException e) {
            throw new RuntimeException(new NihmsCliException(
                    format(ERR_LOADING_TRANSPORT_HINTS, resource, e.getMessage()), e));
        }

        return ((Map<String, String>) (Map) transportProperties);
    }

}

