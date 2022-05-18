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

package org.dataconservancy.pass.deposit.messaging.config.repository;

import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_AUTHMODE;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PASSWORD;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_PROTOCOL;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_FQDN;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_SERVER_PORT;
import static org.dataconservancy.pass.deposit.transport.Transport.TRANSPORT_USERNAME;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.ftp.FtpTransportHints;

public class FtpBinding extends ProtocolBinding {

    static final String PROTO = "ftp";

    private String username;

    private String password;

    @JsonProperty("data-type")
    private String dataType;

    @JsonProperty("transfer-mode")
    private String transferMode;

    @JsonProperty("use-pasv")
    private boolean usePasv;

    @JsonProperty("default-directory")
    private String defaultDirectory;

    public FtpBinding() {
        this.setProtocol(PROTO);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getTransferMode() {
        return transferMode;
    }

    public void setTransferMode(String transferMode) {
        this.transferMode = transferMode;
    }

    public boolean isUsePasv() {
        return usePasv;
    }

    public void setUsePasv(boolean usePasv) {
        this.usePasv = usePasv;
    }

    public String getDefaultDirectory() {
        return defaultDirectory;
    }

    public void setDefaultDirectory(String defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    @Override
    public Map<String, String> asPropertiesMap() {
        Map<String, String> transportProperties = new HashMap<>();

        transportProperties.put(TRANSPORT_USERNAME, getUsername());
        transportProperties.put(TRANSPORT_PASSWORD, getPassword());
        transportProperties.put(TRANSPORT_AUTHMODE, Transport.AUTHMODE.userpass.name());
        transportProperties.put(TRANSPORT_PROTOCOL, Transport.PROTOCOL.ftp.name());
        transportProperties.put(TRANSPORT_SERVER_FQDN, getServerFqdn());
        transportProperties.put(TRANSPORT_SERVER_PORT, getServerPort());
        transportProperties.put(FtpTransportHints.BASE_DIRECTORY, getDefaultDirectory());
        transportProperties.put(FtpTransportHints.TRANSFER_MODE, getTransferMode());
        transportProperties.put(FtpTransportHints.DATA_TYPE, getDataType());
        transportProperties.put(FtpTransportHints.USE_PASV, String.valueOf(isUsePasv()));

        return transportProperties;
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
        FtpBinding that = (FtpBinding) o;
        return usePasv == that.usePasv &&
               Objects.equals(username, that.username) &&
               Objects.equals(password, that.password) &&
               Objects.equals(dataType, that.dataType) &&
               Objects.equals(transferMode, that.transferMode) &&
               Objects.equals(defaultDirectory, that.defaultDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), username, password, dataType, transferMode, usePasv, defaultDirectory);
    }

    @Override
    public String toString() {
        return "FtpBinding{" + "username='" + username + '\'' + ", password='" +
               ((password != null) ? "xxxx" : "<null>") + '\'' + ", dataType='" + dataType + '\'' +
               ", transferMode='" + transferMode + '\'' + ", usePasv=" + usePasv +
               ", defaultDirectory='" + defaultDirectory + '\'' + "} " + super.toString();
    }
}
