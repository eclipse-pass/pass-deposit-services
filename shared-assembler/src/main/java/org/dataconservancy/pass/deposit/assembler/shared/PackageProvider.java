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
package org.dataconservancy.pass.deposit.assembler.shared;

import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface PackageProvider {

    /**
     * Lifecycle method, invoked prior to streaming the package.  Implementations may initialize any necessary state.
     *
     * @param submission
     * @param custodialResources
     */
    void start(DepositSubmission submission, List<DepositFileResource> custodialResources);

    /**
     * Invoked for pathing each custodial resource relative to the base of the package.  Implementations are responsible
     * for preventing name collisions.
     *
     * @param custodialResource
     * @return
     */
    String packagePath(DepositFileResource custodialResource);

    /**
     * Lifecycle method, invoked after streaming the custodial resources, but before closing the package stream.
     * Implementations may return any non-custodial resources to be included in the package stream.
     *
     * @param submission
     * @param packageResources
     * @return
     */
    List<SupplementalResource> finish(DepositSubmission submission, List<PackageStream.Resource> packageResources);

    /**
     * Represents non-custodial (i.e. supplemental) resources to be included in the package stream.
     */
    interface SupplementalResource extends Resource {

        /**
         * The path of the resource relative to the base of the package.
         *
         * @return
         */
        String getPackagePath();

    }

}
