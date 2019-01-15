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
package org.dataconservancy.pass.deposit.assembler;

/**
 * Supported options for building packages
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface PackageOptions {

    /**
     * Packaging specification
     */
    interface Spec {

        /**
         * Specification key
         */
        String KEY = "SPEC";

    }

    /**
     * Package compression
     */
    interface Compression {

        /**
         * Compression key
         */
        String KEY = "COMPRESSION";

        /**
         * Supported compression
         */
        enum OPTS {
            NONE,
            GZIP,
            BZIP2,
            ZIP
        }

    }

    /**
     * Archive formats
     */
    interface Archive {

        /**
         * Archive key
         */
        String KEY = "ARCHIVE";

        /**
         * Supported archive formats
         */
        enum OPTS {
            NONE,
            TAR,
            ZIP
        }

    }

    /**
     * Checksum algorithms
     */
    interface Checksum {

        /**
         * Algorithm key
         */
        String KEY = "ALGO";

        /**
         * Supported checksum algorithms
         */
        enum OPTS {
            SHA512,
            SHA256,
            MD5
        }

    }

}
