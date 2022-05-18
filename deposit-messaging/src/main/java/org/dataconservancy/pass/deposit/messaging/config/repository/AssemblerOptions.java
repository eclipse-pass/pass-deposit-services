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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Archive;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Checksum;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.Compression;

/**
 * Represents the {@code options} provided to an Assembler, typically serialized from JSON.  This class encapsulates
 * the {@code options} object in the example serialization below:
 * <pre>
 *     "BagIt": {
 *     "assembler": {
 *       "specification": "simple",
 *       "beanName": "simpleAssembler",
 *       "options": {
 *         "archive": "ZIP",
 *         "compression": "NONE",
 *         "algorithms": [
 *           "sha512",
 *           "md5"
 *         ],
 *         "baginfo-template-resource": "/bag-info.hbm"
 *       }
 *     }
 * </pre>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AssemblerOptions {

    private String compression;

    private String archive;

    private List<String> algorithms;

    /*
     * Carries additional Assembler options that are not defined as fields on this class.  Allows for arbitrary content
     * to be added to the Assembler configuration in {@code repositories.json} without causing errors in serialization,
     * and without adding new fields and accessors to this class.
     */
    private Map<String, Object> optionsMap = new HashMap<>();

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

    /**
     * Includes only those configuration keys that are not explicitly defined (i.e. lack accessor methods) in this
     * AssemblerOptions.  Given the following Assembler configuration in {@code repositories.json}:
     * <pre>
     *     "BagIt": {
     *     "assembler": {
     *       "specification": "simple",
     *       "beanName": "simpleAssembler",
     *       "options": {
     *         "archive": "ZIP",
     *         "compression": "NONE",
     *         "algorithms": [
     *           "sha512",
     *           "md5"
     *         ],
     *         "baginfo-template-resource": "/bag-info.hbm"
     *       }
     *     }
     * </pre>
     *
     * The {@code Map} returned by this method will only contain {@code baginfo-template-resource}.  The other
     * properties present in the {@code options} JSON object have defined accessors in AssemblerOptions, so they are not
     * included in the returned {@code Map}.  If <em>every</em> key is desired, see {@link #asOptionsMap()}.
     *
     * @return every key and value in the {@code options} JSON object that lacks accessor methods
     * @see #getOptionsMap()
     * @see #getAlgorithms()
     * @see #getArchive()
     * @see #getCompression()
     */
    @JsonAnyGetter
    public Map<String, Object> getOptionsMap() {
        return optionsMap;
    }

    @JsonAnySetter
    public void add(String key, Object value) {
        optionsMap.put(key, value);
    }

    /**
     * Includes every configuration key in this AssemblerOptions as a {@code Map}.  This method differs from {@link
     * #getOptionsMap()} by including <em>every</em> configuration key in the returned {@code Map}, while {@link
     * #getOptionsMap()} only includes "extra" options.  Given the following Assembler configuration in {@code
     * repositories.json}
     * <pre>
     *     "BagIt": {
     *     "assembler": {
     *       "specification": "simple",
     *       "beanName": "simpleAssembler",
     *       "options": {
     *         "archive": "ZIP",
     *         "compression": "NONE",
     *         "algorithms": [
     *           "sha512",
     *           "md5"
     *         ],
     *         "baginfo-template-resource": "/bag-info.hbm"
     *       }
     *     }
     * </pre>
     *
     * The {@code Map} returned by this method will contain every key and value in the {@code options} JSON object,
     * including {@code baginfo-template-resource}.
     *
     * @return every key and value in {@code options} JSON object as a {@code Map}
     * @see #getOptionsMap()
     */
    public Map<String, Object> asOptionsMap() {
        return new HashMap<String, Object>() {
            {
                if (compression != null) {
                    put(Compression.KEY, Compression.OPTS.valueOf(compression.toUpperCase()));
                }
                if (archive != null) {
                    put(Archive.KEY, Archive.OPTS.valueOf(archive.toUpperCase()));
                }
                if (algorithms != null) {
                    put(Checksum.KEY,
                        algorithms.stream()
                                  .map(algo -> Checksum.OPTS.valueOf(algo.toUpperCase()))
                                  .collect(Collectors.toList()));
                }
                this.putAll(optionsMap);
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AssemblerOptions that = (AssemblerOptions) o;
        return Objects.equals(compression, that.compression) && Objects.equals(archive, that.archive) && Objects.equals(
            algorithms, that.algorithms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(compression, archive, algorithms);
    }

    @Override
    public String toString() {
        return "AssemblerOptions{" + "compression='" + compression + '\'' + ", archive='" + archive + '\'' + ", " +
               "algorithms=" + algorithms + '}';
    }

}
