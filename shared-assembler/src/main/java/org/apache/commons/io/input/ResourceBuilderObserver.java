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
 * Abstract class that supplies a member {@link ResourceBuilder} on construction.  Sub-classes are expected to update
 * the state of the {@code ResourceBuilder} after observing the input stream.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public abstract class ResourceBuilderObserver extends ObservableInputStream.Observer {

    protected ResourceBuilder builder;

    protected boolean finished = false;

    public ResourceBuilderObserver(ResourceBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("ResourceBuilder must not be null.");
        }
        this.builder = builder;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    void finished() throws IOException {
        this.finished = true;
    }
}
