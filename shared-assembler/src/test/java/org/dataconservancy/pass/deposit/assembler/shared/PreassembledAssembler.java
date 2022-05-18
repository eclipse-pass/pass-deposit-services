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
package org.dataconservancy.pass.deposit.assembler.shared;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import com.google.gson.JsonObject;
import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.springframework.stereotype.Component;

/**
 * Used in tests to bypass the entire assembly of a package.  Instead, supply a pre-built package along with a
 * checksum and package specification string, and this Assembler will simply stream back the pre-build package.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class PreassembledAssembler implements Assembler {

    private String spec;

    private PackageStream.Checksum checksum;

    private File packageFile;

    private InputStream packageStream;

    private String packageName;

    private long packageLength;

    private Archive.OPTS archive;

    private Compression.OPTS compression;

    /**
     * Constructor that requires the caller to use the accessors to provide state.
     */
    public PreassembledAssembler() {

    }

    /**
     * Constructor with required parameters for streaming back a File.
     *
     * @param spec
     * @param checksum
     * @param packageFile
     */
    public PreassembledAssembler(String spec, PackageStream.Checksum checksum, File packageFile) {
        this.spec = spec;
        this.checksum = checksum;
        this.packageFile = packageFile;
    }

    /**
     * Constructor with required parameters for streaming back an InputStream.
     *
     * TODO: create an InputStream wrapper that encapsulates the required state
     *
     * @param spec
     * @param checksum
     * @param packageName
     * @param packageLength
     * @param archive
     * @param compression
     * @param packageStream
     */
    public PreassembledAssembler(String spec, PackageStream.Checksum checksum, String packageName, long packageLength,
                                 Archive.OPTS archive, Compression.OPTS compression, InputStream packageStream) {
        this.spec = spec;
        this.checksum = checksum;
        this.packageName = packageName;
        this.packageLength = packageLength;
        this.packageStream = packageStream;
        this.compression = compression;
        this.archive = archive;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public PackageStream.Checksum getChecksum() {
        return checksum;
    }

    public void setChecksum(PackageStream.Checksum checksum) {
        this.checksum = checksum;
    }

    public File getPackageFile() {
        return packageFile;
    }

    public void setPackageFile(File packageFile) {
        this.packageFile = packageFile;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public InputStream getPackageStream() {
        return packageStream;
    }

    public void setPackageStream(InputStream packageStream) {
        this.packageStream = packageStream;
    }

    public long getPackageLength() {
        return packageLength;
    }

    public void setPackageLength(long packageLength) {
        this.packageLength = packageLength;
    }

    public Archive.OPTS getArchive() {
        return archive;
    }

    public void setArchive(Archive.OPTS archive) {
        this.archive = archive;
    }

    public Compression.OPTS getCompression() {
        return compression;
    }

    public void setCompression(Compression.OPTS compression) {
        this.compression = compression;
    }

    @Override
    public PackageStream assemble(DepositSubmission submission, Map<String, Object> options) {
        return new PackageStream() {
            @Override
            public InputStream open() {
                try {
                    if (packageFile != null) {
                        return new FileInputStream(packageFile);
                    }
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }

                return packageStream;
            }

            @Override
            public InputStream open(String packageResource) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterator<Resource> resources() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Metadata metadata() {
                return new Metadata() {
                    @Override
                    public String name() {
                        if (packageName != null) {
                            return packageName;
                        }

                        if (packageFile != null) {
                            return packageFile.getName();
                        }

                        throw new IllegalStateException("No Package name is set.");
                    }

                    @Override
                    public String spec() {
                        return spec;
                    }

                    @Override
                    public String mimeType() {
                        switch (compression()) {
                            case ZIP:
                                return "application/zip";
                            case GZIP:
                                return "application/gzip";
                            case BZIP2:
                                return "application/bzip2";
                            default:
                                break;
                        }

                        switch (archive()) {
                            case ZIP:
                                return "application/zip";
                            case TAR:
                                return "application/tar";
                            default:
                                break;
                        }

                        return null;
                    }

                    @Override
                    public long sizeBytes() {
                        if (packageLength > -1) {
                            return packageLength;
                        }

                        if (packageFile != null) {
                            return packageFile.length();
                        }

                        throw new IllegalStateException("No Package length!");
                    }

                    @Override
                    public boolean compressed() {
                        return Compression.OPTS.NONE != compression();
                    }

                    @Override
                    public Compression.OPTS compression() {
                        if (compression != null) {
                            return compression;
                        }

                        if (packageFile == null) {
                            return Compression.OPTS.NONE;
                        }

                        if (packageFile.getName().endsWith(".gz") || packageFile.getName().endsWith(".gzip")) {
                            return Compression.OPTS.GZIP;
                        }

                        if (packageFile.getName().endsWith(".zip")) {
                            return Compression.OPTS.ZIP;
                        }

                        if (packageFile.getName().endsWith(".bz2") || packageFile.getName().endsWith("bzip2")
                            || packageFile.getName().endsWith(".bzip")) {
                            return Compression.OPTS.BZIP2;
                        }

                        return Compression.OPTS.NONE;
                    }

                    @Override
                    public boolean archived() {
                        return Archive.OPTS.NONE != archive();
                    }

                    @Override
                    public Archive.OPTS archive() {
                        if (archive != null) {
                            return archive;
                        }

                        if (packageFile == null) {
                            return Archive.OPTS.NONE;
                        }

                        if (packageFile.getName().endsWith(".tar")) {
                            return Archive.OPTS.TAR;
                        }

                        if (packageFile.getName().endsWith(".zip")) {
                            return Archive.OPTS.ZIP;
                        }

                        return Archive.OPTS.NONE;
                    }

                    @Override
                    public Checksum checksum() {
                        return checksum;
                    }

                    @Override
                    public Collection<Checksum> checksums() {
                        return Collections.singletonList(checksum());
                    }

                    // TODO implement
                    @Override
                    public JsonObject submissionMeta() {
                        return null;
                    }
                };
            }
        };
    }

}
