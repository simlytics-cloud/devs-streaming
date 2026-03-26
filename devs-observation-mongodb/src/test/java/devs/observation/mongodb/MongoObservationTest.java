package devs.observation.mongodb;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import devs.PDevsCoordinator;
import devs.PDevsCouplings;
import devs.PDevsSimulator;
import devs.RootCoordinator;
import devs.couplings.SourceMappingResolver;
import devs.iso.DevsMessage;
import devs.iso.SimulationInit;
import devs.iso.time.LongSimTime;
import devs.msg.Branch;
import devs.msg.Run;
import devs.observation.DevsObservationMessage;
import devs.observation.Observation;
import devs.observation.StopLogger;
import example.generator.GeneratorModel;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@DisplayName("MongoDB Observation Test")
public class MongoObservationTest {

    private static final ActorTestKit testKit = ActorTestKit.create();
    private static final String MONGO_CONN = "mongodb://localhost:27017/";
    private static final String TEST_DB = "test_db_" + UUID.randomUUID().toString().replace("-", "");
    private static MongoClient mongoClient;
    private static MongoDatabase database;
    private static final boolean dropping = true;  // Set to false if you want to inspect the database after the test

    @BeforeAll
    public static void setup() {
        mongoClient = MongoClients.create(MONGO_CONN);
        database = mongoClient.getDatabase(TEST_DB);
    }

