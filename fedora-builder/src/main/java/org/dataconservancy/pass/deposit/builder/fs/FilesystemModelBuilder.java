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

package org.dataconservancy.pass.deposit.builder.fs;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.StreamingSubmissionBuilder;
import org.dataconservancy.pass.deposit.builder.SubmissionBuilder;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds an instance of the Deposit Services model (i.e. a {@link DepositSubmission} from a file on a locally mounted
 * filesystem.
 * <p>
 * The file is JSON data representing a graph of PassEntity objects.  Each object must have a unique ID. The entities in
 * the JSON are linked together by their identifiers to form the graph, rooted with the Submission object.  The file
 * must contain exactly one Submission object, which is the root of the data tree for a deposit.
 * </p>
 * <p>
 * If a {@link PassClient} is provided on construction, the resources present in the local graph will be deposited to
 * the PASS repository.  This results in a new Fedora resource for each resource present in the JSON graph.  The
 * map of local resource identifiers to PASS repository identifiers is obtained by calling {@link #getUriMap()} after
 * invoking one of the {@link #build(String)} or {@link #build(InputStream, Map)} methods.
 * </p>
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class FilesystemModelBuilder extends ModelBuilder implements SubmissionBuilder, StreamingSubmissionBuilder {

    /**
     * Must be non-null if {@link #depositToPass} is {@code true}.
     */
    private PassClient passClient;

    /**
     * Indicates whether or not the PASS resources read from the local JSON should have equivalent resources deposited
     * to the PASS repository.  If this flag is {@code true}, then a {@link #passClient PASS client} <em>must</em>
     * be supplied on {@link #FilesystemModelBuilder(PassClient) construction}.
     */
    private boolean depositToPass = false;

    /**
     * If local PASS resources are deposited to the PASS repository, this map will be keyed by the local PASS resource
     * URI, mapping to the PASS repository URI.
     */
    private ThreadLocal<Map<URI, URI>> uriMap = ThreadLocal.withInitial(HashMap::new);

    private PassJsonFedoraAdapter adapter = new PassJsonFedoraAdapter();

    /**
     * Constructs a builder that will build local resources, but not deposit them to Fedora.
     */
    public FilesystemModelBuilder() {

    }

    /**
     * Constructs a builder that deposits newly built resources to Fedora.
     *
     * @param passClient the PASS client used to deposit newly built resources to Fedora
     */
    public FilesystemModelBuilder(PassClient passClient) {
        this.passClient = passClient;
        this.depositToPass = true;
    }

    /***
     * Build a DepositSubmission from the JSON data in named file.
     * <p>
     * Supported forms of {@code formDataUrl} include:
     * </p>
     * <ul>
     *     <li>{@code http://} or {@code https//}</li>
     *     <li>{@code file:/}</li>
     *     <li>{@code jar:/}</li>
     * </ul>
     * <p>
     * If there is no scheme present, {@code formDataUrl} is interpreted as a path to a local file containing the JSON
     * data.
     * </p>
     *
     * @param formDataUrl url containing the JSON data
     * @return a deposit submission data model
     * @throws InvalidModel if the JSON data cannot be successfully parsed into a valid submission model
     */
    @Override
    public DepositSubmission build(String formDataUrl) throws InvalidModel {
        InputStream is = null;
        try {
            URI resource = new URI(formDataUrl);

            if (resource.getScheme() == null) {
                is = new FileInputStream(formDataUrl);
            } else if (resource.getScheme().startsWith("http") ||
                    resource.getScheme().startsWith("file") ||
                    resource.getScheme().startsWith("jar")) {
                is = resource.toURL().openStream();
            } else {
                throw new InvalidModel(String.format("Unknown scheme '%s' for URL '%s'",
                        resource.getScheme(), formDataUrl));
            }

            return build(is, Collections.emptyMap());
        } catch (FileNotFoundException e) {
            throw new InvalidModel(String.format("Could not open the data file '%s'.", formDataUrl), e);
        } catch (IOException e) {
            throw new InvalidModel(String.format("Failed to close the data file '%s'.", formDataUrl), e);
        } catch (URISyntaxException e) {
            throw new InvalidModel(String.format("Malformed URL '%s'.", formDataUrl), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /***
     * Build a DepositSubmission from JSON data provided in an {@code InputStream}.  This method can be used to bypass
     * the URL resolution logic in {@link #build(String)}.
     *
     * @param stream the InputStream containing the submission graph
     * @return a deposit submission data model
     * @throws InvalidModel if the JSON data cannot be successfully parsed into a valid submission model
     */
    @Override
    public DepositSubmission build(InputStream stream, Map<String, String> streamMd) throws InvalidModel {
        PassJsonFedoraAdapter reader = new PassJsonFedoraAdapter();
        HashMap<URI, PassEntity> entities = new HashMap<>();
        Submission submissionEntity = reader.jsonToPass(stream, entities);

        reader.jsonToFcrepo(stream, )

        return createDepositSubmission(submissionEntity, entities);
    }

    /**
     * The PASS client used to deposit local resources to Fedora.
     *
     * @return the PASS client, may be {@code null}
     * @see #FilesystemModelBuilder(PassClient)
     */
    public PassClient getPassClient() {
        return passClient;
    }

    /**
     * The PASS client used to deposit local resources to Fedora.
     *
     * @param passClient the PASS client
     * @see #FilesystemModelBuilder(PassClient)
     */
    public void setPassClient(PassClient passClient) {
        this.passClient = passClient;
    }

    /**
     * If {@code true}, and if {@link #getPassClient()} is <em>not</em> {@code null}, then this builder will deposit
     * each locally built resource to Fedora.
     *
     * @return whether or not to use a {@code PassClient} to deposit locally built resources to Fedora
     */
    public boolean isDepositToPass() {
        return depositToPass;
    }

    /**
     * If {@code true}, and if {@link #getPassClient()} is <em>not</em> {@code null}, then this builder will deposit
     * each locally built resource to Fedora.
     *
     * @param depositToPass whether or not to use a {@code PassClient} to deposit locally built resources to Fedora
     */
    public void setDepositToPass(boolean depositToPass) {
        this.depositToPass = depositToPass;
    }

    /**
     * Returns a mapping of local PASS entity URIs and their equivalent resources in Fedora.
     * <p>
     * This map will be empty if {@link #isDepositToPass()} is {@code false}.  If {@link #isDepositToPass()} is {@code
     * true} <em>and</em> {@link #getPassClient()} is not {@code null}, this map will be populated after each call to
     * either {@code build(...)} method.
     * </p>
     * <p>
     * This builder is typically instantiated as a singleton.  In order to avoid cross-talk between multiple threads
     * invoking the {@code build(...)} methods, the underlying {@code Map} is managed as a {@code ThreadLocal}.  This
     * means that a caller ought to be able to perform:
     * </p>
     * <ol>
     *     <li>{@link #build(String)}</li>
     *     <li>{@link #getUriMap()}</li>
     *     <li><em>read results from map</em></li>
     *     <li>{@code getUriMap().clear()}</li>
     *     <li><em>builder ready for next invocation</em></li>
     * </ol>
     * <p>
     * Note that this map will grow unless cleared by the caller.
     * </p>
     *
     * @return a map of local entity URIs to Fedora entity URIs
     * @see #FilesystemModelBuilder(PassClient)
     * @see #setPassClient(PassClient)
     * @see #setDepositToPass(boolean)
     */
    public Map<URI, URI> getUriMap() {
        return uriMap.get();
    }

}
