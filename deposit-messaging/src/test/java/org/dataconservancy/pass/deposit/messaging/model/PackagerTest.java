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
package org.dataconservancy.pass.deposit.messaging.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dataconservancy.pass.deposit.assembler.Assembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.messaging.config.repository.AssemblerConfig;
import org.dataconservancy.pass.deposit.messaging.config.repository.AssemblerOptions;
import org.dataconservancy.pass.deposit.messaging.config.repository.RepositoryConfig;
import org.dataconservancy.pass.deposit.messaging.status.DepositStatusProcessor;
import org.dataconservancy.pass.deposit.transport.Transport;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class PackagerTest {

    private Assembler assembler;

    private Transport transport;

    private DepositStatusProcessor dsp;

    private RepositoryConfig repositoryConfig;

    @Before
    public void setUp() throws Exception {
        assembler = mock(Assembler.class);
        transport = mock(Transport.class);
        dsp = mock(DepositStatusProcessor.class);
        repositoryConfig = mock(RepositoryConfig.class);
    }

    /**
     * Insure the Package specification from the <em>config</em> is included in the options returned by
     * {@link Packager#getAssemblerOptions()}.
     */
    @Test
    public void includeSpecInAssemblerOptions() {
        Packager p = new Packager("PackagerName", assembler, transport, repositoryConfig);

        AssemblerConfig config = new AssemblerConfig();
        AssemblerOptions opts = new AssemblerOptions();
        config.setSpec("My Spec");
        config.setOptions(opts);

        when(repositoryConfig.getAssemblerConfig()).thenReturn(config);

        assertEquals("My Spec", p.getAssemblerOptions().get(PackageOptions.Spec.KEY).toString());

        assertNull(p.getAssemblerOptions().get(PackageOptions.Archive.KEY));
        assertNull(p.getAssemblerOptions().get(PackageOptions.Checksum.KEY));
        assertNull(p.getAssemblerOptions().get(PackageOptions.Compression.KEY));
    }

}