    @AfterAll
    public static void cleanup() {
        // Drop database
        if (dropping) {
            CountDownLatch latch = new CountDownLatch(1);
            database.drop().subscribe(new Subscriber<Void>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.request(1);
                }

                @Override
                public void onNext(Void aVoid) {
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
        mongoClient.close();
        testKit.shutdownTestKit();
    }

    @Test
    public void testMongoObservation() throws InterruptedException {
        String runId = UUID.randomUUID().toString();
        String branchId = UUID.randomUUID().toString();

        ActorRef<DevsObservationMessage> mongoActor = testKit.spawn(MongoObservationActor.create(MONGO_CONN, TEST_DB));

        // Wait for the actor to initialize and register with the receptionist
        testKit.createTestProbe().awaitAssert(() -> {
            org.apache.pekko.actor.testkit.typed.javadsl.TestProbe<Receptionist.Listing> probe = testKit.createTestProbe(Receptionist.Listing.class);
            testKit.system().receptionist().tell(Receptionist.find(MongoObservationActor.MONGO_OBSERVATION_KEY, probe.getRef()));
            Receptionist.Listing listing = probe.receiveMessage();
            Assertions.assertFalse(listing.getServiceInstances(MongoObservationActor.MONGO_OBSERVATION_KEY).isEmpty(), "Actor not registered yet");
            return null;
        });

        // 1. Initialize Run and Branch
        Run run = Run.builder()
                ._id(runId)
                .name("MongoObservationTestRun")
                .startTime(Instant.now())
                .status(Run.RunStatus.RUNNING)
                .build();
        mongoActor.tell(run);

        Branch branch = Branch.builder()
                ._id(branchId)
                .runId(runId)
                .forkTime(0.0)
                .build();
        mongoActor.tell(branch);

        // 2. Setup Simulation
        ActorRef<DevsMessage> generatorSim = testKit.spawn(PDevsSimulator.create(
                new GeneratorModel(0, "generator"), LongSimTime.create(0L)), "generator");

        ActorRef<DevsMessage> storageSim = testKit.spawn(PDevsSimulator.create(
                new StorageModel(new StorageState(StorageStateEnum.S0), "storage"),
                LongSimTime.create(0L)), "storage");

        ActorRef<DevsMessage> observerSim = testKit.spawn(PDevsSimulator.create(
                new MongoObserverModel("observer", runId, branchId),
                LongSimTime.create(0L)), "observer");

        Map<String, ActorRef<DevsMessage>> modelSimulators = Map.of(
                "generator", generatorSim,
                "storage", storageSim,
                "observer", observerSim
        );

        PDevsCouplings couplings = PDevsCouplings.builder("mongoTestCoupled")
                .addConnection("generator", "OUTPUT", "storage", "INPUT")
                .addResolver("generator", "OUTPUT", new SourceMappingResolver("observer", "OUTPUT"))
                .addResolver("storage", "OUTPUT", new SourceMappingResolver("observer", "OUTPUT"))
                .build();

        ActorRef<DevsMessage> coordinator = testKit.spawn(PDevsCoordinator.create("mongoTestCoupled",
                modelSimulators, couplings), "coordinator");

        ActorRef<DevsMessage> rootCoordinator =
                testKit.spawn(RootCoordinator.create(LongSimTime.create(2L), coordinator, "mongoTestCoupled"), "root");

        // 3. Run Simulation
        rootCoordinator.tell(SimulationInit.<LongSimTime>builder()
                .eventTime(LongSimTime.create(0L))
                .simulationId("MongoObservationTest")
                .messageId("SimulationInit")
                .senderId("TestActor")
                .receiverId("root")
                .build());

        // Wait for simulation to finish (logical time 2)
        testKit.createTestProbe().expectTerminated(rootCoordinator, java.time.Duration.ofSeconds(10));

        // 4. Stop Logger and Wait
        mongoActor.tell(StopLogger.builder().build());
        testKit.createTestProbe().expectTerminated(mongoActor, java.time.Duration.ofSeconds(10));

        // 5. Verify MongoDB Data
        verifyData("runs", 1);
        verifyData("branches", 1);
        // Generator outputs: 0 at t=0, 1 at t=1, 2 at t=2. (3 total)
        // Wait, why was it 4? Maybe it also outputs at t=3? No, limit 2.
        // Let's check the test log from before. It said:
        // expected: <3> but was: <4> for Integer.
        // And expected: <2> but was: <0> for StorageStateEnum.
        // StorageModel.outputFunction() returns modelState.getStateValue().name() which is a String.
        // So the collection name should be "String".
        verifyData("Integer", 4);
        verifyData("String", 4);
        verifyData(MongoObservationActor.OBSERVATION_TYPES_COLLECTION, 2);
    }

    @Test
    public void testMongoObservationReinitialization() throws InterruptedException {
        String runId = "run1";
        String branchId = "branch1";
        String type = "TestType";

        // 1. Spawn actor and send an observation
        ActorRef<DevsObservationMessage> mongoActor1 = testKit.spawn(MongoObservationActor.create(MONGO_CONN, TEST_DB));
        Observation<LongSimTime, String> obs = Observation.<LongSimTime, String>builder()
                ._id(UUID.randomUUID().toString())
                .runId(runId)
                .branchId(branchId)
                .time(LongSimTime.create(0L))
                .producerModel("model1")
                .observationType(type)
                .payload("data")
                .build();
        mongoActor1.tell(obs);

        // Wait for it to be processed
        mongoActor1.tell(StopLogger.builder().build());
        testKit.createTestProbe().expectTerminated(mongoActor1, java.time.Duration.ofSeconds(10));

        // Verify it was written
        verifyData(MongoObservationActor.OBSERVATION_TYPES_COLLECTION, 3); // 2 from previous test + 1 new

        // 2. Spawn a new actor, it should load the type from DB
        ActorRef<DevsObservationMessage> mongoActor2 = testKit.spawn(MongoObservationActor.create(MONGO_CONN, TEST_DB));

        // Send same observation again. It should NOT try to re-insert the type (which would cause a duplicate key error if not handled)
        mongoActor2.tell(obs);
        mongoActor2.tell(StopLogger.builder().build());
        testKit.createTestProbe().expectTerminated(mongoActor2, java.time.Duration.ofSeconds(10));

        // Still should be 3 entries (no duplicates added)
        verifyData(MongoObservationActor.OBSERVATION_TYPES_COLLECTION, 3);
    }

    private void verifyData(String collectionName, int expectedCount) throws InterruptedException {
        List<Document> documents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        database.getCollection(collectionName).find().subscribe(new Subscriber<Document>() {
            @Override public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(Document document) { documents.add(document); }
            @Override public void onError(Throwable t) { latch.countDown(); }
            @Override public void onComplete() { latch.countDown(); }
        });
        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS), "Timeout waiting for " + collectionName);
        Assertions.assertEquals(expectedCount, documents.size(), "Count mismatch for " + collectionName);
        for (Document doc : documents) {
            Assertions.assertTrue(doc.containsKey("_id"), "Missing _id in " + collectionName);
            Assertions.assertFalse(doc.containsKey("id"), "Found redundant id field in " + collectionName);
        }
    }
}
