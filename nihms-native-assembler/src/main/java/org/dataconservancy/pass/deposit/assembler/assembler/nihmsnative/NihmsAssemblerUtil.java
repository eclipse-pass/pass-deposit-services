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
package org.dataconservancy.pass.deposit.assembler.assembler.nihmsnative;

import org.dataconservancy.pass.deposit.assembler.shared.SizedStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
class NihmsAssemblerUtil {

    /**
     * Returns a {@code SizedStream} for reading the contents of the supplied {@code ByteArrayOutputStream}.
     * <p>
     * Implementation note: this method invokes {@link ByteArrayOutputStream#close()}.
     * </p>
     * @param os the output stream to adapt for reading
     * @return the {@code SizedStream} for reading the content of the supplied output stream
     */
    static SizedStream asSizedStream(ByteArrayOutputStream os) {
        byte[] bytes = os.toByteArray();

        try (InputStream is = new ByteArrayInputStream(bytes)) {
            os.close();
            return new SizedStream() {
                @Override
                public long getLength() {
                    return os.size();
                }

                @Override
                public InputStream getInputStream() {
                    return is;
                }
            };
        } catch (IOException ioe) {
            throw new RuntimeException("Could not create Input Stream, or close Output Stream", ioe);
        }
    }

}
