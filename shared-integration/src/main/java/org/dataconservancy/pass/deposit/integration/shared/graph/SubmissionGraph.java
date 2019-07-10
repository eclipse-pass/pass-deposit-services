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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
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

    private static Submission submission;

    private static List<LinkInstruction> linkInstructions;

    private static Map<URI, PassEntity> entities;

    public static SubmissionGraph newGraph() {
        return new SubmissionGraph();
    }

    public void walk(Predicate<PassEntity> p, BiConsumer<Submission, PassEntity> c) {
        entities.values().stream()
                .filter(p)
                .forEach(entity -> c.accept(submission, entity));
    }

    private SubmissionGraph() {
        submission = new Submission();
        entities = new HashMap<>();
        linkInstructions = new ArrayList<>();
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

    public LinkInstruction linkEntity(PassEntity entity) {
        LinkInstruction li = new LinkInstruction();
        linkInstructions.add(li);
        return li.linkEntity(entity);
    }

    public LinkInstruction linkEntity(Predicate<PassEntity> withPredicate) {
        LinkInstruction li = new LinkInstruction();
        linkInstructions.add(li);
        return li.linkEntity(withPredicate);
    }

    public enum Rel {
        PRIMARY_FUNDER,
        DIRECT_FUNDER,
        PREPARER,
        SUBMITTER,
        PI,
        COPI,
        EFFECTIVE_POLICY
    }

    public SubmissionGraph link() {
        linkInstructions.forEach(li -> {
            PassEntity source = null;
            if (li.withPredicate != null) {
                source = entities.values().stream().filter(li.withPredicate).findAny().orElseThrow(() -> new RuntimeException("Missing entity"));
            } else {
                source = li.source;
            }

            switch (li.asRel) {
                case PI:
                    ((Grant) li.linkTo).setPi(source.getId());
                    break;
                case COPI:
                    ((Grant) li.linkTo).getCoPis().add(source.getId());
                    break;
                case PREPARER:
                    ((Submission) li.linkTo).getPreparers().add(source.getId());
                    break;
                case SUBMITTER:
                    ((Submission) li.linkTo).setSubmitter(source.getId());
                    break;
                case DIRECT_FUNDER:
                    ((Grant) li.linkTo).setDirectFunder(source.getId());
                    break;
                case PRIMARY_FUNDER:
                    ((Grant) li.linkTo).setPrimaryFunder(source.getId());
                    break;
                case EFFECTIVE_POLICY:
                    ((Submission) li.linkTo).getEffectivePolicies().add(source.getId());
                    break;
                default:
                    throw new RuntimeException("Unknown or unhandled relationship: " + li.asRel);
            }
        });

        return this;
    }

    public <T extends PassEntity> GenericBuilder<T> builderFor(Class<T> type) {
        return builderFor(type, null);
    }

    public <T extends PassEntity> GenericBuilder<T> builderFor(Class<T> type, Rel rel) {
        return new GenericBuilder<T>(() -> {
            T instance = null;
            try {
                instance = type.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            instance.setId(uriSupplier.get());
            return instance;
        });
    }

    public static class GenericBuilder<T extends PassEntity> {

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
                getter = toBuild.getClass().getMethod("get" + fieldName);
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
            return build((submission, toBuild) -> toBuild);
        }

        public T build(BiFunction<Submission, T, T> func) {
            func = func.andThen(built -> {
                entities.put(built.getId(), built);
                return built;
            });
            return func.apply(submission, toBuild);
        }

        public T build(TertiaryFunction<Submission, Map<URI, ? super PassEntity>, T, T> func) {
            func = func.andThen(built -> {
                entities.put(built.getId(), built);
                return built;
            });
            return func.apply(submission, entities, toBuild);
        }
    }

    @FunctionalInterface
    interface TertiaryFunction<T, U, S, R> {

        R apply(T t, U u, S s);

        default <V> TertiaryFunction<T, U, S, V> andThen(Function<? super R, ? extends V> after) {
            Objects.requireNonNull(after);
            return (T t, U u, S s) -> after.apply(apply(t, u, s));
        }

    }

    static class LinkInstruction {
        private Predicate<PassEntity> withPredicate;
        private PassEntity source;
        private PassEntity linkTo;
        private Rel asRel;

        LinkInstruction linkEntity(PassEntity entity) {
            source = entity;
            return this;
        }

        LinkInstruction linkEntity(Predicate<PassEntity> predicate) {
            withPredicate = predicate;
            return this;
        }

        static Predicate<PassEntity> with(String fieldName, String value) {
            Predicate<PassEntity> withPredicate = (entity -> {
                String field;
                if (Character.isLowerCase(fieldName.charAt(0))) {
                    field = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
                } else {
                    field = fieldName;
                }

                try {
                    Method getter = entity.getClass().getMethod("get" + field);
                    if (Collection.class.isAssignableFrom(getter.getReturnType())) {
                        return ((Collection) getter.invoke(entity)).contains(value);
                    }
                    return getter.invoke(entity).equals(value);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            return withPredicate;
        }

        LinkInstruction to(PassEntity entity) {
            linkTo = entity;
            return this;
        }

        LinkInstruction as(Rel rel) {
            asRel = rel;
            return this;
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

//    public static class GrantBuilder {
//
//        private Supplier<Grant> s = () -> {
//            Grant grant = new Grant();
//            grant.setId(uriSupplier.get());
//            return grant;
//        };
//
//        private Grant toBuild = s.get();
//
//        public GrantBuilder set(String fieldName, String value) {
//            return set(fieldName, String.class, value);
//        }
//
//        public <T> GrantBuilder add(String fieldName, T value) {
//            Method getter;
//
//            if (Character.isLowerCase(fieldName.charAt(0))) {
//                fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
//            }
//
//            try {
//                getter = Grant.class.getMethod("get" + fieldName);
//            } catch (NoSuchMethodException e) {
//                throw new RuntimeException(e);
//            }
//
//            try {
//                Object obj = getter.invoke(toBuild);
//                if (obj instanceof Collection) {
//                    ((Collection)obj).add(value);
//                }
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//
//            return this;
//        }
//
//        public <T> GrantBuilder set(String fieldName, Class<T> fieldType, T value) {
//            Method setter;
//
//            if (Character.isLowerCase(fieldName.charAt(0))) {
//                fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
//            }
//
//            try {
//                setter = Grant.class.getMethod("set" + fieldName, fieldType);
//            } catch (NoSuchMethodException e) {
//                throw new RuntimeException(e);
//            }
//
//            try {
//                setter.invoke(toBuild, value);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//
//            return this;
//        }
//
//        public Grant build() {
//            return toBuild;
//        }
//    }
//
//    public class RepositoryBuilder {
//
//    }
//
//    public class JournalBuilder {
//
//    }
//
//    public class PublicationBuilder {
//
//    }
//
//    public class PolicyBuilder {
//
//    }
//
//    public class FunderBuilder {
//
//    }
//
//    public class UserBuilder {
//
//    }
//
//    public class FileBuilder {
//
//    }

}
