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

import static org.dataconservancy.pass.deposit.assembler.PackageOptions.Spec;

import java.util.List;
import java.util.Map;

import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.springframework.core.io.Resource;

/**
 * Abstracts the naming and pathing of resources within a package, and allows implementations to add supplemental
 * resources to the package.
 * <p>
 * Implementations must support and align with the {@link Spec packaging specification} provided in the {@code Map} of
 * package options.  For example, a BagIT implementation would {@link #packagePath(DepositFileResource) path} all of the
 * custodial content under a {@code data/} directory, and BagIT tag files would be supplied as {@link
 * #finish(DepositSubmission, List) supplemental resources} under the base of the package.
 * </p>
 * <p>
 * {@code PackageProvider} is invoked when <em>writing</em> the {@code PackageStream} in response to {@link
 * PackageStream#open()}.  Any exceptions should be thrown as {@code RuntimeException} to prevent a corrupt {@code
 * PackageStream} being streamed to callers.
 * </p>
 * <p>
 * A new {@code PackageProvider} is instantiated each time {@code PackageStream#open()} is invoked.  Implementations are
 * not expected to be invoked by multiple threads.
 * </p>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public interface PackageProvider {

    /**
     * Lifecycle method, invoked prior to streaming the package.  Implementations may initialize any necessary state.
     *
     * @param submission         the original submission
     * @param custodialResources the custodial content to be packaged
     * @param packageOptions     the options for creating the package, including the packaging specification
     */
    void start(DepositSubmission submission, List<DepositFileResource> custodialResources,
               Map<String, Object> packageOptions);

    /**
     * Lifecycle method invoked for pathing each custodial resource relative to the base of the package.  The string
     * returned from this method ought to be a valid posix path including the filename.  The bytes of the {@code
     * custodialResource} will be written to the path.  Implementations are responsible for preventing collisions in
     * paths or filenames.
     *
     * @param custodialResource the custodial resource to be packaged
     * @return a valid posix path including the filename where the bytes for {@code custodialResource} will be written
     */
    String packagePath(DepositFileResource custodialResource);

    /**
     * Lifecycle method, invoked after streaming the custodial resources, but before closing the package stream being
     * written to the caller.  Implementations may return any non-custodial resources to be included in the package
     * stream.
     *
     * @param submission       the original submission
     * @param packageResources the metadata for each resource that has been streamed so far
     * @return supplemental resources to be included in the package stream
     */
    List<SupplementalResource> finish(DepositSubmission submission, List<PackageStream.Resource> packageResources);

    /**
     * Represents non-custodial resources to be included in the package stream.
     */
    interface SupplementalResource extends Resource {

        /**
         * The path of the resource relative to the base of the package.
         *
         * @return a valid posix path including the filename where the bytes for this {@code SupplementalResource} will
         * be written
         * @see PackageProvider#packagePath(DepositFileResource)
         */
        String getPackagePath();

    }

}
