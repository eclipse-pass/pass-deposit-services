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
package org.dataconservancy.nihms.transport.ftp;

import com.google.common.net.InetAddresses;
import org.apache.commons.net.ftp.FTPClient;
import org.dataconservancy.nihms.transport.Transport;
import org.dataconservancy.nihms.transport.TransportSession;

import java.util.Map;

import static org.dataconservancy.nihms.transport.ftp.FtpUtil.login;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.makeDirectories;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.performSilently;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.setDataType;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.setPasv;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.setTransferMode;
import static org.dataconservancy.nihms.transport.ftp.FtpUtil.setWorkingDirectory;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class FtpTransport implements Transport {

    private FtpClientFactory ftpClientFactory;

    public FtpTransport(FtpClientFactory ftpClientFactory) {
        this.ftpClientFactory = ftpClientFactory;
    }

    @Override
    public TransportSession open(Map<String, String> hints) {
        FTPClient ftpClient = ftpClientFactory.newInstance(hints);

        String serverName = hints.get(Transport.TRANSPORT_SERVER_FQDN);
        String serverPort = hints.get(Transport.TRANSPORT_SERVER_PORT);
        String transferMode = hints.get(FtpTransportHints.TRANSFER_MODE);
        String baseDir = hints.get(FtpTransportHints.BASE_DIRECTORY);

        if (InetAddresses.isInetAddress(serverName)) {
            performSilently(ftpClient, () -> {
                ftpClient.connect(InetAddresses.forString(serverName), Integer.parseInt(serverPort));
                return true;
            });
        } else {
            performSilently(ftpClient, () -> {
                ftpClient.connect(serverName, Integer.parseInt(serverPort));
                return true;
            });
        }

        login(ftpClient, hints.get(TRANSPORT_USERNAME), hints.get(TRANSPORT_PASSWORD));
        setTransferMode(ftpClient, transferMode);
        setWorkingDirectory(ftpClient, baseDir);

        return new FtpTransportSession(ftpClient);
    }


}
