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
import org.dataconservancy.pass.model.Grant;
import org.dataconservancy.pass.model.PassEntity;
import org.dataconservancy.pass.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Character.toUpperCase;

/**
 * Encapsulates a collection of {@link PassEntity} objects, loosely modeled as a graph rooted with a {@link Submission}.
 * <p>
 * A graph may be created one of two ways:
 * <ol>
 *     <li>inner classes supporting a reflection-based fluent builder API</li>
 *     <li>reading in a graph serialized as JSON</li>
 * </ol>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class SubmissionGraph {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionGraph.class);

    /**
     * Counter supplying unique integers for URIs
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * Creates unique URIs for each entity built by the fluent API
     */
    static Supplier<URI> uriSupplier = () -> {
        return URI.create("urn:uri:pass:entity:" + COUNTER.getAndIncrement());
    };

    /**
     * Determines if the supplied entity is an instance of a {@link Submission}
     */
    public static Predicate<PassEntity> SUBMISSION = (entity) -> entity instanceof Submission;

    /**
     * The {@code Submission} which is the root of the graph
     */
    private Submission submission;

    /**
     * Members of the graph indexed by their URI
     */
    private ConcurrentHashMap<URI, PassEntity> entities;

    /**
     * Adapter used to convert between JSON serialization and Fedora
     */
    private PassJsonFedoraAdapter adapter = new PassJsonFedoraAdapter();

    /**
     * Creates a new instance of a graph, using the supplied map for the members of the graph.  Wraps the {@code
     * HashMap} as a {@code ConcurrentHashMap}.
     *
     * @param entities the graph members; must contain at least one {@code Submission}, may never be {@code null}
     * @throws IllegalArgumentException if the supplied {@code HashMap} does not contain a {@code Submission}
     */
    private SubmissionGraph(HashMap<URI, PassEntity> entities) {
        this(new ConcurrentHashMap<>(entities));
    }

    /**
     * Creates a new instance of a graph, using the supplied map for the members of the graph.
     *
     * @param entities the graph members; must contain at least one {@code Submission}, may never be {@code null}
     * @throws IllegalArgumentException if the supplied {@code ConcurrentHashMap} does not contain a {@code Submission}
     */
    private SubmissionGraph(ConcurrentHashMap<URI, PassEntity> entities) {
        Objects.requireNonNull(entities, "Entities must not be null!");
        this.entities = entities;
        this.submission = (Submission) entities.values().stream().filter(SUBMISSION).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Entities are missing expected Submission entity."));
    }

    /**
     * The {@code Submission} at the root of the graph.
     *
     * @return the Submission
     */
    public Submission submission() {
        return submission;
    }

    /**
     * The members of the graph as a {@code &lt;Stream&gt;}
     *
     * @return the members of the graph
     */
    public Stream<PassEntity> stream() {
        return Streamer.stream(entities);
    }

    /**
     * The members of the graph matching the supplied {@code Predicate}
     *
     * @param p {@code Predicate} used to test each entity in the graph
     * @return a {@code &gt;Stream&lt;} of matching entities
     */
    public Stream<PassEntity> stream(Predicate<PassEntity> p) {
        return Streamer.stream(entities).filter(p);
    }

    /**
     * The members of the graph that are instances of the supplied {@code Class}. Equivalent to:
     * <pre>stream(entity -> clazz.isAssignableFrom(entity.getClass()))</pre>
     *
     * @param clazz the {@code Class} used to test the type of each entity
     * @return a {@code &gt;Stream&lt;} of matching entities
     */
    public Stream<PassEntity> stream(Class<? extends PassEntity> clazz) {
        return stream(entity -> clazz.isAssignableFrom(entity.getClass()));
    }

    /**
     * Apply the supplied consumer to entities of the graph matching the predicate.
     *
     * @param p the predicate matching entities of the graph
     * @param c the consumer which will be supplied the {@code Submission} at the root of the graph as well as the
     *          entity matched by the {@code Predicate}
     * @return this graph
     */
    public SubmissionGraph walk(Predicate<PassEntity> p, BiConsumer<Submission, PassEntity> c) {
        stream(p).forEach(entity -> c.accept(submission, entity));
        return this;
    }

    /**
     * Apply the supplied consumer to entities of the graph matching the supplied {@code Class}.
     *
     * @param clazz the {@code Class} matching entities of the graph
     * @param c the consumer which will be supplied the {@code Submission} at the root of the graph as well as the
     *          entity matched by the {@code Predicate}
     * @return this graph
     */
    public SubmissionGraph walk(Class<? extends PassEntity> clazz, BiConsumer<Submission, PassEntity> c) {
        stream(entity -> clazz.isAssignableFrom(entity.getClass())).forEach(entity -> c.accept(submission, entity));
        return this;
    }

    /**
     * Adds an entity to the graph.
     *
     * @param passEntity the entity
     * @param <T> the type of the entity
     */
    private <T extends PassEntity> void add(T passEntity) {
        entities.put(passEntity.getId(), passEntity);
    }

    /**
     * Retrieve an entity from the graph by its URI.
     *
     * @param u the URI of the entity
     * @return the entity, or {@code null} if it can't be found
     */
    private PassEntity get(URI u) {
        return entities.get(u);
    }

    /**
     * Removes the entity identified by the {@code URI} from the graph, and all references to the entity.
     *
     * @param u the {@code URI} identifying a member of the graph
     * @return this graph
     */
    public SubmissionGraph remove(URI u) {
        removeEntity(u, entities);
        return this;
    }

    public InputStream asJson() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        adapter.passToJson(new HashMap<>(entities), out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static class Streamer {

        private static Stream<PassEntity> stream(Map<URI, PassEntity> entities) {
            return entities.values().stream();
        }

    }

    /**
     * Removes the entity from the graph identified by the {@code URI}.  The members of the graph are represented in
     * the supplied {@code ConcurrentHashMap}.
     * <p>
     * This method not only removes the {@code PassEntity} from the {@code ConcurrentHashMap}, it will also remove any
     * references to the entity by iterating over each field of each remaining {@code PassEntity} in the graph.  For
     * each field whose type is a {@code Collection}, {@code Map}, or {@code URI}:
     * </p>
     * <ul>
     *     <li>If a {@code Collection} contains the URI of the removed entity, the entry is removed from the {@code
     *         Collection}</li>
     *     <li>If a {@code Map} contains the URI as a key or value, the entry is removed from the {@code Map}</li>
     *     <li>If a {@code URI} is equal to the {@code URI} of the removed entity, the field is nulled out</li>
     * </ul>
     *
     * @param u the URI of the entity being removed
     * @param entities the members of the graph
     */
    private static void removeEntity(URI u, ConcurrentHashMap<URI, PassEntity> entities) {
        Objects.requireNonNull(u, "Supplied URI must not be null");
        entities.remove(u);

        // iterate over remaining entities, and remove any references to the removed entity's URI

        entities.values().forEach(entity -> {
            Arrays.stream(entity.getClass().getDeclaredFields())
                    .filter(field -> URI.class.isAssignableFrom(field.getType())
                            || Collection.class.isAssignableFrom(field.getType())
                            || Map.class.isAssignableFrom(field.getType()))
                    .peek(field -> field.setAccessible(true))
                    .forEach(field -> {
                        try {
                            if (Collection.class.isAssignableFrom(field.getType())) {
                                ((Collection) field.get(entity)).remove(u);
                            } else if (Map.class.isAssignableFrom(field.getType())) {
                                Map map = ((Map) field.get(entity));
                                if (map.containsKey(u)) {
                                    map.remove(u);
                                }
                                if (map.containsValue(u)) {
                                    AtomicReference key = new AtomicReference();
                                    map.forEach((k, v) -> {
                                        if (u.equals(v)) {
                                            key.set(k);
                                        }
                                    });

                                    map.remove(key.get());
                                }
                            } else {
                                if (u.equals(field.get(entity))) {
                                    field.set(entity, null);
                                }
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    });
        });
    }

    /**
     * Type-safe method for obtaining a member from the graph.
     *
     * @param u the URI of the entity to retrieve
     * @param type the type of the object identified by the URI
     * @param <T> the type of the object
     * @return the member of the graph, or {@code null} if the member was not found
     */
    public <T extends PassEntity> T get(URI u, Class<T> type) {
        PassEntity obj = entities.get(u);
        if (obj != null) {
            return type.cast(obj);
        }

        return null;
    }

    /**
     * Enums of popular relationships.
     */
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


    private static void linkUsingField(PassEntity target, PassEntity source, String fieldName) {
        Method setter;

        fieldName = upperCase(fieldName);

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

    private static void linkUsingRel(PassEntity target, PassEntity source, Rel rel) {
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


    public static class GraphBuilder {

        private Submission submission;

        private List<LinkInstruction> linkInstructions;

        private ConcurrentHashMap<URI, PassEntity> entities;

        public static GraphBuilder newGraph() {
            return new GraphBuilder();
        }

        public static GraphBuilder newGraph(InputStream in, PassJsonFedoraAdapter adapter) {
            HashMap<URI, PassEntity> entities = new HashMap<>();
            adapter.jsonToPass(in, entities);
            return new GraphBuilder(new ConcurrentHashMap<URI, PassEntity>(entities));
        }

        private GraphBuilder() {
            this.submission = new Submission();
            this.entities = new ConcurrentHashMap<>();
            this.linkInstructions = new ArrayList<>();
            this.submission = new Submission();
            this.submission.setId(uriSupplier.get());
            this.entities.put(submission.getId(), submission);
        }

        private GraphBuilder(HashMap<URI, PassEntity> entities) {
            this(new ConcurrentHashMap<>(entities));
        }

        private GraphBuilder(ConcurrentHashMap<URI, PassEntity> entities) {
            Objects.requireNonNull(entities, "Entities must not be null!");
            this.submission = (Submission) entities.values().stream().filter(SUBMISSION).findAny()
                    .orElseThrow(() -> new IllegalArgumentException("Entities is missing a required Submission entity."));
            this.entities = entities;
            this.linkInstructions = new ArrayList<>();
        }

        public Submission submission() {
            return submission;
        }

        public SubmissionGraph build() {
            SubmissionGraph graph = new SubmissionGraph(entities);
            link();

            return graph;
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

        private GraphBuilder link() {
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
                LOG.trace("  Processing LinkInstruction {}@{}", li.getClass().getSimpleName(),
                        Integer.toHexString(System.identityHashCode(li)));
                PassEntity source = null;
                if (li.sourcePredicate != null) {
                    source =
                            entities.values().stream().filter(li.sourcePredicate).findAny().orElseThrow(() -> new RuntimeException("Missing source entity"));
                } else {
                    source = li.source;
                }

                PassEntity target = null;
                if (li.targetPredicate != null) {
                    target =
                            entities.values().stream().filter(li.targetPredicate).findAny().orElseThrow(() -> new RuntimeException("Missing target entity"));
                } else {
                    target = li.target;
                }

                LOG.trace("    Linking {} ({}) to {} ({}) using {}", target.getClass().getSimpleName(),
                        target.getId(), source.getClass().getSimpleName(), source.getId(), li.rel);

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

        public <T extends PassEntity> EntityBuilder<T> addEntity(Class<T> type) {
            return new EntityBuilder<T>(() -> {
                T instance = null;
                try {
                    instance = type.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                instance.setId(uriSupplier.get());
                return instance;
            }, entities, linkInstructions);
        }

        public GraphBuilder removeEntity(URI u) {
            SubmissionGraph.removeEntity(u, entities);
            return this;
        }

        public Stream<PassEntity> stream() {
            return Streamer.stream(entities);
        }

        public Stream<PassEntity> stream(Predicate<PassEntity> p) {
            return Streamer.stream(entities).filter(p);
        }

        public Stream<PassEntity> stream(Class<? extends PassEntity> clazz) {
            return stream(entity -> clazz.isAssignableFrom(entity.getClass()));
        }

        public GraphBuilder walk(Predicate<PassEntity> p, BiConsumer<Submission, PassEntity> c) {
            stream(p).forEach(entity -> c.accept(submission, entity));
            return this;
        }

        public GraphBuilder walk(Class<? extends PassEntity> clazz, BiConsumer<Submission, PassEntity> c) {
            stream(entity -> clazz.isAssignableFrom(entity.getClass())).forEach(entity -> c.accept(submission, entity));
            return this;
        }

    }

    /**
     * The EntityBuilder shares state with the GraphBuilder supplied on construction:
     * - the entities in the graph
     * - the linking instructions
     *
     * @param <T> the type of {@code PassEntity} being built
     */
    public static class EntityBuilder<T extends PassEntity> {

        private T toBuild;

        private Map<URI, PassEntity> entities;

        private List<LinkInstruction> linkInstructions;

        private Submission submission;

        private EntityBuilder(Supplier<T> s, Map<URI, PassEntity> entities, List<LinkInstruction> linkInstructions) {
            Objects.requireNonNull(s, "Entity Supplier must not be null.");
            Objects.requireNonNull(entities, "Entities Map must not be null.");
            Objects.requireNonNull(linkInstructions, "LinkInstructions must not be null");
            this.toBuild = s.get();
            this.entities = entities;
            this.linkInstructions = linkInstructions;
            this.submission = (Submission) entities.values().stream().filter(SUBMISSION).findAny()
                    .orElseThrow(() -> new RuntimeException("Supplied graph is missing a Submission entity."));
        }

        public EntityBuilder<T> set(String fieldName, String value) {
            return set(fieldName, String.class, value);
        }

        public <U> EntityBuilder<T> add(String fieldName, U value) {
            Method getter;

            fieldName = upperCase(fieldName);

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

        public <U> EntityBuilder<T> set(String fieldName, Class<U> fieldType, U value) {
            Method setter;

            fieldName = upperCase(fieldName);

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

        public EntityBuilder<T> linkFrom(Predicate<PassEntity> predicate, String rel) {
            linkInstructions.add(new LinkInstruction().link(toBuild).to(predicate).as(rel));
            return this;
        }

        public EntityBuilder<T> linkFrom(PassEntity entity, String rel) {
            linkInstructions.add(new LinkInstruction().link(toBuild).to(entity).as(rel));
            return this;
        }

        public EntityBuilder<T> linkTo(Predicate<PassEntity> predicate, String rel) {
            linkInstructions.add(new LinkInstruction().link(predicate).to(toBuild).as(rel));
            return this;
        }

        public EntityBuilder<T> linkTo(PassEntity entity, String rel) {
            linkInstructions.add(new LinkInstruction().link(entity).to(toBuild).as(rel));
            return this;
        }

        public Submission submission() {
            return submission;
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

    private static String upperCase(String fieldName) {
        if (Character.isLowerCase(fieldName.charAt(0))) {
            fieldName = Character.toString(toUpperCase(fieldName.charAt(0))) + fieldName.subSequence(1,
                    fieldName.length());
        }
        return fieldName;
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
