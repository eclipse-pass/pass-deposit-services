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

import org.dataconservancy.pass.deposit.assembler.PackageOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AssemblerOptions {

    private String compression;

    private String archive;

    private List<String> algorithms;

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getArchive() {
        return archive;
    }

    public void setArchive(String archive) {
        this.archive = archive;
    }

    public List<String> getAlgorithms() {
        return algorithms;
    }

    public void setAlgorithms(List<String> algorithms) {
        this.algorithms = algorithms;
    }

    public Map<String, Object> asOptionsMap() {
        return new HashMap<String, Object>() {
            {
                put(PackageOptions.COMPRESSION_KEY, PackageOptions.COMPRESSION.valueOf(compression.toUpperCase()));
                put(PackageOptions.ARCHIVE_KEY, PackageOptions.ARCHIVE.valueOf(archive.toUpperCase()));
                put(PackageOptions.ALGO_KEY,
                        algorithms.stream()
                                .map(algo -> PackageOptions.Algo.valueOf(algo.toUpperCase()))
                                .collect(Collectors.toList()));
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AssemblerOptions that = (AssemblerOptions) o;
        return Objects.equals(compression, that.compression) && Objects.equals(archive, that.archive) && Objects.equals(algorithms, that.algorithms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(compression, archive, algorithms);
    }
}
