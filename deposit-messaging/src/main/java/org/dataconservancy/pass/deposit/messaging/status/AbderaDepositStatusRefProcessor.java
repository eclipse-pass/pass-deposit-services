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

import org.dataconservancy.pass.deposit.messaging.service.DepositStatusRefProcessor;
import org.dataconservancy.pass.model.Deposit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Parses {@link Deposit.DepositStatus} from a SWORD statement document.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement">SWORD v2 Profile §11</a>
 */
@Component
public class AbderaDepositStatusRefProcessor implements DepositStatusRefProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbderaDepositStatusRefProcessor.class);

    private DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser;

    private DepositStatusMapper<SwordDspaceDepositStatus> swordDepositStatusMapper;

    @Autowired
    public AbderaDepositStatusRefProcessor(DepositStatusParser<URI, SwordDspaceDepositStatus> atomStatusParser,
                                           DepositStatusMapper<SwordDspaceDepositStatus> swordDepositStatusMapper) {
        this.atomStatusParser = atomStatusParser;
        this.swordDepositStatusMapper = swordDepositStatusMapper;
    }

    /**
     * Parses the SWORD statement at {@code depositStatusRef}, and returns a corresponding {@link Deposit.DepositStatus}
     *
     * @param depositStatusRef expected to be a {@code URI} referencing a SWORD statement
     * @return the deposit status, may be {@code null}
     */
    @Override
    public Deposit.DepositStatus process(URI depositStatusRef) {
        SwordDspaceDepositStatus swordStatus = atomStatusParser.parse(depositStatusRef);

        if (swordStatus == null) {
            LOG.debug("Unable to parse the SWORD deposit status from {}, returning 'null' Deposit.DepositStatus",
                    depositStatusRef);
            return null;
        }

        Deposit.DepositStatus depositStatus = swordDepositStatusMapper.map(swordStatus);

        if (depositStatus == null) {
            LOG.debug("Unable to map the SWORD deposit status '{}' parsed from {} to a Deposit.DepositStatus.  " +
                            "Returning 'null' Deposit.DepositStatus", swordStatus, depositStatusRef);
            return null;
        }

        return depositStatus;
    }
}
