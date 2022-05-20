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

import java.util.Map;

import org.apache.commons.net.ftp.FTPClient;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.springframework.stereotype.Component;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class DefaultFtpClientFactory implements FtpClientFactory {

    @Override
    public FTPClient newInstance(Map<String, String> hints) {
        String protocolHint = hints.get(Transport.TRANSPORT_PROTOCOL);
        try {
            if (Transport.PROTOCOL.ftp != Transport.PROTOCOL.valueOf(protocolHint)) {
                throw new RuntimeException("Unsupported transport protocol '" + protocolHint + "'");
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown transport protocol '" + protocolHint + "'");
        }

        String authMode = hints.get(Transport.TRANSPORT_AUTHMODE);

        try {
            if (Transport.AUTHMODE.userpass != Transport.AUTHMODE.valueOf(authMode)) {
                throw new RuntimeException("Unsupported authentication mode '" + authMode + "'");
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown authentication mode '" + authMode + "'");
        }

        return new FTPClient();
    }

}
