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
package org.dataconservancy.pass.deposit.transport.fs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.BASEDIR;
import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.CREATE_IF_MISSING;
import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.OVERWRITE;
import static org.dataconservancy.pass.model.RepositoryCopy.CopyStatus.ACCEPTED;

/**
 * Writes {@link PackageStream}s to a directory on the filesystem.
 * Hints accepted by this transport are:
 * <dl>
 *  <dt>baseDir</dt>
 *  <dd>the absolute path to a directory on the filesystem for writing packages</dd>
 *  <dt>createIfMissing</dt>
 *  <dd>create the baseDir if it doesn't exist</dd>
 *  <dt>overwrite</dt>
 *  <dd>overwrite existing packages</dd>
 * </dl>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@Component
public class FilesystemTransport implements Transport {

    private CriticalRepositoryInteraction cri;

    private File baseDir;

    private boolean createIfMissing;

    private boolean overwrite;

    public FilesystemTransport(CriticalRepositoryInteraction cri) {
        this.cri = cri;
    }

    @Override
    public TransportSession open(Map<String, String> hints) {
        baseDir = new File(hints.get(BASEDIR));
        createIfMissing = Boolean.parseBoolean(hints.getOrDefault(CREATE_IF_MISSING, "true"));
        overwrite = Boolean.parseBoolean(hints.getOrDefault(OVERWRITE, "false"));

        if (!baseDir.exists()) {
            if (createIfMissing) {
                try {
                    FileUtils.forceMkdir(baseDir);
                } catch (IOException e) {
                    throw new RuntimeException("Error creating base directory '" + baseDir + "' " + e.getMessage(), e);
                }
            } else {
                throw new RuntimeException("Base directory '" + baseDir + "' does not exist.");
            }
        }

        return new FilesystemTransportSession();
    }

    class FilesystemTransportSession implements TransportSession {

        @Override
        public TransportResponse send(PackageStream packageStream, Map<String, String> metadata) {
            String filename = packageStream.metadata().name();
            AtomicReference<Exception> transportException = new AtomicReference<>();

            File outputFile = new File(baseDir, filename);

            if (!outputFile.exists() || overwrite) {
                try (InputStream in = packageStream.open(); OutputStream out = new FileOutputStream(outputFile)) {
                    IOUtils.copy(in, out);
                } catch (Exception e) {
                    transportException.set(e);
                }
            } else {
                transportException.set(new IOException("Output file '" + outputFile + "' already exists, and " +
                        "'overwrite' flag is 'false'"));
            }

            return new TransportResponse() {
                @Override
                public boolean success() {
                    return transportException.get() == null;
                }

                @Override
                public Throwable error() {
                    return transportException.get();
                }

                @Override
                public void onSuccess(Submission submission, Deposit deposit, RepositoryCopy repositoryCopy) {
                    CriticalResult<RepositoryCopy, RepositoryCopy> result =
                            cri.performCritical(repositoryCopy.getId(), RepositoryCopy.class,
                                    (rc) -> true,
                                    (rc) -> rc.getCopyStatus() == ACCEPTED && rc.getExternalIds().size() > 0,
                                    (rc) -> {
                                        rc.setExternalIds(Collections.singletonList(outputFile.getAbsolutePath()));
                                        rc.setCopyStatus(ACCEPTED);
                                        return rc;
                                    });

                    if (!result.success()) {
                        if (result.throwable().isPresent()) {
                            throw new RuntimeException(result.throwable().get());
                        } else {
                            throw new RuntimeException("Failed to update " + repositoryCopy.getId());
                        }
                    }
                }
            };
        }

        @Override
        public boolean closed() {
            return false;
        }

        @Override
        public void close() throws Exception {
            // no-op
        }

    }

}
