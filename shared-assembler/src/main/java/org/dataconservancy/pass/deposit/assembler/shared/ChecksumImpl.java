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
package org.dataconservancy.pass.deposit.assembler.shared;

import org.dataconservancy.pass.deposit.assembler.PackageOptions.Checksum;
import org.dataconservancy.pass.deposit.assembler.PackageStream;

/**
 * Default implementation of {@link PackageStream.Checksum}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class ChecksumImpl implements PackageStream.Checksum {

    private Checksum.OPTS algorithm;

    private byte[] value;

    private String base64;

    private String hex;

    public ChecksumImpl(Checksum.OPTS algorithm, byte[] value, String base64, String hex) {
        this.algorithm = algorithm;
        this.value = value;
        this.base64 = base64;
        this.hex = hex;
    }

    @Override
    public Checksum.OPTS algorithm() {
        return algorithm;
    }

    @Override
    public byte[] value() {
        return value;
    }

    @Override
    public String asBase64() {
        return base64;
    }

    @Override
    public String asHex() {
        return hex;
    }

    @Override
    public String toString() {
        return "ChecksumImpl{" +
               "algorithm=" + algorithm +
               ", value=[binary value not shown]" +
               ", base64='" + base64 + '\'' +
               ", hex='" + hex + '\'' +
               '}';
    }
}
