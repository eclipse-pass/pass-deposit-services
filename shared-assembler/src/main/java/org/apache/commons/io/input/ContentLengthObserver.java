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
package org.apache.commons.io.input;

import java.io.IOException;

import org.dataconservancy.pass.deposit.assembler.ResourceBuilder;

/**
 * Applies the number of bytes observed to the supplied {@link ResourceBuilder}.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class ContentLengthObserver extends ResourceBuilderObserver {

    private long length = 0;

    public ContentLengthObserver(ResourceBuilder builder) {
        super(builder);
    }

    @Override
    void data(int pByte) throws IOException {
        length++;
    }

    @Override
    void data(byte[] pBuffer, int pOffset, int pLength) throws IOException {
        length += pLength;
    }

    @Override
    void finished() throws IOException {
        if (!isFinished()) {
            builder.sizeBytes(this.length);
        }
        super.finished();
    }

}
