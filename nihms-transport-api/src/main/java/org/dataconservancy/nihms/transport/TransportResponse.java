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

public interface TransportResponse {

    /**
     * Whether or not transferring the stream of bytes to the target destination succeeded without error.  If an error
     * was encountered transferring the bytes, then {@link #error()} can be invoked to retrieve the stack trace.
     *
     * @return {@code false} if there was an error transferring the byte stream to the target system
     */
    boolean success();

    /**
     * Will contain the {@code Throwable} in the case an error is encountered transferring the bytes to the target
     * system.
     *
     * @return the cause of the error, may be {@code null}
     */
    Throwable error();

}
