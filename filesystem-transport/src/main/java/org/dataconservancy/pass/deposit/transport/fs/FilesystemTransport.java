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

import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.BASEDIR;
import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.CREATE_IF_MISSING;
import static org.dataconservancy.pass.deposit.transport.fs.FilesystemTransportHints.OVERWRITE;
import static org.dataconservancy.pass.model.Deposit.DepositStatus.SUBMITTED;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.dataconservancy.pass.deposit.transport.TransportSession;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Deposit.DepositStatus;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.RepositoryCopy;
import org.dataconservancy.pass.model.RepositoryCopy.CopyStatus;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction;
import org.dataconservancy.pass.support.messaging.cri.CriticalRepositoryInteraction.CriticalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemTransport.class);

    private CriticalRepositoryInteraction cri;

    private File baseDir;

    private boolean createIfMissing;

    private boolean overwrite;

    @Autowired
    public FilesystemTransport(CriticalRepositoryInteraction cri) {
        this.cri = cri;
    }

    @Override
    public PROTOCOL protocol() {
        return PROTOCOL.filesystem;
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

                /**
                 * If the package file created by
                 * {@link FilesystemTransport.FilesystemTransportSession#send(PackageStream, Map)
                 * send(...)} exists, then the {@code RepositoryCopy.CopyStatus} is updated to {@code COMPLETE}, the
                 * {@code RepositoryCopy.externalIds} are updated to contain the path to the package file, and the
                 * {@code Deposit.DepositStatus} is updated to {@code ACCEPTED}.
                 *
                 * @param submission the Submission that resulted in success
                 * @param deposit the Deposit associated with the Submission
                 * @param repositoryCopy the RepositoryCopy encapsulating the package file created by this {@code
                 *                       Transport}
                 */
                @Override
                public void onSuccess(Submission submission, Deposit deposit, RepositoryCopy repositoryCopy) {
                    LOG.trace("Invoking onSuccess for tuple [{} {} {}]",
                              submission.getId(), deposit.getId(), repositoryCopy.getId());
                    CriticalResult<RepositoryCopy, RepositoryCopy> rcCr =
                        cri.performCritical(repositoryCopy.getId(), RepositoryCopy.class,
                                            (rc) -> outputFile.exists(),
                                            (rc) -> rc.getCopyStatus() == CopyStatus.COMPLETE
                                                    && rc.getExternalIds().size() > 0
                                                    && rc.getAccessUrl() != null,
                                            (rc) -> {
                                                rc.getExternalIds().add(outputFile.toURI().toString());
                                                rc.setCopyStatus(CopyStatus.COMPLETE);
                                                rc.setAccessUrl(outputFile.toURI());
                                                return rc;
                                            });

                    verifySuccess(repositoryCopy, rcCr);

                    LOG.trace("onSuccess updated RepositoryCopy {}", rcCr.resource().get().getId());

                    CriticalResult<Deposit, Deposit> depositCr =
                        cri.performCritical(deposit.getId(), Deposit.class,
                                            (criDeposit) -> SUBMITTED == criDeposit.getDepositStatus(),
                                            (criDeposit) -> DepositStatus.ACCEPTED == criDeposit.getDepositStatus(),
                                            (criDeposit) -> {
                                                criDeposit.setDepositStatus(DepositStatus.ACCEPTED);
                                                return criDeposit;
                                            });

                    verifySuccess(deposit, depositCr);

                    LOG.trace("onSuccess updated Deposit {}", depositCr.resource().get().getId());
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

    private void verifySuccess(PassEntity entity, CriticalResult<?, ?> result) {
        if (!result.success()) {
            if (result.throwable().isPresent()) {
                throw new RuntimeException(result.throwable().get());
            } else {
                throw new RuntimeException("Failed to update " + entity.getId());
            }
        }
    }

}
