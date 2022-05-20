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

import org.apache.commons.io.input.NullInputStream;

public class MarkUnsupportedInputStream extends NullInputStream {

    public MarkUnsupportedInputStream(long size, boolean throwEofException) {
        super(size, false, throwEofException);
    }

    /**
     * Overrides the behavior of {@link NullInputStream} to be a no-op.  The contract for {@link java.io.InputStream}
     * does not demand that calls to {@code mark(int)} result in an exception being thrown in the event {@code
     * markSupported()} returns {@code false}; the exception is thrown by {@code reset()}.
     *
     * @param readlimit {@inheritDoc}
     */
    @Override
    public synchronized void mark(int readlimit) {
        // no-op
    }
}
