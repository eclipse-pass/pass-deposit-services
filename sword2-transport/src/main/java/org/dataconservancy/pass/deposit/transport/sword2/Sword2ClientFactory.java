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
package org.dataconservancy.pass.deposit.transport.sword2;

import java.util.Map;

import org.swordapp.client.SWORDClient;

/**
 * Creates, and optionally configures, {@link SWORDClient} instances for use.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface Sword2ClientFactory {

    /**
     * Create a new instance of a SWORD v2 client.  The supplied {@code hints} are used by the factory implementation
     * to optionally configure the client.
     *
     * @param hints used to configure the SWORD client, may be {@code null}
     * @return a new {@code SWORDClient} instance
     */
    SWORDClient newInstance(Map<String, String> hints);

}
