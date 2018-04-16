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

package org.dataconservancy.nihms.builder.fs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.io.IOUtils;
import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.client.adapter.PassJsonAdapterBasic;
import org.dataconservancy.pass.client.fedora.FedoraPassClient;
import org.dataconservancy.pass.model.Deposit;
import org.dataconservancy.pass.model.Funder;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Journal;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Person;
import org.dataconservancy.pass.model.Policy;
import org.dataconservancy.pass.model.Publisher;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.Workflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Converts and transports PassEntity data between local JSON files, indexed lists and Fedora repositories.
 * The functionality supports:
 *   1. Creating DepositSubmission data from resources on a Fedora server.
 *   2. Creating DepositSubmission data from a local JSON file containing PassEntity data.
 *   3. Downloading a JSON snapshot of Fedora resources rooted at a specified Submission resource.
 *   4. Uploading JSON PassEntity data to a Fedora repository to create test data or migrate repository contents.
 *
 * It might make sense to migrate this functionality to the pass-json-adapter module.
 *
 * @author Ben Trumbore (wbt3@cornell.edu)
 */
class PassJsonFedoraAdapter {

    /**
     * Extract PassEntity data from a JSON input stream and fill a collection of PassEntity objects.
     * @param is the input stream carrying the JSON data.
     * @param entities the map that will contain the parsed PassEntity objects, indexed by their IDs.
     * @return the PassEntity Submission object that is the root of the data tree.
     */
    Submission jsonToPass(final InputStream is, final HashMap<URI, PassEntity> entities) {
        Submission submission = null;
        try {
            // Read JSON stream that defines the sample repo data
            final String contentString = IOUtils.toString(is, Charset.defaultCharset());
            final JsonArray entitiesJson = new JsonParser().parse(contentString).getAsJsonArray();

            // Add all the PassEntity objects to the map and remember the Submission object
            final PassJsonAdapterBasic adapter = new PassJsonAdapterBasic();
            for (JsonElement entityJson : entitiesJson) {
                // What is the entity type?
                final JsonElement typeName = entityJson.getAsJsonObject().get("@type");
                final String typeStr = "org.dataconservancy.pass.model." + typeName.getAsString();
                final Class<org.dataconservancy.pass.model.PassEntity> type =
                        (Class<org.dataconservancy.pass.model.PassEntity>) Class.forName(typeStr);

                // Create and save the PassEntity object
                final byte[] entityJsonBytes = entityJson.toString().getBytes();
                final PassEntity entity = adapter.toModel(entityJsonBytes, type);
                final URI uri = new URI(entityJson.getAsJsonObject().get("@id").getAsString());
                entities.put(uri, entity);
                if (entity instanceof Submission) {
                    submission = (Submission)entity;
                }
            }
            return submission;

        } catch (IOException e) {
            System.out.println("Could not read from input stream.");
            e.printStackTrace();
        } catch (JsonSyntaxException e) {
            System.out.println("Could not parse sample data JSON.");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.out.println("Could not identify class type for entity.");
            e.printStackTrace();
        } catch (URISyntaxException e) {
            System.out.println("Entity contained an invalid URI as its Id.");
            e.printStackTrace();
        }

        entities.clear();
        return null;
    }

    /***
     * Serializes a collection of PassEntity objects into an output stream as JSON.
     * @param entities the map that contains the PassEntity objects to serialize.
     * @param os the output stream carrying the JSON representing the PassEntity objects.
     */
    void passToJson(final HashMap<URI, PassEntity> entities, final OutputStream os) {
        final PassJsonAdapterBasic adapter = new PassJsonAdapterBasic();
        final ArrayList<URI> printedUris = new ArrayList<>();
        final PrintWriter pw = new PrintWriter(os);

        boolean first = true;
        pw.println("[");

        for (URI uri : entities.keySet()) {
            final PassEntity entity = entities.get(uri);
            final byte[] text = adapter.toJson(entity, false);
            // Make sure each resource is only printed once
            if (! printedUris.contains(uri)) {
                if (!first) {
                    pw.println(",");
                } else {
                    first = false;
                }
                pw.print(new String(text));
                printedUris.add(uri);
            }
        }

        pw.println("\n]");
        pw.close();
    }

