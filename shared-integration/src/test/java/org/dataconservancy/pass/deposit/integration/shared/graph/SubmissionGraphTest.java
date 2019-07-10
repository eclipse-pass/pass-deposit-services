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

import org.dataconservancy.pass.model.Funder;
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Policy;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.User;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.LinkInstruction.with;
import static org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.uriSupplier;
import static org.junit.Assert.*;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionGraphTest {

//    @Test
//    public void simple() {
//        Grant grant = new SubmissionGraph.GrantBuilder()
//                .set("awardNumber", "123456")
//                .set("localKey", "edu.jhu:123456")
//                .set("awardStatus", Grant.AwardStatus.class, Grant.AwardStatus.ACTIVE)
//                .add("coPis", uriSupplier.get())
//                .add("coPis", uriSupplier.get())
//                .build();
//
//        assertEquals("123456", grant.getAwardNumber());
//        assertEquals("edu.jhu:123456", grant.getLocalKey());
//        assertEquals(Grant.AwardStatus.ACTIVE, grant.getAwardStatus());
//
//        assertNotNull(grant.getId());
//        assertNotNull(grant.getCoPis());
//        assertEquals(2, grant.getCoPis().size());
//    }
//
//    @Test
//    public void setList() {
//        Grant grant = new SubmissionGraph.GrantBuilder()
//                .set("coPis", List.class, Arrays.asList(uriSupplier.get(), uriSupplier.get()))
//                .build();
//
//        assertNotNull(grant.getCoPis());
//        assertEquals(2, grant.getCoPis().size());
//    }

    @Test
    public void generic() {
        Supplier<Grant> grantSupplier = () -> {
            Grant grant = new Grant();
            grant.setId(uriSupplier.get());
            return grant;
        };

        Grant grant = new SubmissionGraph.GenericBuilder<>(grantSupplier)
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
        SubmissionGraph graph = SubmissionGraph.newGraph();

        Grant grant = graph.builderFor(Grant.class)
                .set("awardNumber", "123456")
                .set("localKey", "edu.jhu:123456")
                .set("awardStatus", Grant.AwardStatus.class, Grant.AwardStatus.ACTIVE)
                .add("coPis", uriSupplier.get())
                .add("coPis", uriSupplier.get())
                .build((submission, g) -> {
                    submission.getGrants().add(g.getId());
                    return g;
                });

        graph.builderFor(Funder.class)
                .set("name", "National Institutes of Health")
                .set("url", URI.class, URI.create("http://nih.gov"))
                .set("localKey", "edu.jhu:nih.gov")
                .build((submission, entities, f) -> {
                    ((Grant)entities.get(grant.getId())).setPrimaryFunder(f.getId());
                    return f;
                });

        graph.builderFor(Policy.class)
                .set("title", "Institutional Policy")
                .set("description", "My institutional policy")
                .set("policyUrl", URI.class, URI.create("http://www.google.com"))
                .build((submission, p) -> {
                    graph.walk(e -> e instanceof Funder, (s, e) -> {
                        Funder f = (Funder)e;
                        f.setPolicy(p.getId());
                    });
                    return p;
                });

        User user = graph.builderFor(User.class)
                .set("username", "esm")
                .set("firstName", "Elliot")
                .set("lastName", "Metsger")
                .set("displayName", "Elliot")
                .set("email", "emetsger@jhu.edu")
                .add("locatorIds", "emetsge1")
                .build();

        graph.linkEntity(with("localKey", "edu.jhu:nih.gov"))
                .to(grant)
                .as(SubmissionGraph.Rel.PRIMARY_FUNDER);

        graph.linkEntity(with("locatorIds", "emetsge1"))
                .to(grant)
                .as(SubmissionGraph.Rel.COPI);

        graph.linkEntity(user)
                .to(graph.)

        graph.link();


    }

}