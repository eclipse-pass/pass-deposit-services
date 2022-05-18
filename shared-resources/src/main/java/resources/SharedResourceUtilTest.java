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
package resources;

import org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomResources;
import org.junit.Test;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SharedResourceUtilTest {

    @Test
    public void findByFullResourceName() throws Exception {
        SharedResourceUtil.findStreamByName("org/dataconservancy/pass/deposit/messaging/support" +
                                            "/swordv2/AtomStatusParser-archived" + ".xml");
    }

    @Test
    public void findByFullResourceNameWithClass() throws Exception {
        SharedResourceUtil.findStreamByName("org/dataconservancy/pass/deposit/messaging/support" +
                                            "/swordv2/AtomStatusParser-archived" + ".xml", AtomResources.class);
    }

    @Test(expected = AssertionError.class)
    public void findByResourceName() throws Exception {
        SharedResourceUtil.findStreamByName("AtomStatusParser-archived.xml");
    }

    @Test(expected = AssertionError.class)
    public void findByResourceNameWithClass() throws Exception {
        SharedResourceUtil.findStreamByName("AtomStatusParser-archived.xml", AtomResources.class);
    }

}