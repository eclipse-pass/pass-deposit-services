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
package org.dataconservancy.pass.deposit.messaging.support.swordv2;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AtomResources {

    private AtomResources() {
        //never called
    }

    public static final String BASE_PATH = "org/dataconservancy/pass/deposit/messaging/support/swordv2";

    public static final String ARCHIVED_STATUS_RESOURCE = BASE_PATH + "/AtomStatusParser-archived.xml";

    public static final String INPROGRESS_STATUS_RESOURCE = BASE_PATH + "/AtomStatusParser-inprogress.xml";

    public static final String INREVIEW_STATUS_RESOURCE = BASE_PATH + "/AtomStatusParser-inreview.xml";

    public static final String MISSING_STATUS_RESOURCE = BASE_PATH + "/AtomStatusParser-missing.xml";

    public static final String MULTIPLE_STATUS_RESOURCE = BASE_PATH + "/AtomStatusParser-multiple.xml";

    public static final String UNKNOWN_STATUS_RESOURCE = BASE_PATH + "/AtomStatusParser-unknown.xml";

    public static final String WITHDRAWN_STATUS_RESOURCE = BASE_PATH + "/AtomStatusParser-withdrawn.xml";

}
