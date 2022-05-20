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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;

import org.dataconservancy.pass.deposit.model.DepositFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;

/**
 * A Spring {@code Resource} paired with its {@link DepositFile}, providing access to resource metadata (e.g. file name
 * or resource location) and the {@link Resource#getInputStream() byte stream} for the resource.
 * <p>
 * Users of {@code DepositFileResource} may prefer the {@link #getDepositFile() DepositFile} for metadata rather than
 * than the metadata provided by the Spring {@code Resource}.  For example, {@link Resource#getFilename()} will return
 * a <em>URL path</em> as a resource name when the {@code Resource} is remote (i.e. implemented as {@link UrlResource})
 * vs a <em>filesystem path</em> when the resource is local (i.e. implemented as a {@link FileSystemResource}).  The
 * {@link DepositFile} associated with the {@code Resource} is independent of the {@code Resource}
 * <em>implementation</em>.  This allows the user of {@code DepositFileResource} to obtain the logical name of a
 * resource (via {@link DepositFile#getName()}) independent of its location.  The underlying Spring {@code Resource}
 * insures that the bytes for the {@code DepositFile} are available, even if the {@code Resource} requires authorization
 * to retrieve.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositFileResource implements Resource {

    private Resource resource;

    private DepositFile depositFile;

    /**
     * Create a new instance.  The underlying {@code Resource} must be {@link #setResource(Resource) set} after
     * construction.
     *
     * @param depositFile the DepositFile
     */
    public DepositFileResource(DepositFile depositFile) {
        if (depositFile == null) {
            throw new IllegalArgumentException("DepositFile must not be null.");
        }

        this.depositFile = depositFile;
    }

    /**
     * Create a new instance with the {@code resource} supplying the bytes for the {@code depositFile}.
     *
     * @param depositFile the DepositFile
     * @param resource    the underlying Resource
     */
    public DepositFileResource(DepositFile depositFile, Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Spring Resource must not be null.");
        }

        if (depositFile == null) {
            throw new IllegalArgumentException("DepositFile must not be null.");
        }

        this.resource = resource;
        this.depositFile = depositFile;
    }

    /**
     * Obtain the underlying {@code DepositFile}
     *
     * @return the {@code DepositFile}
     */
    public DepositFile getDepositFile() {
        return depositFile;
    }

    /**
     * Set the underlying {@code DepositFile}
     *
     * @param depositFile the {@code DepositFile}
     */
    public void setDepositFile(DepositFile depositFile) {
        if (depositFile == null) {
            throw new IllegalArgumentException("DepositFile must not be null.");
        }
        this.depositFile = depositFile;
    }

    /**
     * Obtain the underlying Spring {@code Resource}.  All {@link Resource} methods on this class forward to the
     * instance returned by this method.  If this method returns {@code null}, all {@code Resource} methods will throw
     * an {@code IllegalStateException} until the {@code Resource} is {@link #setResource(Resource) set}.
     *
     * @return the Spring {@code Resource}
     */
    public Resource getResource() {
        return resource;
    }

    /**
     * Set the underlying Spring {@code Resource}.  All {@link Resource} methods on this class forward to the instance
     * set here.  Until this method is invoked with a non-{@code null} {@code Resource}, all {@code Resource} methods on
     * this class will throw {@code IllegalArgumentException}.
     *
     * @param resource the Spring {@code Resource}
     * @throws IllegalArgumentException if {@code resource} is {@code null}
     */
    public void setResource(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Spring Resource must not be null.");
        }
        this.resource = resource;
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean exists() {
        assertState();
        return resource.exists();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean isReadable() {
        assertState();
        return resource.isReadable();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean isOpen() {
        assertState();
        return resource.isOpen();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public boolean isFile() {
        assertState();
        return resource.isFile();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public URL getURL() throws IOException {
        assertState();
        return resource.getURL();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public URI getURI() throws IOException {
        assertState();
        return resource.getURI();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public File getFile() throws IOException {
        assertState();
        return resource.getFile();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        assertState();
        return resource.readableChannel();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public long contentLength() throws IOException {
        assertState();
        return resource.contentLength();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public long lastModified() throws IOException {
        assertState();
        return resource.lastModified();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public Resource createRelative(String relativePath) throws IOException {
        assertState();
        return resource.createRelative(relativePath);
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Nullable
    @Override
    public String getFilename() {
        assertState();
        return resource.getFilename();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public String getDescription() {
        assertState();
        return resource.getDescription();
    }

    /**
     * <em>Implementation note:</em> forwards to the underlying Spring {@link Resource}.
     *
     * {@inheritDoc}
     *
     * @return {@inheritDoc}
     * @throws IllegalStateException if the underlying Spring {@code Resource} has not been set
     */
    @Override
    public InputStream getInputStream() throws IOException {
        assertState();
        return resource.getInputStream();
    }

    private void assertState() {
        if (this.resource == null) {
            throw new IllegalStateException("The delegate Spring Resource is null: has setResource(Resource) been " +
                                            "called?");
        }
    }

    @Override
    public String toString() {
        return "DepositFileResource{" + "resource=" + resource + ", depositFile=" + depositFile + '}';
    }
}
