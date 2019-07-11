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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionGraph.class);

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    static Supplier<URI> uriSupplier = () -> {
        return URI.create("urn:uri:pass:entity:" + COUNTER.getAndIncrement());
    };

    private static Submission submission;

    private static List<LinkInstruction> linkInstructions;

    private static Map<URI, PassEntity> entities;

    public static Predicate<PassEntity> SUBMISSION = (entity) -> entity instanceof Submission;

    public static SubmissionGraph newGraph() {
        return new SubmissionGraph();
    }

    private SubmissionGraph() {
        submission = new Submission();
        entities = new HashMap<>();
        linkInstructions = new ArrayList<>();
        submission.setId(uriSupplier.get());
        entities.put(submission.getId(), submission);
    }

    public static Submission submission() {
        return (Submission) entities.values().stream()
                .filter(SUBMISSION)
                .findAny()
                .orElseThrow(() -> new RuntimeException("Missing Submission entity"));
    }
    public void walk(Predicate<PassEntity> p, BiConsumer<Submission, PassEntity> c) {
        entities.values().stream()
                .filter(p)
                .forEach(entity -> c.accept(submission, entity));
    }

    private <T extends PassEntity> void add(T passEntity) {
        entities.put(passEntity.getId(), passEntity);
    }

    private PassEntity get(URI u) {
        return entities.get(u);
    }

    public <T extends PassEntity> T get(URI u, Class<T> type) {
        return type.cast(entities.get(u));
    }

    public LinkInstruction link(PassEntity entity) {
        LinkInstruction li = new LinkInstruction();
        linkInstructions.add(li);
        return li.link(entity);
    }

    public LinkInstruction link(Predicate<PassEntity> withPredicate) {
        LinkInstruction li = new LinkInstruction();
        linkInstructions.add(li);
        return li.link(withPredicate);
    }

    public enum Rel {
        PRIMARY_FUNDER,
        DIRECT_FUNDER,
        PREPARER,
        SUBMITTER,
        PI,
        COPI,
        EFFECTIVE_POLICY,
        PUBLICATION,
        GRANT,
        JOURNAL,
        PUBLISHER
    }

    public SubmissionGraph link() {
        if (linkInstructions.isEmpty()) {
            // no link instructions, do nothing
            LOG.trace("No LinkInstructions to process, returning.");
            return this;
        }

        LOG.trace("Processing {} LinkInstructions", linkInstructions.size());
        linkInstructions.forEach(li -> LOG.trace("  {}", li));

        Iterator<LinkInstruction> iterator = linkInstructions.iterator();

        while (iterator.hasNext()) {
            LinkInstruction li = iterator.next();
            LOG.trace("  Processing LinkInstruction {}@{}",
                    li.getClass().getSimpleName(),
                    Integer.toHexString(System.identityHashCode(li)));
            PassEntity source = null;
            if (li.sourcePredicate != null) {
                source = entities.values().stream()
                        .filter(li.sourcePredicate)
                        .findAny()
                        .orElseThrow(() -> new RuntimeException("Missing source entity"));
            } else {
                source = li.source;
            }

            PassEntity target= null;
            if (li.targetPredicate != null) {
                target = entities.values().stream()
                        .filter(li.targetPredicate)
                        .findAny()
                        .orElseThrow(() -> new RuntimeException("Missing target entity"));
            } else {
                target = li.target;
            }

            LOG.trace("    Linking {} ({}) to {} ({}) using {}",
                    target.getClass().getSimpleName(),
                    target.getId(),
                    source.getClass().getSimpleName(),
                    source.getId(),
                    li.rel);

            try {
                linkUsingRel(target, source, Rel.valueOf(li.rel));
            } catch (IllegalArgumentException e) {
                linkUsingField(target, source, li.rel);
            }

            // Remove successfully processed LinkInstructions
            iterator.remove();
        }

        return this;
    }

    private void linkUsingField(PassEntity target, PassEntity source, String fieldName) {
        Method setter;

        if (Character.isLowerCase(fieldName.charAt(0))) {
            fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1, fieldName.length());
        }

        try {
            setter = target.getClass().getMethod("set" + fieldName, URI.class);
            setter.invoke(target, source.getId());
        } catch (NoSuchMethodException e) {
            Method getter = null;
            try {
                getter = target.getClass().getMethod("get" + fieldName);
                if (Collection.class.isAssignableFrom(getter.getReturnType())) {
                    ((Collection)getter.invoke(target)).add(source.getId());
                    return;
                }
                throw new RuntimeException(String.format("No such method set%s on %s", fieldName, target.getClass().getSimpleName()));
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(String.format("No such method get%s on %s", fieldName, target.getClass().getSimpleName()));
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(String.format("Error accessing get%s on %s: %s", fieldName,
                        target.getClass().getSimpleName(), e.getMessage()), e);
            } catch (InvocationTargetException ex) {
                throw new RuntimeException(String.format("Error invoking get%s on %s: %s", fieldName,
                        target.getClass().getSimpleName(), e.getMessage()), e);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(String.format("Error accessing set%s on %s: %s", fieldName,
                    target.getClass().getSimpleName(), e.getMessage()), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(String.format("Error invoking get%s on %s: %s", fieldName,
                    target.getClass().getSimpleName(), e.getMessage()), e);
        }
    }

    private void linkUsingRel(PassEntity target, PassEntity source, Rel rel) {
        switch (rel) {
            case PI:
                ((Grant) target).setPi(source.getId());
                break;
            case COPI:
                ((Grant) target).getCoPis().add(source.getId());
                break;
            case PREPARER:
                ((Submission) target).getPreparers().add(source.getId());
                break;
            case SUBMITTER:
                ((Submission) target).setSubmitter(source.getId());
                break;
            case DIRECT_FUNDER:
                ((Grant) target).setDirectFunder(source.getId());
                break;
            case PRIMARY_FUNDER:
                ((Grant) target).setPrimaryFunder(source.getId());
                break;
            case EFFECTIVE_POLICY:
                ((Submission) target).getEffectivePolicies().add(source.getId());
                break;
            default:
                throw new RuntimeException("Unknown or unhandled relationship: " + rel);
        }
    }

    public <T extends PassEntity> GenericBuilder<T> builderFor(Class<T> type) {
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

        private GenericBuilder(Supplier<T> s) {
            if (entities == null) {
                throw new IllegalStateException(String.format("%s.%s must be invoked prior to using %s",
                        SubmissionGraph.class.getSimpleName(), "newGraph()", GenericBuilder.class.getSimpleName()));
            }
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
                setter = toBuild.getClass().getMethod("set" + fieldName, fieldType);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(String.format("No such method set%s on %s", fieldName, toBuild.getClass().getSimpleName()));
            }

            try {
                setter.invoke(toBuild, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return this;
        }

        public GenericBuilder<T> linkFrom(Predicate<PassEntity> predicate, String rel) {
            linkInstructions.add(new LinkInstruction().link(toBuild).to(predicate).as(rel));
            return this;
        }

        public GenericBuilder<T> linkFrom(PassEntity entity, String rel) {
            linkInstructions.add(new LinkInstruction().link(toBuild).to(entity).as(rel));
            return this;
        }

        public GenericBuilder<T> linkTo(Predicate<PassEntity> predicate, String rel) {
            linkInstructions.add(new LinkInstruction().link(predicate).to(toBuild).as(rel));
            return this;
        }

        public GenericBuilder<T> linkTo(PassEntity entity, String rel) {
            linkInstructions.add(new LinkInstruction().link(entity).to(toBuild).as(rel));
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
        private Predicate<PassEntity> sourcePredicate;
        private Predicate<PassEntity> targetPredicate;
        private PassEntity source;
        private PassEntity target;
        private String rel;

        LinkInstruction link(PassEntity entity) {
            source = entity;
            return this;
        }

        LinkInstruction link(Predicate<PassEntity> predicate) {
            sourcePredicate = predicate;
            return this;
        }

        static Predicate<PassEntity> entityHaving(String fieldName, String value) {
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
                } catch (NoSuchMethodException e) {
                    // not the droid we are looking for
                    return false;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(String.format("Error accessing get%s on %s: %s", field,
                            entity.getClass().getSimpleName(), e.getMessage()), e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(String.format("Error invoking get%s on %s: %s", field,
                            entity.getClass().getSimpleName(), e.getMessage()), e);
                }
            });

            return withPredicate;
        }

        LinkInstruction to(PassEntity entity) {
            target = entity;
            return this;
        }

        LinkInstruction to(Predicate<PassEntity> predicate) {
            targetPredicate = predicate;
            return this;
        }

        LinkInstruction as(Rel rel) {
            this.rel = rel.name();
            return this;
        }

        LinkInstruction as(String rel) {
            this.rel = rel;
            return this;
        }

//        @Override
//        public String toString() {
//            return "LinkInstruction{" + "sourcePredicate=" + sourcePredicate + ", targetPredicate=" + targetPredicate + ", source=" + source + ", target=" + target + ", rel=" + rel + '}';
//        }
    }

}
