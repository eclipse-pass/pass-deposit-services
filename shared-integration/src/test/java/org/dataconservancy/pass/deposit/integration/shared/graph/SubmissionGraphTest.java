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
package org.dataconservancy.pass.deposit.integration.shared.graph;

import org.dataconservancy.pass.deposit.builder.fs.PassJsonFedoraAdapter;
import org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.Rel;
import org.dataconservancy.pass.model.File;
import org.dataconservancy.pass.model.Funder;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.Policy;
import org.dataconservancy.pass.model.Publication;
import org.dataconservancy.pass.model.Repository;
import org.dataconservancy.pass.model.User;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import submissions.SubmissionResourceUtil;

import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.LinkInstruction.entityHaving;
import static org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.submission;
import static org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.uriSupplier;
import static org.junit.Assert.*;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionGraphTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionGraphTest.class);

    @Test
    public void generic() {

        SubmissionGraph graph = SubmissionGraph.newGraph();

        Grant grant = graph.builderFor(Grant.class)
                .set("awardNumber", "123456")
                .set("localKey", "edu.jhu:123456")
                .set("awardStatus", Grant.AwardStatus.class, Grant.AwardStatus.ACTIVE)
                .add("coPis", uriSupplier.get())
                .add("coPis", uriSupplier.get())
                .build();

        assertEquals("123456", grant.getAwardNumber());
        assertEquals("edu.jhu:123456", grant.getLocalKey());
        assertEquals(Grant.AwardStatus.ACTIVE, grant.getAwardStatus());

        assertNotNull(grant.getId());
        assertNotNull(grant.getCoPis());
        assertEquals(2, grant.getCoPis().size());
    }

    @Test
    public void graph() {

        // Create a new graph.  The SubmissionGraph will automatically create URI ids for each object created by
        // builders of the graph.
        SubmissionGraph graph = SubmissionGraph.newGraph();

        // Create link "instructions" based on unique entity properties, prior to any additions to the graph
        // The link instructions are carried out after all entities have been added to the graph
        // Note that this link instruction doesn't have to have references to any Java objects or PassEntity IDs, it
        // can be crafted a priori if the the local keys are known ahead of time
        graph.link(entityHaving("locatorIds", "mpatton1"))
                .to(entityHaving("awardNumber", "123456"))
                .as(Rel.PI);

        // Reflection-based builder allows for building any PassEntity
        Grant grant = graph.builderFor(Grant.class)
                .set("awardNumber", "123456")
                .set("localKey", "edu.jhu:123456")
                .set("awardStatus", Grant.AwardStatus.class, Grant.AwardStatus.ACTIVE)
                .build((submission, g) -> {
                    // build(...) method accepts functions to manipulate the submission after build
                    // Normally links between resources are handled by link instructions, so a function like this
                    // linking a Grant to the Submission isn't necessary
                    submission.getGrants().add(g.getId());
                    return g;
                });

        // None of the following entities set ids (done automatically by the SubmissionGraph)
        // Linking entities is carried out later by processing link instructions
        Funder primaryFunder = graph.builderFor(Funder.class)
                .set("name", "National Institutes of Health")
                .set("url", URI.class, URI.create("http://nih.gov"))
                .set("localKey", "edu.jhu:nih.gov")
                .build();

        Funder directFunder = graph.builderFor(Funder.class)
                .set("name", "JHU")
                .set("url", URI.class, URI.create("http://jhu.edu"))
                .set("localKey", "edu.jhu")
                .build();

        Policy policy = graph.builderFor(Policy.class)
                .set("title", "Institutional Policy")
                .set("description", "My institutional policy")
                .set("policyUrl", URI.class, URI.create("http://www.google.com"))
                // link to other pass entities
                .linkTo(entityHaving("repositoryKey", "edu:jhu:repo:j10p"), "repositories")
                .linkFrom(entityHaving("localKey", "edu.jhu"), "policy")
                .build();

        Repository repo = graph.builderFor(Repository.class)
                .set("repositoryKey", "edu:jhu:repo:j10p")
                .build();

        User esm = graph.builderFor(User.class)
                .set("username", "esm")
                .set("firstName", "Elliot")
                .set("lastName", "Metsger")
                .set("displayName", "Elliot")
                .set("email", "emetsger@jhu.edu")
                .add("locatorIds", "emetsge1")
                .linkFrom(submission(), "submitter")
                .build();

        User msp = graph.builderFor(User.class)
                .set("username", "msp")
                .set("firstName", "Mark")
                .set("lastName", "Patton")
                .set("displayName", "Moo")
                .set("email", "mpatton@jhu.edu")
                .add("locatorIds", "mpatton1")
                .build();

        Publication pub = graph.builderFor(Publication.class)
                .set("title", "Publication Title")
                .set("doi", "http://dx.doi.org/123/45")
                .set("volume", "1")
                .set("issue", "4")
                .linkFrom(submission(), "publication")
                .build();

        // Link the entity with the following localKey to the supplied Grant as the Grant.primaryFunder
        graph.link(entityHaving("localKey", "edu.jhu:nih.gov"))
                .to(grant)
                .as(Rel.PRIMARY_FUNDER);

        // If you have object references, you don't need to specify a field, just provide the reference.
        graph.link(directFunder)
                .to(grant)
                .as(Rel.DIRECT_FUNDER);

        graph.link(entityHaving("locatorIds", "emetsge1"))
                .to(grant)
                .as(Rel.COPI);

        // perform the linking of the elements in the graph using the provided link instructions
        graph.link();

        assertNotNull(grant.getPrimaryFunder());
        assertEquals(primaryFunder.getId(), grant.getPrimaryFunder());

        assertEquals(1, grant.getCoPis().size());
        assertEquals(esm.getId(), grant.getCoPis().iterator().next());

        assertEquals(directFunder.getId(), grant.getDirectFunder());

        assertEquals(msp.getId(), grant.getPi());

        assertEquals(pub.getId(), submission().getPublication());

        assertEquals(esm.getId(), submission().getSubmitter());

        assertEquals(policy.getId(), directFunder.getPolicy());

        assertEquals(repo.getId(), policy.getRepositories().iterator().next());
    }

    @Test
    public void streamingGraph() {
        InputStream stream = SubmissionResourceUtil.lookupStream(URI.create("fake:submission1"));
        SubmissionGraph graph = SubmissionGraph.newGraph(stream, new PassJsonFedoraAdapter());

        assertEquals("fake:submission1", submission().getId().toString());
        LOG.info("Graph contains:");
        graph.walk(entity -> true, (submission, entity) -> LOG.info("  {} ({})", entity.getClass().getSimpleName(), entity.getId()));
    }

    @Test
    public void remove() {
        InputStream stream = SubmissionResourceUtil.lookupStream(URI.create("fake:submission1"));
        SubmissionGraph graph = SubmissionGraph.newGraph(stream, new PassJsonFedoraAdapter());

        AtomicInteger count = new AtomicInteger(0);
        graph.walk(entity -> entity instanceof File, (s, f) -> count.getAndIncrement());
        assertEquals(8, count.get());
        count.set(0);

        URI file1 = URI.create("fake:file1");
        assertNotNull(graph.get(file1, File.class));
        graph.remove(file1);
        graph.walk(entity -> entity instanceof File, (s, f) -> count.getAndIncrement());
        assertEquals(7, count.get());
        assertNull(graph.get(file1, File.class));

        // fake:user2 is a PI of fake:grant1
        // fake:user1 is a co-PI of fake:grant1
        Grant grant = graph.get(URI.create("fake:grant1"), Grant.class);
        URI user2 = URI.create("fake:user2");
        URI user1 = URI.create("fake:user1");
        assertEquals(user2, grant.getPi());
        assertTrue(grant.getCoPis().contains(user1));
        assertNotNull(graph.get(user1, User.class));
        assertNotNull(graph.get(user2, User.class));

        graph.remove(user2);
        graph.remove(user1);

        assertNull(graph.get(user1, User.class));
        assertNull(graph.get(user2, User.class));
        assertFalse(grant.getCoPis().contains(user1));
        assertNull(grant.getPi());
    }

    @Test
    public void walk() {
        InputStream stream = SubmissionResourceUtil.lookupStream(URI.create("fake:submission1"));
        SubmissionGraph graph = SubmissionGraph.newGraph(stream, new PassJsonFedoraAdapter());

        AtomicInteger count = new AtomicInteger(0);
        graph.walk(entity -> entity instanceof File, (s, f) -> count.getAndIncrement());
        assertEquals(8, count.get());
        count.set(0);

        graph.walk(File.class, (s, f) -> count.getAndIncrement());
        assertEquals(8, count.get());
    }
}