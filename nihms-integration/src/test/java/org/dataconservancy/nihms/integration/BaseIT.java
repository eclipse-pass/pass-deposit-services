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

package org.dataconservancy.nihms.integration;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

public abstract class BaseIT {

    protected static final Logger LOG = LoggerFactory.getLogger(BaseIT.class);

    protected static final String DOCKER_HOST_PROPERTY = "docker.host.address";

    @Before
    public void verifyDockerHostProperty() throws Exception {
        assertNotNull("Expected required system property 'docker.host.address' to be set.",
                System.getProperty("docker.host.address"));
    }
}
