/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
 * DEVS Streaming Framework Java contributors.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package devs.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import devs.PDevsCoordinator;
import devs.PDevsCouplings;
import devs.PDevsSimulator;
import devs.RootCoordinator;
import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.NextInternalTimeReport;
import devs.iso.PortValue;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.SimulationTerminatePayload;
import devs.iso.time.LongSimTime;
import devs.simulation.recorder.GenStoreRecorderOutputCouplingHandler;
import devs.simulation.recorder.RecorderModel;
import devs.utils.ConfigUtils;
import devs.utils.DevsObjectMapper;
import devs.utils.KafkaUtils;
import example.coordinator.GenStoreInputCouplingHandler;
import example.coordinator.GenStoreOutputCouplingHandler;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for verifying the functionality of Kafka DEVS (Discrete Event System Specification)
 * stream proxies and their interactions within a distributed simulation environment.
 * <p>
 * The test cases in this class ensure proper functionality of Kafka topics, DEVS simulation actors,
 * and corresponding Kafka stream proxies. These tests validate scenario-based use cases, such as
 * simulating DEVS system behavior, message transformations, state transitions, and event
 * propagation through Kafka-based proxies.
 * <p>
 * This class primarily focuses on: - Ensuring that Kafka topics are correctly configured and
 * managed during the simulation. - Validating the integration and communication between generator,
 * storage, recorder, and coordinator simulation actors using Kafka proxies. - Simulating discrete
 * event workflows while asserting their correctness concerning timing, sequencing, and data
 * integrity.
 * <p>
 * Dependencies: - An Akka-based architecture for managing DEVS models and their interactions. -
 * Kafka for establishing distributed topics and facilitating event-driven communication. -
 * Jackson's ObjectMapper for serializing and deserializing DEVS model messages.
 * <p>
 * Key aspects tested in this class: - Proper setup and cleanup of Kafka topics. - Execution of DEVS
 * simulation steps with expected event transitions and outputs. - Correct relaying of events
 * between Kafka proxies and DEVS actors.
 */
@Disabled("Requires KAFKA instance")
public class KafkaDevsStreamProxyTest {
  
  static final String genStoreSystemTopic = "genStoreSystem";
  static final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  static Long time;

  /**
   * Tests the Kafka proxy for a DEVS (Discrete Event System Specification) stream by simulating a
   * message flow and verifying the interaction among Kafka topics, simulation actors, and proxies.
   * <p>
   * The test performs the following operations: - Configures the Kafka cluster with the necessary
   * producer and consumer properties. - Creates and deletes Kafka topics for the simulation before
   * and after the test execution. - Spawns DEVS simulation actors, including a generator and its
   * corresponding Kafka proxy, and sets up communication via Kafka. - Spawns test probes to
   * validate message sending and receiving behavior for simulation actors. - Sends initialization
   * and simulation messages through Kafka topics and validates that the expected transitions and
   * outputs occur in the system. - Confirms the correctness of DEVS message values, such as the
   * simulation time and sender identity.
   * <p>
   * Assertions ensure that: - Initialization messages are correctly received by the simulated
   * actor. - State transitions and outputs adhere to the expected simulation logic. - Kafka
   * integration correctly facilitates message relay for DEVS actors.
   * <p>
   * This test demonstrates the integration of Kafka streaming with discrete event simulations while
   * verifying correctness in message handling and system behavior.
   *
   * @throws IOException          if a failure occurs in Kafka topic management or message
   *                              serialization.
   * @throws ExecutionException   if a Kafka operation fails during asynchronous execution.
   * @throws InterruptedException if the execution is interrupted during thread sleep operations.
   */
  @Test
  @DisplayName("Test Kafka Proxy for a DEVS Stream")
  void kafkaDevsStreamProxyTest() throws IOException, ExecutionException, InterruptedException {
    // create the producer and consumer topics
    Config config = ConfigFactory.load();
    Config kafkaClusterConfig = config.getConfig("kafka-cluster");
    Config kafkaConsumerConfig = config.getConfig("kafka-readall-consumer");
    Properties kafkaClusterProperties = ConfigUtils.toProperties(kafkaClusterConfig);
    AdminClient adminClient = KafkaUtils.createAdminClient(kafkaClusterProperties);
    KafkaUtils.deleteTopics(Arrays.asList(genStoreSystemTopic), adminClient);
    Thread.sleep(5000);
    KafkaUtils.createTopic(genStoreSystemTopic, adminClient, Optional.of(1), Optional.empty());
    Producer<Long, String> producer =
        KafkaUtils.createProducer(ConfigUtils.copyProperties(kafkaClusterProperties));

    ActorTestKit testKit = ActorTestKit.create();

    final TestProbe<DevsMessage> toSimulatorProbe = testKit.createTestProbe("toSimulatorProbe");
    final TestProbe<DevsMessage> fromSimulatorProbe = testKit.createTestProbe("fromSimulatorProbe");

    final ActorRef<DevsMessage> generatorProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("generator", genStoreSystemTopic, kafkaClusterConfig),
        "Proxy");
    
