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
package org.dataconservancy.pass.deposit.messaging.config.repository;

import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_AUTHMODE;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;
import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.BASEDIR;
import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.CREATE_IF_MISSING;
import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.OVERWRITE;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.dataconservancy.pass.deposit.transport.Transport;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class FilesystemBinding extends ProtocolBinding {

    static final String PROTO = "filesystem";

    private String baseDir;

    private String overwrite;

    private String createIfMissing;

    public FilesystemBinding() {
        setProtocol(PROTO);
    }

    @Override
    public Map<String, String> asPropertiesMap() {
        Map<String, String> transportProperties = new HashMap<>();

        transportProperties.put(TRANSPORT_AUTHMODE, Transport.AUTHMODE.implicit.name());
        transportProperties.put(TRANSPORT_PROTOCOL, Transport.PROTOCOL.filesystem.name());
        transportProperties.put(BASEDIR, baseDir);
        transportProperties.put(OVERWRITE, overwrite);
        transportProperties.put(CREATE_IF_MISSING, createIfMissing);

        return transportProperties;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getOverwrite() {
        return overwrite;
    }

    public void setOverwrite(String overwrite) {
        this.overwrite = overwrite;
    }

    public String getCreateIfMissing() {
        return createIfMissing;
    }

    public void setCreateIfMissing(String createIfMissing) {
        this.createIfMissing = createIfMissing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        FilesystemBinding that = (FilesystemBinding) o;
        return Objects.equals(baseDir, that.baseDir) &&
               Objects.equals(overwrite, that.overwrite) &&
               Objects.equals(createIfMissing, that.createIfMissing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), baseDir, overwrite, createIfMissing);
    }

    @Override
    public String toString() {
        return "FilesystemBinding{" + "baseDir='" + baseDir + '\'' + ", overwrite='" + overwrite + '\'' + ", " +
               "createIfMissing='" + createIfMissing + '\'' + "} " + super.toString();
    }
}
