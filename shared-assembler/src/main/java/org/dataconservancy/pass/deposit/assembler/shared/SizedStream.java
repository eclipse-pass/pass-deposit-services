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

import java.io.InputStream;

/**
 * Supplies an {@code InputStream} and its length.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface SizedStream {

    /**
     * The number of bytes to be supplied by the {@link #getInputStream() InputStream}
     *
     * @return the size of the {@code InputStream}, always an integer 0 or greater
     */
    long getLength();

    /**
     * An {@code InputStream} with size {@link #getLength()}
     *
     * @return the {@code InputStream}
     */
    InputStream getInputStream();

}
