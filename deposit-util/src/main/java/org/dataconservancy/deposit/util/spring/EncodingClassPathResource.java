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
package org.dataconservancy.deposit.util.spring;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Spring ClassPathResource which exposes <em>encoded</em> paths, but performs internal operations such as resolving
 * an {@code InputStream} using the <em>decoded</em> path.
 * <p>
 * {@link #getPath()} will always return the path supplied to this class on construction, as it is {@code final}.  If
 * an <em>encoded</em> path is supplied on construction, then {@code getPath()} will return the encoded path.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class EncodingClassPathResource extends ClassPathResource {

    public static final String RESOURCE_KEY = "encodedclasspath:";

    private String encodedPath;

    private String decodedPath;

    @Nullable
    private ClassLoader classLoader;

    @Nullable
    private Class<?> clazz;

    public EncodingClassPathResource(String encodedPath) {
        this(encodedPath, (ClassLoader) null);
    }

    public EncodingClassPathResource(String encodedPath, @Nullable ClassLoader classLoader) {
        super(encodedPath, classLoader);
        this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
        this.encodedPath = StringUtils.cleanPath(encodedPath);
        if (this.encodedPath.startsWith("/")) {
            this.encodedPath = this.encodedPath.substring(1);
        }
        this.decodedPath = decodePath(this.encodedPath);
    }

    public EncodingClassPathResource(String encodedPath, @Nullable Class<?> clazz) {
        super(encodedPath, clazz);
        this.clazz = clazz;
        this.encodedPath = StringUtils.cleanPath(encodedPath);
        this.decodedPath = decodePath(this.encodedPath);
    }

    public EncodingClassPathResource(String encodedPath, @Nullable ClassLoader classLoader, @Nullable Class<?> clazz) {
        super(encodedPath, classLoader, clazz);
        this.clazz = clazz;
        this.classLoader = classLoader;
        this.encodedPath = StringUtils.cleanPath(encodedPath);
        this.decodedPath = decodePath(this.encodedPath);
    }

    /**
     * Returns the URL of the resource, which will be encoded per normal URL encoding rules.
     * <p>
     * Internally, the <em>decoded</em> resource path is used to look up the resource, and the path is encoded by virtue
     * of being returned as a URL.
     * </p>
     *
     * @return the URL which
     */
    @Nullable
    @Override
    protected URL resolveURL() {
        if (this.clazz != null) {
            return this.clazz.getResource(this.decodedPath);
        } else if (this.classLoader != null) {
            return this.classLoader.getResource(this.decodedPath);
        } else {
            return ClassLoader.getSystemResource(this.decodedPath);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is;
        if (this.clazz != null) {
            is = this.clazz.getResourceAsStream(this.decodedPath);
        } else if (this.classLoader != null) {
            is = this.classLoader.getResourceAsStream(this.decodedPath);
        } else {
            is = ClassLoader.getSystemResourceAsStream(this.decodedPath);
        }
        if (is == null) {
            throw new FileNotFoundException(getDescription() + " cannot be opened because it does not exist");
        }
        return is;
    }

    @Override
    public Resource createRelative(String relativePath) {
        String pathToUse = StringUtils.applyRelativePath(this.encodedPath, relativePath);
        return (this.clazz != null ? new EncodingClassPathResource(pathToUse, this.clazz) :
                new EncodingClassPathResource(pathToUse, this.classLoader));
    }

    @Nullable
    @Override
    public String getFilename() {
        return StringUtils.getFilename(this.encodedPath);
    }

    @Override
    public String getDescription() {
        StringBuilder builder = new StringBuilder("encoded class path resource [");
        String pathToUse = encodedPath;
        if (this.clazz != null && !pathToUse.startsWith("/")) {
            builder.append(ClassUtils.classPackageAsResourcePath(this.clazz));
            builder.append('/');
        }
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1);
        }
        builder.append(pathToUse);
        builder.append(']');
        return builder.toString();
    }

    static String encodePath(String path) {
        try {
            return URLEncoder.encode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    static String decodePath(String path) {
        try {
            return URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
