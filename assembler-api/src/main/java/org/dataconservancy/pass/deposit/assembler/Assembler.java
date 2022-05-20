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
package org.dataconservancy.pass.deposit.assembler;

import java.util.Map;

import org.dataconservancy.pass.deposit.model.DepositSubmission;

/**
 * Responsible for assembling the components of a {@link DepositSubmission submission} into a serialized package.  This
 * includes de-referencing byte streams associated with the submission, creating or assembling the metadata describing
 * the submission, and providing a serialization of the package.  Implementations of this interface are knowledgeable of
 * the specific packaging requirements (e.g. comporting with BagIt, or profile of BagIt) of a submission destination ,
 * and are responsible for providing a compliant package.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface Assembler {

    /**
     * Creates a streamable package in accordance with any specifications or rules defined by the implementation.  The
     * supplied {@code submission} should be considered the custodial content of the returned package.  Whether or not
     * package resources <em>generated</em> by this implementation are considered custodial content are determined by
     * the implementation.
     *
     * @param submission the custodial content being packaged
     * @param options    the options used when creating the package
     * @return a streamable package containing the custodial content being packaged
     */
    PackageStream assemble(DepositSubmission submission, Map<String, Object> options);

}
