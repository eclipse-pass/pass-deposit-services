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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class FtpBinding extends ProtocolBinding {

    static final String PROTO = "ftp";

    private String username;

    private String password;

    @JsonProperty("server-fqdn")
    private String serverFqdn;

    @JsonProperty("server-port")
    private String serverPort;

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

    public String getServerFqdn() {
        return serverFqdn;
    }

    public void setServerFqdn(String serverFqdn) {
        this.serverFqdn = serverFqdn;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FtpBinding that = (FtpBinding) o;
        return usePasv == that.usePasv &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(serverFqdn, that.serverFqdn) &&
                Objects.equals(serverPort, that.serverPort) &&
                Objects.equals(dataType, that.dataType) &&
                Objects.equals(transferMode, that.transferMode) &&
                Objects.equals(defaultDirectory, that.defaultDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), username, password, serverFqdn, serverPort, dataType, transferMode, usePasv, defaultDirectory);
    }

}
