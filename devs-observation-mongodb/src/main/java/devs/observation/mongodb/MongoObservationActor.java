package devs.observation.mongodb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import devs.msg.Branch;
import devs.msg.Run;
import devs.observation.DevsObservationMessage;
import devs.observation.Observation;
import devs.observation.ObservationSinkKeys;
import devs.observation.ObservationTypeEntry;
import devs.observation.StopLogger;
import devs.utils.DevsObjectMapper;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Observation sink actor that persists shared observation messages to MongoDB collections.
 */
public class MongoObservationActor extends AbstractBehavior<DevsObservationMessage> {

    /**
     * Collection that stores the observation type catalog.
     */
    public static final String OBSERVATION_TYPES_COLLECTION = "observation_types";

    protected final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    protected final MongoClient mongoClient;
    protected final MongoDatabase mongoDatabase;
    protected final Map<String, DevsObservationMessage> pendingInserts = new HashMap<>();
    protected final Set<String> initializedCollections = new HashSet<>();
    protected final Set<String> knownObservationTypes = new HashSet<>();
    protected final TimerScheduler<DevsObservationMessage> timers;
    protected final StashBuffer<DevsObservationMessage> stash;

    /**
     * Creates a MongoDB-backed observation sink behavior.
     *
     * @param mongoConnectionString MongoDB connection string
     * @param database target database name
     * @return configured observation sink behavior
     */
    public static Behavior<DevsObservationMessage> create(String mongoConnectionString, String database) {
        return Behaviors.setup(context -> {
            return Behaviors.withTimers(timers -> {
                return Behaviors.withStash(1000, stash -> {
                    return new MongoObservationActor(context, timers, stash, mongoConnectionString, database);
                });
            });
        });
    }

