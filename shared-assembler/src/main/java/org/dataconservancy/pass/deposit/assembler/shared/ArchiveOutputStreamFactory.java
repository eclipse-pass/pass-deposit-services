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

import java.io.OutputStream;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.dataconservancy.pass.deposit.assembler.PackageStream;

/**
 * Responsible for creating the {@link ArchiveOutputStream} used to write the {@link PackageStream}.  Implementations
 * are expected to wrap the supplied {@code OutputStream} with an {@code ArchiveOutputStream} configured per the
 * the package options.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface ArchiveOutputStreamFactory {

    /**
     * Returns an {@code ArchiveOutputStream}, wrapping the supplied {@code OutputStream} according to the package
     * options.
     *
     * @param packageOptions the package options, including compression and archive formats
     * @param toWrap         the output stream to be wrapped by the returned {@code ArchiveOutputStream}
     * @return the configured {@code ArchiveOutputStream} ready for writing
     */
    ArchiveOutputStream newInstance(Map<String, Object> packageOptions, OutputStream toWrap);

}
