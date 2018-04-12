/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.nihms.assembler.nihmsnative;

import org.dataconservancy.nihms.assembler.Assembler;
import org.dataconservancy.nihms.assembler.PackageStream;
import org.dataconservancy.nihms.model.DepositFile;
import org.dataconservancy.nihms.model.DepositSubmission;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.MalformedURLException;
import java.util.List;
import java.util.stream.Collectors;

public class NihmsAssembler implements Assembler {

    private static final String ERR_MAPPING_LOCATION = "Unable to resolve the location of a submitted file ('%s') to a Spring Resource type.";

    private static final String FILE_PREFIX = "file:";

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String WILDCARD_CLASSPATH_PREFIX = "classpath*:";

    private static final String HTTP_PREFIX = "http:";

    private static final String HTTPS_PREFIX = "https:";

    /**
     * Assembles Java {@code Object} references to <em>{@code InputStream}s</em> for each file in the package.  The
     * references are supplied to the {@code NihmsPackageStream} implementation, which does the heavy lifting of
     * actually creating a stream for the tar.gz archive.
     *
     * @param submission the custodial content being packaged
     * @return a PackageStream which actually creates the stream for the tar.gz archive
     */
    @Override
    public PackageStream assemble(DepositSubmission submission) {

        // Prepare manifest and a serialization of the manifest
        StreamingSerializer manifestSerializer = new NihmsManifestSerializer(submission.getManifest());
        // Prepare metadata and a serialization of the metadata
        StreamingSerializer metadataSerializer = new NihmsMetadataSerializer(submission.getMetadata());
        // Locate byte streams for uploaded manuscript and supplemental data
        List<Resource> fileResources = submission.getFiles()
                .stream()
                .map(DepositFile::getLocation)
                .map(location -> {
                            if (location.startsWith(FILE_PREFIX)) {
                                return new FileSystemResource(location);
                            }
                            if (location.startsWith(CLASSPATH_PREFIX) ||
                                    location.startsWith(WILDCARD_CLASSPATH_PREFIX)) {
                                if (location.startsWith(WILDCARD_CLASSPATH_PREFIX)) {
                                    return new ClassPathResource(location.substring(WILDCARD_CLASSPATH_PREFIX.length()));
                                }
                                return new ClassPathResource(location.substring(CLASSPATH_PREFIX.length()));
                            }
                            if (location.startsWith(HTTP_PREFIX) || location.startsWith(HTTPS_PREFIX)) {
                                try {
                                    return new UrlResource(location);
                                } catch (MalformedURLException e) {
                                    throw new RuntimeException(e.getMessage(), e);
                                }
                            }
                            if (location.contains("/") || location.contains("\\")) {
                                // assume it is a file
                                return new FileSystemResource(location);
                            }

                            throw new RuntimeException(String.format(ERR_MAPPING_LOCATION, location));
                        })
                .collect(Collectors.toList());

        SimpleMetadataImpl md = new SimpleMetadataImpl(sanitize(submission.getName()), -1L);

        return new NihmsPackageStream(manifestSerializer, metadataSerializer, fileResources, md);
    }

    static String sanitize(String name) {
        String result = name
                .chars()
                .filter(NihmsAssembler::isValidChar)
                .mapToObj(c -> Character.toString((char)c))
                .collect(Collectors.joining());

        return result;
    }

    private static boolean isValidChar(int ch) {
        int i = ch & 0x0000FFFF;

        // outside of the latin-1 code block
        if (i >= 0x007f) {
            return false;
        }

        // a - z 0x61 - 0x7a
        if (i >= 0x0061 && i <= 0x007a) {
            return true;
        }

        // A - Z 0x41 - 0x5a
        if (i >= 0x0041 && i <= 0x005a) {
            return true;
        }

        // 0 - 9 0x30 - 0x39
        if (i >= 0x0030 && i <= 0x0039) {
            return true;
        }

        // otherwise it's an illegal character inside of the latin-1 code block
        return false;
    }

}