    /**
     * Creates a MongoDB-backed observation sink actor.
     *
     * @param context actor context
     * @param timers timer scheduler used for delayed shutdown retries
     * @param stash stash used while initialization is still in progress
     * @param mongoConnectionString MongoDB connection string
     * @param database target database name
     */
    public MongoObservationActor(ActorContext<DevsObservationMessage> context,
                                TimerScheduler<DevsObservationMessage> timers,
                                StashBuffer<DevsObservationMessage> stash,
                                String mongoConnectionString,
                                String database) {
        super(context);
        this.timers = timers;
        this.stash = stash;
        try {
            this.mongoClient = MongoClients.create(mongoConnectionString);
            this.mongoDatabase = this.mongoClient.getDatabase(database);
            getContext().getLog().debug("Created mongodb observation actor to {}", mongoConnectionString);

            this.mongoDatabase.listCollectionNames()
                    .subscribe(new CollectionExistsSubscriber(getContext().getSelf(), getContext().getLog()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Receive<DevsObservationMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(LoadObservationTypes.class, this::onLoadObservationTypes)
                .onMessage(InitializationResult.class, this::onInitializationResult)
                .onMessage(DevsObservationMessage.class, msg -> {
                    stash.stash(msg);
                    return Behaviors.same();
                })
                .build();
    }

    private Behavior<DevsObservationMessage> onLoadObservationTypes(LoadObservationTypes ignored) {
        loadObservationTypes();
        return Behaviors.same();
    }

    private void loadObservationTypes() {
        this.mongoDatabase.getCollection(OBSERVATION_TYPES_COLLECTION)
                .find()
                .subscribe(new InitializationSubscriber(getContext().getSelf()));
    }

    private Behavior<DevsObservationMessage> onInitializationResult(InitializationResult result) {
        this.knownObservationTypes.addAll(result.existingTypes());
        getContext().getLog().info("Initialized with {} existing observation types", knownObservationTypes.size());

        // Now that we are initialized, register with the receptionist
        getContext().getSystem().receptionist().tell(
                Receptionist.register(ObservationSinkKeys.OBSERVATION_SINK_KEY, getContext().getSelf()));

        return stash.unstashAll(ready());
    }

    private Receive<DevsObservationMessage> ready() {
        return newReceiveBuilder()
                .onMessage(Observation.class, this::onObservation)
                .onMessage(Run.class, this::onRun)
                .onMessage(Branch.class, this::onBranch)
                .onMessage(ObservationTypeEntry.class, this::onObservationTypeEntry)
                .onMessage(InsertResult.class, this::onInsertResult)
                .onMessage(StopLogger.class, this::onStopLogger)
                .build();
    }

    protected Behavior<DevsObservationMessage> onObservation(Observation<?, ?> observation) {
        String insertId = observation._id();
        String observationType = observation.getObservationType();

        // Check if this is a new observation type and publish it if so
        if (!knownObservationTypes.contains(observationType)) {
            ObservationTypeEntry typeEntry = ObservationTypeEntry.builder()
                    .typeId(observationType)
                    .archiveName(observationType)
                    .build();
            publishObservationType(typeEntry);
            knownObservationTypes.add(observationType);
        }

        Optional<Document> documentOption = toDocument(observation);
        if (documentOption.isPresent()) {
            pendingInserts.put(insertId, observation);
            String collectionName = observationType;
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collectionName);

            // Create indices if this is the first time we see this observation type in this session
            if (!initializedCollections.contains(collectionName)) {
                // Compound index for replay performance: runId, branchId, and simulationTime (mapped to time in Observation)
                mongoCollection.createIndex(new Document("runId", 1)
                        .append("branchId", 1)
                        .append("time", 1))
                        .subscribe(new IndexSubscriber(collectionName, getContext().getLog()));
                initializedCollections.add(collectionName);
            }

            mongoCollection.insertOne(documentOption.get())
                    .subscribe(new InsertSubscriber(insertId, getContext().getSelf()));
        }
        return Behaviors.same();
    }

    protected Behavior<DevsObservationMessage> onObservationTypeEntry(ObservationTypeEntry entry) {
        publishObservationType(entry);
        knownObservationTypes.add(entry.getTypeId());
        return Behaviors.same();
    }

    private void publishObservationType(ObservationTypeEntry entry) {
        String insertId = entry.getTypeId();
        Optional<Document> documentOption = toDocument(entry);
        if (documentOption.isPresent()) {
            pendingInserts.put(insertId, entry);
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(OBSERVATION_TYPES_COLLECTION);
            mongoCollection.insertOne(documentOption.get())
                    .subscribe(new InsertSubscriber(insertId, getContext().getSelf()));
        }
    }

    protected Behavior<DevsObservationMessage> onRun(Run run) {
        String insertId = run._id();
        Optional<Document> documentOption = toDocument(run);
        if (documentOption.isPresent()) {
            pendingInserts.put(insertId, run);
            String collectionName = "runs";
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collectionName);

            // Create indices if this is the first time we see the runs collection
            if (!initializedCollections.contains(collectionName)) {
                mongoCollection.createIndex(new Document("status", 1))
                        .subscribe(new IndexSubscriber(collectionName, getContext().getLog()));
                initializedCollections.add(collectionName);
            }

            mongoCollection.insertOne(documentOption.get())
                    .subscribe(new InsertSubscriber(insertId, getContext().getSelf()));
        }
        return Behaviors.same();
    }

    protected Behavior<DevsObservationMessage> onBranch(Branch branch) {
        String insertId = branch._id();
        Optional<Document> documentOption = toDocument(branch);
        if (documentOption.isPresent()) {
            pendingInserts.put(insertId, branch);
            String collectionName = "branches";
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection(collectionName);

            // Create indices if this is the first time we see the branches collection
            if (!initializedCollections.contains(collectionName)) {
                mongoCollection.createIndex(new Document("runId", 1))
                        .subscribe(new IndexSubscriber(collectionName, getContext().getLog()));
                mongoCollection.createIndex(new Document("parentBranchId", 1))
                        .subscribe(new IndexSubscriber(collectionName, getContext().getLog()));
                initializedCollections.add(collectionName);
            }

            mongoCollection.insertOne(documentOption.get())
                    .subscribe(new InsertSubscriber(insertId, getContext().getSelf()));
        }
        return Behaviors.same();
    }

    protected Optional<Document> toDocument(DevsObservationMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            Document doc = Document.parse(json);
            return Optional.of(doc);
        } catch (JsonProcessingException e) {
            getContext().getLog().warn("Error creating BSON Document from message", e);
        }
        return Optional.empty();
    }

    protected Behavior<DevsObservationMessage> onInsertResult(InsertResult insertResult) {
        if (insertResult instanceof InsertSuccessResult insertSuccessResult) {
            DevsObservationMessage msg = pendingInserts.remove(insertSuccessResult.insertId());
            getContext().getLog().debug("Inserted {}", msg.getClass().getSimpleName());
        } else if (insertResult instanceof InsertErrorResult insertErrorResult) {
            DevsObservationMessage msg = pendingInserts.remove(insertErrorResult.insertId());
            try {
                getContext().getLog().error("Error {} inserting document {}", insertErrorResult.error(),
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(msg));
            } catch (JsonProcessingException e) {
                getContext().getLog().error("Error {} inserting document", insertErrorResult.error());
            }
        }
        return Behaviors.same();
    }