    // Creates a list of URIs that are the updated counterparts to a provided list of "old" URIs.
    private ArrayList<URI> getUpdatedUris(final HashMap<URI, URI> uriMap, final List<URI> oldUris) {
        final ArrayList<URI> newUris = new ArrayList<>();
        for (URI oldUri : oldUris) {
            newUris.add(uriMap.get(oldUri));
        }
        return newUris;
    }

    /***
     * Uploads a collection of PassEntity objects to a Fedora server as new resources.
     *
     * The collection must contain exactly one Submission entity.  All other entities
     * referenced by this Submission (and further entities referenced by them) must
     * be present in the collection.  All entities must have unique IDs.
     *
     * The target Fedora server is specified with the pass.fedora.baseurl system property,
     * which defaults to http://localhost:8080/ (trailing slash is needed).
     * Credentials on the server are specified with the
     * pass.fedora.user and pass.fedora.password system properties.
     *
     * @param entities the PassEntity objects to upload.
     * @return the URI on the Fedora server of the newly created Submission resource.
     */
    URI passToFcrepo(final HashMap<URI, PassEntity> entities) {
        final PassClient client = new FedoraPassClient();
        final HashMap<URI, URI> uriMap = new HashMap<>();
        URI submissionUri = null;

        // Create each entity as a resource on the Fedora server, remembering their URIs.
        for (URI oldUri : entities.keySet()) {
            final PassEntity entity = entities.get(oldUri);
            entity.setId(null); // Clear out before pushing to repo
            final URI newUri = client.createResource(entity);
            entity.setId(newUri);
            uriMap.put(oldUri, newUri);
        }

        // Update links between resources using collected information
        for (URI oldUri : entities.keySet()) {
            final PassEntity entity = entities.get(oldUri);
            boolean needUpdate = true;
            if (entity instanceof Submission) {
                submissionUri = uriMap.get(oldUri);
                final Submission submission = (Submission)entity;
                submission.setJournal(uriMap.get(submission.getJournal()));
                submission.setDeposits(getUpdatedUris(uriMap, submission.getDeposits()));
                submission.setGrants(getUpdatedUris(uriMap, submission.getGrants()));
                submission.setWorkflows(getUpdatedUris(uriMap, submission.getWorkflows()));
            } else if (entity instanceof Deposit) {
                final Deposit deposit = (Deposit)entity;
                deposit.setSubmission(uriMap.get(deposit.getSubmission()));
            } else if (entity instanceof Grant) {
                final Grant grant = (Grant)entity;
                grant.setPrimaryFunder(uriMap.get(grant.getPrimaryFunder()));
                grant.setDirectFunder(uriMap.get(grant.getDirectFunder()));
                grant.setPi(uriMap.get(grant.getPi()));
                grant.setCoPis(getUpdatedUris(uriMap, grant.getCoPis()));
                grant.setSubmissions(getUpdatedUris(uriMap, grant.getSubmissions()));
            } else if (entity instanceof Funder) {
                final Funder funder = (Funder)entity;
                funder.setPolicy(uriMap.get(funder.getPolicy()));
            } else if (entity instanceof Policy) {
                final Policy policy = (Policy)entity;
                policy.setRepositories(getUpdatedUris(uriMap, policy.getRepositories()));
            } else if (entity instanceof Publisher) {
                final Publisher publisher = (Publisher)entity;
                publisher.setJournals(getUpdatedUris(uriMap, publisher.getJournals()));
            } else if (entity instanceof Journal) {
                final Journal journal = (Journal)entity;
                journal.setPublisher(uriMap.get(journal.getPublisher()));
            } else {
                needUpdate = false;
            }
            if (needUpdate) {
                client.updateResource(entity);
            }
        }

        return submissionUri;
    }

