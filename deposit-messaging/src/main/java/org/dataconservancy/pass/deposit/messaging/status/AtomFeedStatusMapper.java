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
package org.dataconservancy.pass.deposit.messaging.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.dataconservancy.pass.deposit.messaging.support.swordv2.AtomUtil;
import org.dataconservancy.pass.model.Deposit;

/**
 * Maps the status retrieved from an Atom document to a PASS {@link org.dataconservancy.pass.model.Deposit}
 * {@link Deposit#getDepositStatus() status}.  The Atom document is a SWORD statement received in response to a
 * SWORD deposit.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement">SWORD v2 Profile §11</a>
 */
public class AtomFeedStatusMapper extends AbstractStatusMapper<Document<Feed>> {

    public AtomFeedStatusMapper(JsonNode statusMap) {
        super(statusMap);
    }

    @Override
    protected String getConfigurationKey() {
        return SWORDV2_MAPPING_KEY;
    }

    @Override
    public Deposit.DepositStatus map(Document<Feed> statusToMap) {
        SwordDspaceDepositStatus status = AtomUtil.parseAtomStatement(statusToMap);
        if (status != null) {
            return mapInternal(status.name());
        }

        return mapInternal(null);
    }
}