    protected Behavior<DevsObservationMessage> onStopLogger(StopLogger stopLogger) {
        if (pendingInserts.isEmpty()) {
            getContext().getLog().info("MongoObservationActor stopped");
            this.mongoClient.close();
            return Behaviors.stopped();
        } else {
            timers.startSingleTimer(stopLogger, Duration.ofSeconds(1));
            getContext().getLog().info("MongoObservationActor waiting for {} pending inserts", pendingInserts.size());
            return Behaviors.same();
        }
    }

    /**
     * Internal result marker for asynchronous MongoDB insert operations.
     */
    protected interface InsertResult extends DevsObservationMessage {}

    /**
     * Reports that a pending insert completed successfully.
     *
     * @param insertId identifier of the inserted message
     */
    protected record InsertSuccessResult(String insertId) implements InsertResult {}

    /**
     * Reports that a pending insert failed.
     *
     * @param insertId identifier of the failed message
     * @param error backend error description
     */
    protected record InsertErrorResult(String insertId, String error) implements InsertResult {}

    /**
     * Triggers loading the observation type catalog after collection discovery completes.
     */
    protected record LoadObservationTypes() implements DevsObservationMessage {}

    /**
     * Carries the set of observation types discovered during actor startup.
     *
     * @param existingTypes observation types already present in the catalog collection
     */
    protected record InitializationResult(Set<String> existingTypes) implements DevsObservationMessage {}

    /**
     * Subscriber that checks whether the observation type catalog collection already exists.
     */
    protected static class CollectionExistsSubscriber implements Subscriber<String> {
        private final ActorRef<DevsObservationMessage> self;
        private final Logger log;
        private boolean collectionExists;

        /**
         * Creates a collection-discovery subscriber for actor startup.
         *
         * @param self actor that should receive the next initialization step
         * @param log logger used for warning messages
         */
        public CollectionExistsSubscriber(ActorRef<DevsObservationMessage> self, Logger log) {
            this.self = self;
            this.log = log;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String collectionName) {
            if (OBSERVATION_TYPES_COLLECTION.equals(collectionName)) {
                collectionExists = true;
            }
        }

        @Override
        public void onError(Throwable t) {
            log.warn("Error listing MongoDB collections during observation actor initialization; continuing with empty observation types", t);
            self.tell(new InitializationResult(Set.of()));
        }

        @Override
        public void onComplete() {
            if (collectionExists) {
                self.tell(new LoadObservationTypes());
            } else {
                self.tell(new InitializationResult(Set.of()));
            }
        }
    }

    /**
     * Subscriber that gathers known observation types before the actor registers itself.
     */
    protected static class InitializationSubscriber implements Subscriber<Document> {
        private final ActorRef<DevsObservationMessage> self;
        private final Set<String> types = new HashSet<>();

        /**
         * Creates a startup subscriber that reports results back to the actor.
         *
         * @param self actor that should receive the initialization result
         */
        public InitializationSubscriber(ActorRef<DevsObservationMessage> self) {
            this.self = self;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Document document) {
            String typeId = document.getString("_id");
            if (typeId != null) {
                types.add(typeId);
            }
        }

        @Override
        public void onError(Throwable t) {
            self.tell(new InitializationResult(types));
        }

        @Override
        public void onComplete() {
            self.tell(new InitializationResult(types));
        }
    }

    /**
     * Subscriber that logs index creation outcomes for a collection.
     */
    protected static class IndexSubscriber implements Subscriber<String> {
        protected final String collectionName;
        protected final Logger log;

        /**
         * Creates an index subscriber for one collection.
         *
         * @param collectionName collection whose index creation is being tracked
         * @param log logger used for progress and error reporting
         */
        public IndexSubscriber(String collectionName, Logger log) {
            this.collectionName = collectionName;
            this.log = log;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
        }

        @Override
        public void onNext(String indexName) {
            log.debug("Created index {} on collection {}", indexName, collectionName);
        }

        @Override
        public void onError(Throwable t) {
            log.error("Error creating index on collection " + collectionName, t);
        }

        @Override
        public void onComplete() {
        }
    }

    /**
     * Subscriber that converts Mongo insert callbacks into actor messages.
     */
    protected static class InsertSubscriber implements Subscriber<InsertOneResult> {
        protected final String insertId;
        protected final ActorRef<DevsObservationMessage> self;

        /**
         * Creates an insert subscriber for one pending write.
         *
         * @param insertId identifier of the message being inserted
         * @param self actor that should receive completion notifications
         */
        public InsertSubscriber(String insertId, ActorRef<DevsObservationMessage> self) {
            this.insertId = insertId;
            this.self = self;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(1);
        }

        @Override
        public void onNext(InsertOneResult t) {
            self.tell(new InsertSuccessResult(insertId));
        }

        @Override
        public void onError(Throwable t) {
            self.tell(new InsertErrorResult(insertId, t.getMessage()));
        }

        @Override
        public void onComplete() {
        }
    }
}
