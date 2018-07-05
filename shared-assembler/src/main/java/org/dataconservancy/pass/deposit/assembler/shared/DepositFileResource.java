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

import org.dataconservancy.pass.deposit.model.DepositFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;

/**
 * A Spring {@code Resource} paired with its {@link DepositFile}, providing access to "logical" resource metadata.
 * <p>
 * Users of {@code DepositFileResource} may prefer the {@link #getDepositFile() DepositFile} for metadata rather than
 * than the metadata provided by the Spring {@code Resource}.  For example, {@link Resource#getFilename()} will return
 * a <em>URL path</em> as a resource name when the {@code Resource} is remote (i.e. implemented as {@link UrlResource})
 * vs a <em>filesystem path</em> when the resource is local (i.e. implemented as a {@link FileSystemResource}).
 * </p>
 * <p>
 * The {@link DepositFile} associated with the {@code Resource} is independent of the {@code Resource}
 * <em>implementation</em>.  This allows the user of {@code DepositFileResource}, for example, to obtain the logical
 * name of a resource (via {@link DepositFile#getName()}) independent of its location.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DepositFileResource implements Resource {

    private Resource resource;

    private DepositFile depositFile;

    public DepositFileResource(DepositFile depositFile) {
        if (depositFile == null) {
            throw new IllegalArgumentException("DepositFile must not be null.");
        }

        this.depositFile = depositFile;
    }

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

    public DepositFile getDepositFile() {
        return depositFile;
    }

    public void setDepositFile(DepositFile depositFile) {
        if (depositFile == null) {
            throw new IllegalArgumentException("DepositFile must not be null.");
        }
        this.depositFile = depositFile;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Spring Resource must not be null.");
        }
        this.resource = resource;
    }

    @Override
    public boolean exists() {
        assertState();
        return resource.exists();
    }

    @Override
    public boolean isReadable() {
        assertState();
        return resource.isReadable();
    }

    @Override
    public boolean isOpen() {
        assertState();
        return resource.isOpen();
    }

    @Override
    public boolean isFile() {
        assertState();
        return resource.isFile();
    }

    @Override
    public URL getURL() throws IOException {
        assertState();
        return resource.getURL();
    }

    @Override
    public URI getURI() throws IOException {
        assertState();
        return resource.getURI();
    }

    @Override
    public File getFile() throws IOException {
        assertState();
        return resource.getFile();
    }

    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        assertState();
        return resource.readableChannel();
    }

    @Override
    public long contentLength() throws IOException {
        assertState();
        return resource.contentLength();
    }

    @Override
    public long lastModified() throws IOException {
        assertState();
        return resource.lastModified();
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        assertState();
        return resource.createRelative(relativePath);
    }

    @Nullable
    @Override
    public String getFilename() {
        assertState();
        return resource.getFilename();
    }

    @Override
    public String getDescription() {
        assertState();
        return resource.getDescription();
    }

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