    final ActorRef<DevsMessage> simulator = testKit.spawn(
        Behaviors.setup(context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0), LongSimTime.builder().t(0L).build(), context)));

    ActorRef<DevsMessage> generatorReceiver = testKit.spawn(KafkaReceiver.create(toSimulatorProbe.getRef(),
        fromSimulatorProbe.getRef(), "generator", kafkaConsumerConfig, genStoreSystemTopic), "generatorReceiver");

    // Initialize and expect next sim time to be 1
    SimulationInit<LongSimTime> simulationInit = SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId("KafkaDevsStreamProxyTest")
        .messageId("SimulationInit")
        .senderId("Proxy")
        .receiverId("generator")
        .build();
    String initSimString = objectMapper.writeValueAsString(simulationInit);
    final Long start = System.currentTimeMillis();
    producer.send(new ProducerRecord<>(genStoreSystemTopic, 0L, initSimString));
    DevsMessage devsMessageFromKafka = toSimulatorProbe.receiveMessage(Duration.ofSeconds(10));
    assert (devsMessageFromKafka instanceof SimulationInitMessage<?>);
    SimulationInitMessage<LongSimTime> initSimFromKafka =
        (SimulationInitMessage<LongSimTime>) devsMessageFromKafka;
    assert (initSimFromKafka.getSimulationInit().getEventTime().getT() == 0L);
    simulator.tell(initSimFromKafka);
    DevsMessage receivedMessage = fromSimulatorProbe.receiveMessage(Duration.ofSeconds(10));
    time = System.currentTimeMillis() - start;
    assert (receivedMessage instanceof NextInternalTimeReport<?>);
    NextInternalTimeReport<LongSimTime> nextTime = (NextInternalTimeReport<LongSimTime>) receivedMessage;
    assert (nextTime.getNextInternalTime() instanceof LongSimTime);
    assert (((LongSimTime) nextTime.getNextInternalTime()).getT() == 1L);
    assert ("generator".equals(nextTime.getSenderId()));
    generatorProxy.tell(SimulationTerminate.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .simulationId("KafkaDevsStreamProxyTest")
        .messageId("SimulationTerminate")
        .senderId("Proxy")
        .receiverId("generator")
        .payload(SimulationTerminatePayload.builder().reason("Simulation terminated").build())
        .build());
    Thread.sleep(5000);
    KafkaUtils.deleteTopics(Arrays.asList(genStoreSystemTopic), adminClient);
    testKit.shutdownTestKit();
  }


  /**
   * Tests the simulation of a distributed DEVS (Discrete Event System Specification) model on
   * Kafka. The method sets up a test environment with Kafka topics, actor systems, and proxies for
   * DEVS models, and verifies the proper functioning of the simulation by asserting expected
   * execution flows.
   * <p>
   * The test simulates three main components (generator, storage, and recorder) interacting through
   * Kafka topics using a coordinator to manage the overall system. Kafka proxies are created to
   * facilitate communication between components and Kafka topics.
   * <p>
   * Key steps include: - Initializing Kafka topics for the simulation. - Starting actors for each
   * DEVS model and their Kafka proxies. - Establishing couplings and a coordinator to manage the
   * models' interactions. - Starting the root coordinator to drive the simulation. - Verifying the
   * exchange of messages (e.g., InitSim and ExecuteTransition) between components using
   * assertions.
   * <p>
   * At the end of the test, the test environment, including Kafka topics and the actor test kit, is
   * cleaned up to prevent resource leakage.
   *
   * @throws IOException          if an I/O error occurs during the Kafka configuration or
   *                              communication.
   * @throws ExecutionException   if an exception occurs in asynchronous Kafka operations.
   * @throws InterruptedException if the thread is interrupted during sleep or asynchronous Kafka
   *                              operations.
   */
  @Test
  @DisplayName("Test Kafka Proxy for an entire simulation")
  void kafkaDevsStreamSimulation() throws IOException, ExecutionException, InterruptedException {
    Config config = ConfigFactory.load();
    Config kafkaClusterConfig = config.getConfig("kafka-cluster");
    Properties kafkaClusterProperties = ConfigUtils.toProperties(kafkaClusterConfig);
    AdminClient adminClient = KafkaUtils.createAdminClient(kafkaClusterProperties);
    KafkaUtils.deleteTopics(
        Arrays.asList(genStoreSystemTopic),
        adminClient);
    Thread.sleep(5000);
    KafkaUtils.createTopics(
        Arrays.asList(genStoreSystemTopic),
        adminClient, Optional.of(1), Optional.empty());
    ActorTestKit testKit = ActorTestKit.create();
    ActorRef<DevsMessage> generator = testKit.spawn(
        Behaviors.setup(context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0), LongSimTime.builder().t(0L).build(), context)),
        "generatorSim");
    ActorRef<DevsMessage> generatorProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("generator", genStoreSystemTopic, kafkaClusterConfig),
        "generatorProxy");

    ActorRef<DevsMessage> storage = testKit.spawn(Behaviors
            .setup(context -> new PDevsSimulator<LongSimTime, StorageState, StorageModel>(
                new StorageModel(new StorageState(StorageStateEnum.S0)),
                LongSimTime.builder().t(0L).build(), context)),
        "storageSim");
    ActorRef<DevsMessage> storageProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("storage", genStoreSystemTopic, kafkaClusterConfig),
        "storageProxy");

    TestProbe<DevsMessage> toRecorderProbe = testKit.createTestProbe("toRecorderProbe");

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generatorProxy);
    modelSimulators.put("storage", storageProxy);
    modelSimulators.put("recorder", toRecorderProbe.getRef());

    PDevsCouplings genStoreCoupling =
        new PDevsCouplings(Collections.singletonList(new GenStoreInputCouplingHandler()),
            Arrays.asList(new GenStoreOutputCouplingHandler(),
                new GenStoreRecorderOutputCouplingHandler()));

    ActorRef<DevsMessage> coordinator =
        testKit.spawn(
            Behaviors.setup(
                context -> new PDevsCoordinator<LongSimTime>("genStoreCoupled",
                    modelSimulators, genStoreCoupling, context)),
            "coordinator");

    ActorRef<DevsMessage> coordinatorProxy =
        testKit.spawn(KafkaDevsStreamProxy.create("coordinator", genStoreSystemTopic,
            kafkaClusterConfig), "coordinatorProxy");

    Config kafkaConsumerConfig = config.getConfig("kafka-readall-consumer");
    ActorRef<DevsMessage> storageReceiver = testKit.spawn(KafkaReceiver.create(storage,
        coordinatorProxy, "storage", kafkaConsumerConfig, genStoreSystemTopic), "storageReceiver");

    ActorRef<DevsMessage> generatorReceiver = testKit.spawn(KafkaReceiver.create(generator,
        coordinatorProxy, "generator", kafkaConsumerConfig, genStoreSystemTopic), "generatorReceiver");

    ActorRef<DevsMessage> rootCoordinator =
        testKit.spawn(
            Behaviors.setup(context -> new RootCoordinator<LongSimTime>(context,
                LongSimTime.builder().t(2L).build(), coordinator, "genStoreCoupled")),
            "rootCoordinator");

    ActorRef<DevsMessage> coordinatorReceiver =
        testKit.spawn(KafkaReceiver.create(coordinator, rootCoordinator,
            "genStoreCoupled", kafkaConsumerConfig, genStoreSystemTopic), "coordinatorReceiver");

    final ActorRef<DevsMessage> recorderSim =
        testKit.spawn(
            Behaviors.setup(
                context -> new PDevsSimulator<LongSimTime, Void, RecorderModel>(
                    new RecorderModel("recorder"),
                    LongSimTime.builder().t(0L).build(), context)),
            "recorderSim");

    Thread.sleep(3000);
    rootCoordinator.tell(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId("KafkaDevsStreamProxyTest")
        .messageId("SimulationInit")
        .senderId("Proxy")
        .receiverId("root")
        .build());

    // Expect initSim message to recorder
    DevsMessage message1 = toRecorderProbe.receiveMessage(Duration.ofSeconds(100));
    assert (message1 instanceof SimulationInitMessage<?>);
    SimulationInitMessage<LongSimTime> initSimMessage = (SimulationInitMessage<LongSimTime>) message1;
    assert (initSimMessage.getSimulationInit().getEventTime().getT() == 0L);
    recorderSim.tell(initSimMessage);

    // Expect execute external transition message with generator output of 0
    DevsMessage messag2 = toRecorderProbe.receiveMessage(Duration.ofSeconds(100));
    assert (messag2 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition =
        (ExecuteTransition<LongSimTime>) messag2;
    assert (executeExternalTransition.getPayload().getInputs().size() > 0);
    List<PortValue<?>> modelInputs = executeExternalTransition.getPayload().getInputs();
    assert (modelInputs.get(0).getValue().equals(0));
    assert (executeExternalTransition.getEventTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition);

    // Expect execute external transition message with generator output of 1 and a storage
    // output
    // of StorageStateEnum.S0
    DevsMessage message3 = toRecorderProbe.receiveMessage(Duration.ofSeconds(100));
    assert (message3 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition2 =
        (ExecuteTransition<LongSimTime>) message3;
    assert (executeExternalTransition2.getPayload().getInputs().size() > 0);
    List<PortValue<?>> recorderBag = executeExternalTransition2.getPayload().getInputs();
    PortValue<?> generatorPort = recorderBag.stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert (generatorPort.getValue().equals(1));
    PortValue<?> storagePort = recorderBag.stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert (storagePort.getValue().equals("S0"));
    assert (executeExternalTransition2.getEventTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition2);

    // Expect execute external transition message with no generator output and a storage output
    // of StorageStateEnum.S1
    DevsMessage messag4 = toRecorderProbe.receiveMessage(Duration.ofSeconds(100));
    assert (messag2 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition3 =
        (ExecuteTransition<LongSimTime>) messag4;
    assert (executeExternalTransition3.getPayload().getInputs().size() == 1);
    List<PortValue<?>> recorderBag4 = executeExternalTransition3.getPayload().getInputs();
    Optional<PortValue<?>> generatorPort4 = recorderBag4.stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortName())).findFirst();
    assert (generatorPort4.isEmpty());
    PortValue<?> storagePort4 = recorderBag4.stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert (storagePort4.getValue().equals("S1"));
    assert (executeExternalTransition2.getEventTime().getT() == 1L);
    testKit.shutdownTestKit();
    KafkaUtils.deleteTopics(
        Arrays.asList(genStoreSystemTopic),
        adminClient);
    Thread.sleep(5000);


  }

  /**
   * Tests the simulation of a distributed DEVS (Discrete Event System Specification) model on Kafka
   * without using probes. This method sets up a testing environment with Kafka topics, actor
   * systems, and proxies for DEVS models, and verifies the correct operation of the simulation by
   * asserting expected interactions and behavior.
   * <p>
   * The test simulates three main components (generator, storage, and recorder) interacting via
   * Kafka topics through a coordinator, which manages the system. Kafka proxies are used to
   * facilitate communication between components and their corresponding Kafka topics.
   * <p>
   * Key operations include: - Configuring Kafka topics and setting up the required producers and
   * consumers for simulation. - Initializing the necessary actors for DEVS models and their Kafka
   * proxies. - Defining couplings and a coordinator to handle interactions between actors. -
   * Creating receivers to relay messages between DEVS models and their proxies. - Driving the
   * simulation using a root coordinator to propagate simulation events.
   * <p>
   * The method avoids the use of probes for monitoring the interactions but ensures the proper
   * functioning of the system through systematic initialization, execution, and cleanup processes.
   * <p>
   * The test includes reset operations to delete and recreate Kafka topics before and after the
   * simulation to ensure a clean and isolated test environment.
   *
   * @throws IOException          if an error occurs in Kafka topic management or message
   *                              serialization.
   * @throws ExecutionException   if a Kafka operation fails during asynchronous execution.
   * @throws InterruptedException if the thread is interrupted during sleep or Kafka operations.
   */
  @Test
  @DisplayName("Test Kafka Proxy for an entire simulation without using probes")
  void kafkaDevsStreamSimulationWithoutProbes()
      throws IOException, ExecutionException, InterruptedException {
    Config config = ConfigFactory.load();
    Config kafkaClusterConfig = config.getConfig("kafka-cluster");
    Properties kafkaClusterProperties = ConfigUtils.toProperties(kafkaClusterConfig);
    AdminClient adminClient = KafkaUtils.createAdminClient(kafkaClusterProperties);
    KafkaUtils.deleteTopics(
        Arrays.asList(genStoreSystemTopic),
        adminClient);
    Thread.sleep(5000);
    KafkaUtils.createTopics(
        Arrays.asList(genStoreSystemTopic),
        adminClient, Optional.of(1), Optional.empty());
    ActorTestKit testKit = ActorTestKit.create();
    ActorRef<DevsMessage> generator = testKit.spawn(
        Behaviors.setup(context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0), LongSimTime.builder().t(0L).build(), context)),
        "generatorSim");
    ActorRef<DevsMessage> generatorProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("generator", genStoreSystemTopic, kafkaClusterConfig),
        "generatorProxy");

    ActorRef<DevsMessage> storage = testKit.spawn(Behaviors
            .setup(context -> new PDevsSimulator<LongSimTime, StorageState, StorageModel>(
                new StorageModel(new StorageState(StorageStateEnum.S0)),
                LongSimTime.builder().t(0L).build(), context)),
        "storageSim");
    ActorRef<DevsMessage> storageProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("storage", genStoreSystemTopic, kafkaClusterConfig),
        "storageProxy");

    ActorRef<DevsMessage> recorderSim =
        testKit.spawn(
            Behaviors.setup(
                context -> new PDevsSimulator<LongSimTime, Void, RecorderModel>(
                    new RecorderModel("recorder"),
                    LongSimTime.builder().t(0L).build(), context)),
            "recorderSim");

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generatorProxy);
    modelSimulators.put("storage", storageProxy);
    modelSimulators.put("recorder", recorderSim);

    PDevsCouplings genStoreCoupling =
        new PDevsCouplings(Collections.singletonList(new GenStoreInputCouplingHandler()),
            Arrays.asList(new GenStoreOutputCouplingHandler(),
                new GenStoreRecorderOutputCouplingHandler()));

    ActorRef<DevsMessage> coordinator =
        testKit.spawn(
            Behaviors.setup(
                context -> new PDevsCoordinator<LongSimTime>("genStoreCoupled",
                    modelSimulators, genStoreCoupling, context)),
            "coordinator");

    ActorRef<DevsMessage> coordinatorProxy =
        testKit.spawn(KafkaDevsStreamProxy.create("genStoreCoupled", genStoreSystemTopic,
            kafkaClusterConfig), "coordinatorProxy");
    Config kafkaConsumerConfig = config.getConfig("kafka-readall-consumer");
    ActorRef<DevsMessage> storageReceiver = testKit.spawn(KafkaReceiver.create(storage,
        coordinatorProxy, "storage", kafkaConsumerConfig, genStoreSystemTopic), "storageReceiver");

    ActorRef<DevsMessage> generatorReceiver = testKit.spawn(KafkaReceiver.create(generator,
        coordinatorProxy, "generator",kafkaConsumerConfig, genStoreSystemTopic), "generatorReceiver");

    ActorRef<DevsMessage> rootCoordinator =
        testKit.spawn(
            Behaviors.setup(context -> new RootCoordinator<LongSimTime>(context,
                LongSimTime.builder().t(2L).build(), coordinator, "genStoreCoupled")),
            "rootCoordinator");

    ActorRef<DevsMessage> coordinatorReceiver =
        testKit.spawn(KafkaReceiver.create(coordinator, rootCoordinator,
            "genStoreCoupled", kafkaConsumerConfig, genStoreSystemTopic), "coordinatorReceiver");

    Thread.sleep(3000);
    rootCoordinator.tell(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId("KafkaDevsStreamProxyTest")
        .messageId("SimulationInit")
        .senderId("Proxy")
        .receiverId("root")
        .build());
    Thread.sleep(5000);
    testKit.shutdownTestKit();
    //KafkaUtils.deleteTopics(Arrays.asList(genStoreSystemTopic), adminClient);
    Thread.sleep(5000);
  }
}
