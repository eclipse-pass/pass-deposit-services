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

import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositSubmission;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Used in tests to bypass the entire assembly of a package.  Instead, supply a pre-built package along with a
 * checksum and package specification string, and this Assembler will simply stream back the pre-build package.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class PreassembledAssembler implements Assembler {

    private String spec;

    private PackageStream.Checksum checksum;

    private File packageFile;

    public PreassembledAssembler() {

    }

    public PreassembledAssembler(String spec, PackageStream.Checksum checksum, File packageFile) {
        this.spec = spec;
        this.checksum = checksum;
        this.packageFile = packageFile;
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

    @Override
    public PackageStream assemble(DepositSubmission submission, Map<String, Object> options) {
        return new PackageStream() {
            @Override
            public InputStream open() {
                try {
                    return new FileInputStream(packageFile);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
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
                        return packageFile.getName();
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
                        }

                        switch (archive()) {
                            case ZIP:
                                return "application/zip";
                            case TAR:
                                return "application/tar";
                        }

                        return null;
                    }

                    @Override
                    public long sizeBytes() {
                        return packageFile.length();
                    }

                    @Override
                    public boolean compressed() {
                        return Compression.OPTS.NONE != compression();
                    }

                    @Override
                    public Compression.OPTS compression() {
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
                };
            }
        };
    }

}
