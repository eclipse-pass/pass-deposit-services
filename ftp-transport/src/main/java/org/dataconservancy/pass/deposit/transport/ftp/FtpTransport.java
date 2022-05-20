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
package org.dataconservancy.pass.deposit.transport.ftp;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.setTransferMode;
import static org.dataconservancy.pass.deposit.transport.ftp.FtpUtil.setWorkingDirectory;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.apache.commons.net.ftp.FTPClient;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encapsulates a provider for FTP transport sessions.  {@link #open(Map) Opening} a FTP transport session entails the
 * following:
 * <ol>
 *     <li>Successfully {@link FtpUtil#connect(FTPClient, String, int) connect} to an FTP server</li>
 *     <li>Successfully {@link FtpUtil#login(FTPClient, String, String) login} to the FTP server</li>
 *     <li>Set the transfer mode being used for this session</li>
 *     <li>Create (if needed) and change into the base working directory</li>
 * </ol>
 * In other words, a caller executing a {@link FtpTransport#open(Map)} will receive a {@link FtpTransportSession} that
 * is connected, logged in, and set to a certain working directory.
 *
 * Hints accepted by this transport are:
 * <dl>
 *     <dt>{@link Transport#TRANSPORT_SERVER_FQDN}</dt>
 *     <dd>The fully qualified domain name, IPv4, or IPv6 address of the FTP server</dd>
 *     <dt>{@link Transport#TRANSPORT_SERVER_PORT}</dt>
 *     <dd>The TCP port number of the FTP server</dd>
 *     <dt>{@link Transport#TRANSPORT_USERNAME}</dt>
 *     <dd>The username to authenticate as</dd>
 *     <dt>{@link Transport#TRANSPORT_PASSWORD}</dt>
 *     <dd>The password to authenticate with</dd>
 *     <dt>{@link FtpTransportHints#TRANSFER_MODE}</dt>
 *     <dd>The transfer mode, expected to be one of {@link FtpTransportHints.MODE}</dd>
 *     <dt>{@link FtpTransportHints#DATA_TYPE}</dt>
 *     <dd>The data type to use when transferring files, expected to be one of {@link FtpTransportHints.TYPE}</dd>
 *     <dt>{@link FtpTransportHints#BASE_DIRECTORY}</dt>
 *     <dd>A directory that will be set as the current working directory for the session</dd>
 * </dl>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class FtpTransport implements Transport {

    private static Logger LOG = LoggerFactory.getLogger(FtpTransport.class);

    private FtpClientFactory ftpClientFactory;

    /**
     * Constructs a new FtpTransport with the supplied {@link FtpClientFactory}.  The client factory is used to create
     * instances of {@link FTPClient} that underly {@link #open(Map) opened sessions}.
     *
     * @param ftpClientFactory used to create instances of {@link FTPClient}
     */
    @Autowired
    public FtpTransport(FtpClientFactory ftpClientFactory) {
        this.ftpClientFactory = ftpClientFactory;
    }

    @Override
    public PROTOCOL protocol() {
        return PROTOCOL.ftp;
    }

    /**
     * Uses the supplied configuration hints to open a new session with an FTP server.  Each session has new {@link
     * FTPClient} which is used to communicate with the remote FTP server.  The {@link #FtpTransport(FtpClientFactory)
     * client factory} supplied on construction is used for creating the {@code FTPClient} instances.
     *
     * @param hints configuration hints
     * @return the open transport session
     * @throws RuntimeException if the session cannot be successfully opened
     */
    @Override
    public TransportSession open(Map<String, String> hints) {
        return open(ftpClientFactory.newInstance(hints), hints);
    }

    /**
     * Uses the supplied configuration hints to open a new session with an FTP server.  The {@code ftpClient} underlies
     * the opened session.
     * <p>
     * Package private method for testing.
     * </p>
     *
     * @param ftpClient the FTP client used by the underlying FTP session
     * @param hints     configuration hints
     * @return the open transport session
     * @throws RuntimeException if the session cannot be successfully opened
     */
    FtpTransportSession open(FTPClient ftpClient, Map<String, String> hints) {
        String serverName = hints.get(Transport.TRANSPORT_SERVER_FQDN);
        String serverPort = hints.get(Transport.TRANSPORT_SERVER_PORT);
        String transferMode = hints.get(FtpTransportHints.TRANSFER_MODE);
        String baseDir = hints.get(FtpTransportHints.BASE_DIRECTORY);

        FtpUtil.connect(ftpClient, serverName, Integer.parseInt(serverPort));
        FtpUtil.login(ftpClient, hints.get(TRANSPORT_USERNAME), hints.get(TRANSPORT_PASSWORD));
        setTransferMode(ftpClient, transferMode);

        if (baseDir != null && baseDir.trim().length() > 0) {
            if (baseDir.contains("%s")) {
                baseDir = String.format(baseDir, OffsetDateTime.now(ZoneId.of("UTC")).format(ISO_LOCAL_DATE));
            }
            setWorkingDirectory(ftpClient, baseDir);
        }

        // Initialize the system type, which is cached for the duration of an FTP Client instance
        // Having this value cached will resolve some issues with aborted file transfers and directory listings
        FtpUtil.performSilently(ftpClient, ftpClient::getSystemType);

        FtpTransportSession session = new FtpTransportSession(ftpClient);
        LOG.debug("Opened {}@{}...", session.getClass().getSimpleName(), toHexString(identityHashCode(session)));
        return session;
    }

}
