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

package org.dataconservancy.nihms.transport;

import java.util.Map;

/**
 * Abstracts the transport protocol used to deposit a package with a target submission system.
 */
public interface Transport {

    String TRANSPORT_SERVERID = "nihms.transport.serverid";

    String TRANSPORT_USERNAME = "nihms.transport.username";

    String TRANSPORT_PASSWORD = "nihms.transport.password";

    String TRANSPORT_AUTHMODE = "nihms.transport.authmode";

    String TRANSPORT_PROTOCOL = "nihms.transport.protocol";

    String TRANSPORT_SERVER_FQDN = "nihms.transport.server-fqdn";

    String TRANSPORT_SERVER_PORT = "nihms.transport.server-port";

    enum AUTHMODE {

        /**
         * The implementation will use the username and password from {@link #TRANSPORT_USERNAME} and {@link #TRANSPORT_PASSWORD}
         */
        userpass,

        /**
         * The transport implementation will perform authentication implicitly
         */
        implicit,

        /**
         * The transport implementation will look up authentication credentials using an implementation-specific
         * reference (e.g. a {@link #TRANSPORT_SERVERID} that can be used to look up authentication credentials)
         */
        reference

    }

    enum PROTOCOL {
        http,
        https,
        ftp
    }

    TransportSession open(Map<String, String> hints);

}
