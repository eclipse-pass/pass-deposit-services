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
    Submission jsonToPass(InputStream is, HashMap<URI, PassEntity> entities) {
        Submission submission = null;
        try {
            // Read JSON stream that defines the sample repo data
            String contentString = IOUtils.toString(is, Charset.defaultCharset());
            JsonArray entitiesJson = new JsonParser().parse(contentString).getAsJsonArray();

            // Add all the PassEntity objects to the map and remember the Submission object
            PassJsonAdapterBasic adapter = new PassJsonAdapterBasic();
            for (JsonElement entityJson : entitiesJson) {
                // What is the entity type?
                JsonElement typeName = entityJson.getAsJsonObject().get("@type");
                String typeStr = "org.dataconservancy.pass.model." + typeName.getAsString();
                Class<org.dataconservancy.pass.model.PassEntity> type =
                        (Class<org.dataconservancy.pass.model.PassEntity>) Class.forName(typeStr);

                // Create and save the PassEntity object
                byte[] entityJsonBytes = entityJson.toString().getBytes();
                PassEntity entity = adapter.toModel(entityJsonBytes, type);
                URI uri = new URI(entityJson.getAsJsonObject().get("@id").getAsString());
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
    void passToJson(HashMap<URI, PassEntity> entities, OutputStream os) {
        PassJsonAdapterBasic adapter = new PassJsonAdapterBasic();
        ArrayList<URI> printedUris = new ArrayList<>();
        PrintWriter pw = new PrintWriter(os);

        boolean first = true;
        pw.println("[");

        for (URI uri : entities.keySet()) {
            PassEntity entity = entities.get(uri);
            byte[] text = adapter.toJson(entity, false);
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
    private ArrayList<URI> getUpdatedUris(HashMap<URI, URI> uriMap, List<URI> oldUris) {
        ArrayList<URI> newUris = new ArrayList<>();
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
    URI passToFcrepo(HashMap<URI, PassEntity> entities) {
        PassClient client = new FedoraPassClient();
        HashMap<URI, URI> uriMap = new HashMap<>();
        URI submissionUri = null;

        // Create each entity as a resource on the Fedora server, remembering their URIs.
        for (URI oldUri : entities.keySet()) {
            PassEntity entity = entities.get(oldUri);
            entity.setId(null); // Clear out before pushing to repo
            URI newUri = client.createResource(entity);
            entity.setId(newUri);
            uriMap.put(oldUri, newUri);
        }

        // Update links between resources using collected information
        for (URI oldUri : entities.keySet()) {
            PassEntity entity = entities.get(oldUri);
            boolean needUpdate = true;
            if (entity instanceof Submission) {
                submissionUri = uriMap.get(oldUri);
                Submission submission = (Submission)entity;
                submission.setJournal(uriMap.get(submission.getJournal()));
                submission.setDeposits(getUpdatedUris(uriMap, submission.getDeposits()));
                submission.setGrants(getUpdatedUris(uriMap, submission.getGrants()));
                submission.setWorkflows(getUpdatedUris(uriMap, submission.getWorkflows()));
            } else if (entity instanceof Deposit) {
                Deposit deposit = (Deposit)entity;
                deposit.setSubmission(uriMap.get(deposit.getSubmission()));
            } else if (entity instanceof Grant) {
                Grant grant = (Grant)entity;
                grant.setPrimaryFunder(uriMap.get(grant.getPrimaryFunder()));
                grant.setDirectFunder(uriMap.get(grant.getDirectFunder()));
                grant.setPi(uriMap.get(grant.getPi()));
                grant.setCoPis(getUpdatedUris(uriMap, grant.getCoPis()));
                grant.setSubmissions(getUpdatedUris(uriMap, grant.getSubmissions()));
            } else if (entity instanceof Funder) {
                Funder funder = (Funder)entity;
                funder.setPolicy(uriMap.get(funder.getPolicy()));
            } else if (entity instanceof Policy) {
                Policy policy = (Policy)entity;
                policy.setRepositories(getUpdatedUris(uriMap, policy.getRepositories()));
            } else if (entity instanceof Publisher) {
                Publisher publisher = (Publisher)entity;
                publisher.setJournals(getUpdatedUris(uriMap, publisher.getJournals()));
            } else if (entity instanceof Journal) {
                Journal journal = (Journal)entity;
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
    Submission fcrepoToPass(URI submissionUri, HashMap<URI, PassEntity> entities) {
        PassClient client = new FedoraPassClient();

        Submission submission = client.readResource(submissionUri, Submission.class);
        entities.put(submissionUri, submission);

        Journal journal = client.readResource(submission.getJournal(), Journal.class);
        entities.put(submission.getJournal(), journal);
        Publisher publisher = client.readResource(journal.getPublisher(), Publisher.class);
        entities.put(journal.getPublisher(), publisher);

        // Assume all deposits are unique, but repositories may be duplicated.
        for (URI depositUri : submission.getDeposits()) {
            Deposit deposit = client.readResource(depositUri, Deposit.class);
            entities.put(depositUri, deposit);
            if (! entities.containsKey(deposit.getRepository())) {
                Repository repository = client.readResource(deposit.getRepository(), Repository.class);
                entities.put(deposit.getRepository(), repository);
            }
        }

        // Assume all grants are unique, but funders, policies and people may be duplicated.
        for (URI grantUri : submission.getGrants()) {
            Grant grant = client.readResource(grantUri, Grant.class);
            entities.put(grantUri, grant);
            if (! entities.containsKey(grant.getPrimaryFunder())) {
                Funder funder = client.readResource(grant.getPrimaryFunder(), Funder.class);
                entities.put(grant.getPrimaryFunder(), funder);
                if (! entities.containsKey(funder.getPolicy())) {
                    Policy policy = client.readResource(funder.getPolicy(), Policy.class);
                    entities.put(funder.getPolicy(), policy);
                }
            }
            if (! entities.containsKey(grant.getDirectFunder())) {
                Funder funder = client.readResource(grant.getDirectFunder(), Funder.class);
                entities.put(grant.getDirectFunder(), funder);
                if (!entities.containsKey(funder.getPolicy())) {
                    Policy policy = client.readResource(funder.getPolicy(), Policy.class);
                    entities.put(funder.getPolicy(), policy);
                }
            }
            Person pi = client.readResource(grant.getPi(), Person.class);
            entities.put(grant.getPi(), pi);
            for (URI copiUri : grant.getCoPis()) {
                if (! entities.containsKey(copiUri)) {
                    Person copi = client.readResource(copiUri, Person.class);
                    entities.put(copiUri, copi);
                }
            }
        }

        for (URI workflowUri : submission.getWorkflows()) {
            Workflow workflow = client.readResource(workflowUri, Workflow.class);
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
    URI jsonToFcrepo(InputStream is) {
        HashMap<URI, PassEntity> entities = new HashMap<>();
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
    void fcrepoToJson(URI submissionUri, OutputStream os) {
        HashMap<URI, PassEntity> entities = new HashMap<>();
        fcrepoToPass(submissionUri, entities);
        passToJson(entities, os);
    }
}