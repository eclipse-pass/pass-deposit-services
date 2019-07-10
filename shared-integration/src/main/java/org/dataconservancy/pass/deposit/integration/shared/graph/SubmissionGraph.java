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

import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.lang.Character.toUpperCase;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionGraph {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    static Supplier<URI> uriSupplier = () -> {
        return URI.create("urn:uri:pass:entity:" + COUNTER.getAndIncrement());
    };

    private Submission submission;

    private static Map<URI, ? super PassEntity> entities = new HashMap<>();

    public SubmissionGraph() {
        submission = new Submission();
        submission.setId(uriSupplier.get());
        entities.put(submission.getId(), submission);
    }

    public <T extends PassEntity> void add(T passEntity) {
        entities.put(passEntity.getId(), passEntity);
    }

    public PassEntity get(URI u) {
        return (PassEntity)entities.get(u);
    }

    public <T extends PassEntity> T get(URI u, Class<T> type) {
        return type.cast(entities.get(u));
    }

    public static class GenericBuilder<T> {

        private Supplier<T> s;

        private T toBuild;

        public GenericBuilder(Supplier<T> s) {
            this.s = s;
            this.toBuild = s.get();
        }

        public GenericBuilder<T> set(String fieldName, String value) {
            return set(fieldName, String.class, value);
        }

        public <U> GenericBuilder<T> add(String fieldName, U value) {
            Method getter;

            if (Character.isLowerCase(fieldName.charAt(0))) {
                fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
            }

            try {
                getter = Grant.class.getMethod("get" + fieldName);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            try {
                Object obj = getter.invoke(toBuild);
                if (obj instanceof Collection) {
                    ((Collection)obj).add(value);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return this;
        }

        public <U> GenericBuilder<T> set(String fieldName, Class<U> fieldType, U value) {
            Method setter;

            if (Character.isLowerCase(fieldName.charAt(0))) {
                fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
            }

            try {
                setter = Grant.class.getMethod("set" + fieldName, fieldType);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            try {
                setter.invoke(toBuild, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return this;
        }

        public T build() {
            return toBuild;
        }
    }

//    public static class Linker {
//
//        public <T extends PassEntity> void link(PassEntity entity, Class<T> type) {
//            entities.values().stream().filter(target -> {
//                Arrays.stream(target.getClass().getDeclaredMethods())
//                        .filter(method -> method.getName().startsWith("get"))
//                        .filter(method -> method.get)
//                })
//            })
//        }
//
//    }

    public static class GrantBuilder {

        private Supplier<Grant> s = () -> {
            Grant grant = new Grant();
            grant.setId(uriSupplier.get());
            return grant;
        };

        private Grant toBuild = s.get();

        public GrantBuilder set(String fieldName, String value) {
            return set(fieldName, String.class, value);
        }

        public <T> GrantBuilder add(String fieldName, T value) {
            Method getter;

            if (Character.isLowerCase(fieldName.charAt(0))) {
                fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
            }

            try {
                getter = Grant.class.getMethod("get" + fieldName);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            try {
                Object obj = getter.invoke(toBuild);
                if (obj instanceof Collection) {
                    ((Collection)obj).add(value);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return this;
        }

        public <T> GrantBuilder set(String fieldName, Class<T> fieldType, T value) {
            Method setter;

            if (Character.isLowerCase(fieldName.charAt(0))) {
                fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
            }

            try {
                setter = Grant.class.getMethod("set" + fieldName, fieldType);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            try {
                setter.invoke(toBuild, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return this;
        }

        public Grant build() {
            return toBuild;
        }
    }

    public class RepositoryBuilder {

    }

    public class JournalBuilder {

    }

    public class PublicationBuilder {

    }

    public class PolicyBuilder {

    }

    public class FunderBuilder {

    }

    public class UserBuilder {

    }

    public class FileBuilder {

    }

}
