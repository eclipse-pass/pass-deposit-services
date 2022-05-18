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
package org.apache.commons.io.input;

import static java.util.Base64.getEncoder;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.dataconservancy.pass.deposit.assembler.PackageOptions.Checksum;
import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;
import org.dataconservancy.pass.deposit.assembler.shared.ChecksumImpl;

/**
 * Computes a digest over the observed bytes, and applies it to the {@link ResourceBuilder}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DigestObserver extends ResourceBuilderObserver {

    private Checksum.OPTS algo;

    private MessageDigest digest;

    public DigestObserver(ResourceBuilder builder, Checksum.OPTS algorithm) {
        super(builder);
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm must not be null.");
        }

        this.algo = algorithm;

        try {
            switch (this.algo) {
                case MD5:
                    this.digest = MessageDigest.getInstance("MD5");
                    break;
                case SHA256:
                    this.digest = MessageDigest.getInstance("SHA-256");
                    break;
                case SHA512:
                    this.digest = MessageDigest.getInstance("SHA-512");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown algorithm: " + algo.name());
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unable to obtain MessageDigest instance for algorithm: " + algo.name());
        }
    }

    @Override
    void data(int pByte) throws IOException {
        digest.update((byte) pByte);
    }

    @Override
    void data(byte[] pBuffer, int pOffset, int pLength) throws IOException {
        digest.update(pBuffer, pOffset, pLength);
    }

    @Override
    void finished() throws IOException {
        if (!isFinished()) {
            byte[] value = this.digest.digest();
            builder.checksum(new ChecksumImpl(algo, value, getEncoder().encodeToString(value), encodeHexString(value)));
        }
        super.finished();
    }
}
