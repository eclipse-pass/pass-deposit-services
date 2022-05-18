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

public class FtpTransportHints {

    private FtpTransportHints() {
        //never called
    }

    public static final String BASE_DIRECTORY = "deposit.transport.protocol.ftp.basedir";

    public static final String TRANSFER_MODE = "deposit.transport.protocol.ftp.transfer-mode";

    public static final String USE_PASV = "deposit.transport.protocol.ftp.use-pasv";

    public static final String DATA_TYPE = "deposit.transport.protocol.ftp.data-type";

    public enum MODE {
        stream,
        block,
        compressed
    }

    public enum TYPE {
        ascii,
        binary
    }

}
