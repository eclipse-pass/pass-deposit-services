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

package org.dataconservancy.nihms.transport;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Future;

public interface TransportSession extends AutoCloseable {

    /**
     * Streams the supplied {@code content} to a destination associated with this session.  The implementation should
     * use the supplied {@code destinationResource} to identify the submitted content in the target submission system.
     * Implementations are responsible for creating the necessary structure on the target system to accept the
     * {@code content} (e.g. creating a destination directory or collection in the target system that will accept the
     * named resource).
     *
     * @param destinationResource
     * @param content
     * @return
     */
    TransportResponse send(String destinationResource, InputStream content);

    TransportResponse send(String destinationResource, Map<String, String> metadata, InputStream content);

    boolean closed();

}