    /***
     * Downloads a tree of resources, rooted at a Submission, from a Fedora server.
     *
     * The target Fedora server is specified with the pass.fedora.baseurl system property,
     * which defaults to http://localhost:8080/ (trailing slash is needed).
     * Credentials on the server are specified with the
     * pass.fedora.user and pass.fedora.password system properties.
     *
     * @param submissionUri the URI of the root Submission resource to download.
     * @param entities the collection of PassEntity objects that is created.
     * @return the Submission entity that corresponds to the provided URI.
     */
    Submission fcrepoToPass(final URI submissionUri, final HashMap<URI, PassEntity> entities) {
        final PassClient client = new FedoraPassClient();

        final Submission submission = client.readResource(submissionUri, Submission.class);
        entities.put(submissionUri, submission);

        final Journal journal = client.readResource(submission.getJournal(), Journal.class);
        entities.put(submission.getJournal(), journal);
        final Publisher publisher = client.readResource(journal.getPublisher(), Publisher.class);
        entities.put(journal.getPublisher(), publisher);

        // Assume all deposits are unique, but repositories may be duplicated.
        for (URI depositUri : submission.getDeposits()) {
            final Deposit deposit = client.readResource(depositUri, Deposit.class);
            entities.put(depositUri, deposit);
            if (! entities.containsKey(deposit.getRepository())) {
                final Repository repository = client.readResource(deposit.getRepository(), Repository.class);
                entities.put(deposit.getRepository(), repository);
            }
        }

        // Assume all grants are unique, but funders, policies and people may be duplicated.
        for (URI grantUri : submission.getGrants()) {
            final Grant grant = client.readResource(grantUri, Grant.class);
            entities.put(grantUri, grant);
            if (! entities.containsKey(grant.getPrimaryFunder())) {
                final Funder funder = client.readResource(grant.getPrimaryFunder(), Funder.class);
                entities.put(grant.getPrimaryFunder(), funder);
                if (! entities.containsKey(funder.getPolicy())) {
                    final Policy policy = client.readResource(funder.getPolicy(), Policy.class);
                    entities.put(funder.getPolicy(), policy);
                }
            }
            if (! entities.containsKey(grant.getDirectFunder())) {
                final Funder funder = client.readResource(grant.getDirectFunder(), Funder.class);
                entities.put(grant.getDirectFunder(), funder);
                if (!entities.containsKey(funder.getPolicy())) {
                    final Policy policy = client.readResource(funder.getPolicy(), Policy.class);
                    entities.put(funder.getPolicy(), policy);
                }
            }
            final Person pi = client.readResource(grant.getPi(), Person.class);
            entities.put(grant.getPi(), pi);
            for (URI copiUri : grant.getCoPis()) {
                if (! entities.containsKey(copiUri)) {
                    final Person copi = client.readResource(copiUri, Person.class);
                    entities.put(copiUri, copi);
                }
            }
        }

        for (URI workflowUri : submission.getWorkflows()) {
            final Workflow workflow = client.readResource(workflowUri, Workflow.class);
            entities.put(workflowUri, workflow);
        }

        return submission;
    }

    /***
     * Upload JSON PassEntity data to a Fedora repository.
     *
     * The JSON must contain exactly one Submission entity.  All other entities
     * referenced by this Submission (and further entities referenced by them) must
     * be present in the JSON.  All entities must have unique IDs.
     *
     * The target Fedora server is specified with the pass.fedora.baseurl system property.
     * Credentials on the server are specified with the
     * pass.fedora.user and pass.fedora.password system properties.
     *
     * @param is the stream containing the JSON data.
     * @return the RUI of the root Submission resource on the Fedora server.
     */
    URI jsonToFcrepo(final InputStream is) {
        final HashMap<URI, PassEntity> entities = new HashMap<>();
        jsonToPass(is, entities);
        return passToFcrepo(entities);
    }

    /***
     * Serialize a resource tree (rooted at a Submission) from a Fedora server as JSON.
     *
     * The target Fedora server is specified with the pass.fedora.baseurl system property.
     * Credentials on the server are specified with the
     * pass.fedora.user and pass.fedora.password system properties.
     *
     * @param submissionUri the URI for the root Submission resource to download.
     * @param os the output stream containing the serialized data.
     */
    void fcrepoToJson(final URI submissionUri, final OutputStream os) {
        final HashMap<URI, PassEntity> entities = new HashMap<>();
        fcrepoToPass(submissionUri, entities);
        passToJson(entities, os);
    }
}