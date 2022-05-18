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

import java.util.Arrays;

/**
 * Enumerates file extensions for various archive and compression formats
 */
public enum Extension {

    /**
     * Extension for the TAR format, {@code tar}
     */
    TAR("tar"),

    /**
     * Extension for the GZIP format, {@code gz}
     */
    GZ("gz"),

    /**
     * Extension for the ZIP format, {@code zip}
     */
    ZIP("zip"),

    /**
     * Extension for the BZIP2 format, {@code bz2}
     */
    BZ2("bz2");

    /**
     * The extension as it would be used in a file name, with no preceding or succeeding periods
     */
    private String ext;

    /**
     * Construct an extension for the supplied string.
     *
     * @param ext the extension as it would be used in a file name
     */
    Extension(String ext) {
        this.ext = ext;
    }

    /**
     * The extension as it would be used in a file name, with no preceding or succeeding periods, all lower-case
     *
     * @return the extension as it would be used in a file name
     */
    public String getExt() {
        return ext;
    }

    /**
     * Parse an {@code Extension} from the string form as it would be used in a file name.  Note that this form should
     * be all lower-case, with no preceding or succeeding periods.
     *
     * @param ext the string form of a file extension
     * @return the corresponding {@code Extension}
     * @throws IllegalArgumentException if the {@code ext} does not correspond to an existing {@code Extension}
     */
    public static Extension parseExt(String ext) {
        return Arrays.stream(values())
                     .filter(candidateExt -> candidateExt.getExt().equals(ext))
                     .findAny()
                     .orElseThrow(() -> new IllegalArgumentException("No Extension exists for '" + ext + "'"));
    }
}
