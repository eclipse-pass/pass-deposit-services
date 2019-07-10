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
import org.dataconservancy.pass.model.Submission;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.dataconservancy.pass.deposit.integration.shared.graph.SubmissionGraph.uriSupplier;
import static org.junit.Assert.*;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionGraphTest {

    @Test
    public void simple() {
        Grant grant = new SubmissionGraph.GrantBuilder()
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
    public void setList() {
        Grant grant = new SubmissionGraph.GrantBuilder()
                .set("coPis", List.class, Arrays.asList(uriSupplier.get(), uriSupplier.get()))
                .build();

        assertNotNull(grant.getCoPis());
        assertEquals(2, grant.getCoPis().size());
    }

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
    public void genericMapPut() {
        Map<URI, ? super PassEntity> map = new HashMap<>();
        map.put(uriSupplier.get(), new Grant());
        map.put(uriSupplier.get(), new Submission());
    }

    @Test
    public void graph() {
        SubmissionGraph graph = new SubmissionGraph();

        URI u = uriSupplier.get();
        Grant g = new Grant();
        g.setId(u);

        graph.add(g);
        Grant foo = graph.get(u, Grant.class);


    }
}