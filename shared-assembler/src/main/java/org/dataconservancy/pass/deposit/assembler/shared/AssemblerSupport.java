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

import javafx.beans.binding.When;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.dataconservancy.pass.deposit.assembler.MetadataBuilder;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.ARCHIVE;
import org.dataconservancy.pass.deposit.assembler.PackageOptions.COMPRESSION;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.apache.tika.mime.MediaType.APPLICATION_ZIP;
import static org.dataconservancy.pass.deposit.assembler.PackageOptions.ARCHIVE_KEY;
import static org.dataconservancy.pass.deposit.assembler.PackageOptions.COMPRESSION_KEY;
import static org.dataconservancy.pass.deposit.assembler.PackageOptions.SPEC;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AssemblerSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AssemblerSupport.class);

    /**
     * Copies <em>supported</em> Assembler {@code options} from the supplied {@code Map} and adds them to the package
     * metadata using the supplied {@code MetadataBuilder}.
     * <p>
     * The following defaults are used when the {@code options Map} does not provide a value:
     * </p>
     * <dl>
     *     <dt>{@link MetadataBuilder#spec(String)}</dt>
     *     <dd>{@code ""} (an empty, zero-length string)</dd>
     *     <dt>{@link MetadataBuilder#archive(ARCHIVE)}</dt>
     *     <dd>{@link ARCHIVE#NONE}</dd>
     *     <dt>{@link MetadataBuilder#compression(COMPRESSION)}</dt>
     *     <dd>{@link COMPRESSION#NONE}</dd>
     *     <dt>{@link MetadataBuilder#mimeType(String)}</dt>
     *     <dd>A function of the {@link MetadataBuilder#archive(ARCHIVE) archive} and
     *         {@link MetadataBuilder#compression(COMPRESSION) compression} used</dd>
     * </dl>
     * @param mdb MetadataBuilder to be populated
     * @param options the Assembler options to be copied to the MetadataBuilder
     */
    public static void buildMetadata(MetadataBuilder mdb, Map<String, Object> options) {
        mdb.spec(options.getOrDefault(SPEC, "").toString());
        mdb.archive((ARCHIVE) options.getOrDefault(ARCHIVE_KEY, ARCHIVE.NONE));
        mdb.archived(options.getOrDefault(ARCHIVE_KEY, ARCHIVE.NONE) != ARCHIVE.NONE);
        mdb.compression((COMPRESSION) options.getOrDefault(COMPRESSION_KEY, COMPRESSION.NONE));
        mdb.compressed(options.getOrDefault(COMPRESSION_KEY, COMPRESSION.NONE) != COMPRESSION.NONE);

        PackageStream.Metadata md = mdb.build();

        // Set the IANA mime type of the package stream based on the archive type or compression type

        switch (md.archive()) {
            case ZIP:
                mdb.mimeType(APPLICATION_ZIP.toString());
                break;
            case TAR:
                mdb.mimeType(MediaType.application("x-tar").toString());
                break;
            default:
                mdb.mimeType(MediaType.OCTET_STREAM.toString());
                break;
        }

        switch (md.compression()) {
            case ZIP:
                mdb.mimeType(APPLICATION_ZIP.toString());
                break;
            case BZIP2:
                mdb.mimeType(MediaType.application("x-bzip2").toString());
                break;
            case GZIP:
                mdb.mimeType(MediaType.application("gzip").toString());
                break;
        }
    }

    /**
     * Determine the media type of the supplied InputStream.  If the supplied stream does not support {@code mark(int)}
     * the default mime type 'application/octet-stream' is returned.
     *
     * @param in the InputStream to type
     * @param detector the Tika-based Detector
     * @return the mime type of the InputStream
     * @throws IOException if any errors occur reading bytes from the stream
     */
    public static MediaType detectMediaType(InputStream in, Detector detector) throws IOException {
        // Media type detection may fail because InputStream.mark(...) is not supported
        // by the supplied InputStream
        if (!in.markSupported()) {
            MediaType defaultMimeType = MediaType.OCTET_STREAM;
            LOG.info("Mime type detection of {}@{} failed: mark(int) is not supported.  Using default mime type {}",
                    in.getClass().getName(), Integer.toHexString(System.identityHashCode(in)), defaultMimeType);

            return defaultMimeType;
        }

        // If Metadata supplied to the detector is ever refactored to be non-empty, then the javadoc on
        // AssemblerSupportTest.testResetStreamWhenMarkNotSupported should be consulted.
        return detector.detect(in, new Metadata());
    }

}
