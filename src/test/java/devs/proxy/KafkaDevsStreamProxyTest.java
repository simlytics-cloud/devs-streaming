/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import devs.PDevsCoordinator;
import devs.PDevsCouplings;
import devs.PDevsSimulator;
import devs.RootCoordinator;
import devs.msg.*;
import devs.msg.time.LongSimTime;
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
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Disabled("Requires KAFKA instance")
public class KafkaDevsStreamProxyTest {

  static final ActorTestKit testKit = ActorTestKit.create();
  static final String generatorInputTopic = "generatorInput";
  static final String coordinatorInputTopic = "coordinatorInput";
  static final String storageInputTopic = "storageInput";
  static final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  static Long time;

  @BeforeAll
  public static void initialize() {
    objectMapper.registerModule(new Jdk8Module());
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
    if (time != null) {
      System.out.println("Simulation took " + time + " milliseconds.");
    }
  }

  @Test
  @DisplayName("Test Kafka Proxy for a DEVS Stream")
  void kafkaDevsStreamProxyTest() throws IOException, ExecutionException, InterruptedException {
    // create the producer and consumer topics
    Config config = ConfigFactory.load();
    Config kafkaClusterConfig = config.getConfig("kafka-cluster");
    Config kafkaConsumerConfig = config.getConfig("kafka-readall-consumer");
    Properties kafkaClusterProperties = ConfigUtils.toProperties(kafkaClusterConfig);
    AdminClient adminClient = KafkaUtils.createAdminClient(kafkaClusterProperties);
    KafkaUtils.deleteTopics(Arrays.asList(generatorInputTopic), adminClient);
    Thread.sleep(5000);
    KafkaUtils.createTopic(generatorInputTopic, adminClient, Optional.of(1), Optional.empty());
    Producer<Long, String> producer = KafkaUtils.createProducer(ConfigUtils.copyProperties(kafkaClusterProperties));


    TestProbe<DevsMessage> toSimulatorProbe = testKit.createTestProbe("toSimulatorProbe");
    TestProbe<DevsMessage> fromSimulatorProbe = testKit.createTestProbe("fromSimulatorProbe");
    ActorRef<DevsMessage> simulator = testKit.spawn(Behaviors.setup(context ->
        new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0),
            LongSimTime.builder().t(0L).build(),
            context
        )));

    ActorRef<DevsMessage> generatorProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("generator",
            generatorInputTopic, kafkaClusterConfig), "Proxy");

    ActorRef<DevsMessage> generatorReceiver = testKit.spawn(KafkaReceiver.create(
        toSimulatorProbe.getRef(), fromSimulatorProbe.getRef(), kafkaConsumerConfig,
        generatorInputTopic));

    // Initialize and expect next sim time to be 1
    InitSim initSim = InitSim.builder().time(LongSimTime.builder().t(0L).build()).build();
    String initSimString = objectMapper.writeValueAsString(initSim);
    Long start = System.currentTimeMillis();
    producer.send(new ProducerRecord<>(generatorInputTopic, 0L, initSimString));
    DevsMessage devsMessageFromKafka = toSimulatorProbe.receiveMessage(Duration.ofSeconds(10));
    assert(devsMessageFromKafka instanceof InitSimMessage<?>);
    InitSimMessage<LongSimTime> initSimFromKafka = (InitSimMessage<LongSimTime>) devsMessageFromKafka;
    assert(initSimFromKafka.getInitSim().getTime().getT() == 0L);
    simulator.tell(initSimFromKafka);
    DevsMessage receivedMessage = fromSimulatorProbe.receiveMessage(Duration.ofSeconds(10));
    time = System.currentTimeMillis() - start;
    assert (receivedMessage instanceof NextTime<?>);
    NextTime nextTime = (NextTime) receivedMessage;
    assert (nextTime.getTime() instanceof LongSimTime);
    assert (((LongSimTime) nextTime.getTime()).getT() == 1L);
    assert ("generator".equals(nextTime.getSender()));
    generatorProxy.tell(SimulationDone.builder().time(LongSimTime.builder().t(1L).build()).build());
    Thread.sleep(5000);
    KafkaUtils.deleteTopics(Arrays.asList(generatorInputTopic), adminClient);

  }


  @Test
  @DisplayName("Test Kafka Proxy for an entire simulation")
  void kafkaDevsStreamSimulation() throws IOException, ExecutionException, InterruptedException {
    Config config = ConfigFactory.load();
    Config kafkaClusterConfig = config.getConfig("kafka-cluster");
    Config kafkaConsumerConfig = config.getConfig("kafka-readall-consumer");
    Properties kafkaClusterProperties = ConfigUtils.toProperties(kafkaClusterConfig);
    AdminClient adminClient = KafkaUtils.createAdminClient(kafkaClusterProperties);
    KafkaUtils.deleteTopics(Arrays.asList(generatorInputTopic, coordinatorInputTopic, storageInputTopic), adminClient);
    Thread.sleep(5000);
    KafkaUtils.createTopics(Arrays.asList(generatorInputTopic, coordinatorInputTopic, storageInputTopic), adminClient, Optional.of(1), Optional.empty());


    ActorRef<DevsMessage> generator = testKit.spawn(Behaviors.setup(context ->
        new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0),
            LongSimTime.builder().t(0L).build(),
            context
        )), "generatorSim");
    ActorRef<DevsMessage> generatorProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("generator", generatorInputTopic,
            kafkaClusterConfig), "generatorProxy");


    ActorRef<DevsMessage> storage = testKit.spawn(Behaviors.setup(context ->
        new PDevsSimulator<LongSimTime, StorageState, StorageModel>(
            new StorageModel(new StorageState(StorageStateEnum.S0)),
            LongSimTime.builder().t(0L).build(),
            context
        )), "storageSim");
    ActorRef<DevsMessage> storageProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("storage", storageInputTopic,
            kafkaClusterConfig), "storageProxy");


    TestProbe<DevsMessage> toRecorderProbe = testKit.createTestProbe("toRecorderProbe");

    ActorRef<DevsMessage> recorderSim = testKit.spawn(Behaviors.setup(context ->
        new PDevsSimulator<LongSimTime, Void, RecorderModel>(
            new RecorderModel("recorder"),
            LongSimTime.builder().t(0L).build(),
            context
        )), "recorderSim");

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generatorProxy);
    modelSimulators.put("storage", storageProxy);
    modelSimulators.put("recorder", toRecorderProbe.getRef());

    PDevsCouplings genStoreCoupling = new PDevsCouplings(
        Collections.singletonList(new GenStoreInputCouplingHandler()),
        Arrays.asList(new GenStoreOutputCouplingHandler(), new GenStoreRecorderOutputCouplingHandler()));


    ActorRef<DevsMessage> coordinator = testKit.spawn(Behaviors.setup(context ->
        new PDevsCoordinator<LongSimTime>(
            "genStoreCoupled",
            "root",
            modelSimulators,
            genStoreCoupling,
            context
        )
    ), "coordinator");

    ActorRef<DevsMessage> coordinatorProxy = testKit.spawn(
        KafkaDevsStreamProxy.create("coordinator", coordinatorInputTopic,
            kafkaClusterConfig), "coordinatorProxy");

    ActorRef<DevsMessage> storageReceiver = testKit.spawn(
        KafkaReceiver.create(storage, coordinatorProxy, kafkaConsumerConfig, storageInputTopic), "storageReceiver");

    ActorRef<DevsMessage> generatorReceiver = testKit.spawn(
        KafkaReceiver.create(generator, coordinatorProxy, kafkaConsumerConfig, generatorInputTopic), "generatorReceiver");


    ActorRef<DevsMessage> rootCoordinator = testKit.spawn(Behaviors.setup(context ->
        new RootCoordinator<LongSimTime>(context, LongSimTime.builder().t(2L).build(), coordinator)), "rootCoordinator");

    ActorRef<DevsMessage> coordinatorReceiver = testKit.spawn(
        KafkaReceiver.create(coordinator, rootCoordinator, kafkaConsumerConfig, coordinatorInputTopic), "coordinatorReceiver");

    Thread.sleep(3000);
    rootCoordinator.tell(InitSim.builder().time(LongSimTime.builder().t(0L).build()).build());

    // Expect initSim message to recorder
    DevsMessage message1 = toRecorderProbe.receiveMessage();
    assert (message1 instanceof InitSimMessage<?>);
    InitSimMessage<LongSimTime> initSimMessage = (InitSimMessage<LongSimTime>) message1;
    assert (initSimMessage.getInitSim().getTime().getT() == 0L);
    recorderSim.tell(initSimMessage);

    // Expect execute external transition message with generator output of 0
    DevsMessage messag2 = toRecorderProbe.receiveMessage(Duration.ofSeconds(100));
    assert (messag2 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition =
        (ExecuteTransition<LongSimTime>) messag2;
    assert (executeExternalTransition.getModelInputsOption().isPresent());
    Bag modelInputs = executeExternalTransition.getModelInputsOption().get();
    assert(modelInputs.getPortValueList().get(0).getValue().equals(0));
    assert (executeExternalTransition.getTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition);

    // Expect execute external transition message with generator output of 1 and a storage output
    // of StorageStateEnum.S0
    DevsMessage message3 = toRecorderProbe.receiveMessage(Duration.ofSeconds(100));
    assert (message3 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition2 =
        (ExecuteTransition<LongSimTime>) message3;
    assert (executeExternalTransition2.getModelInputsOption().isPresent());
    Bag recorderBag = executeExternalTransition2.getModelInputsOption().get();
    PortValue<?> generatorPort = recorderBag
        .getPortValueList().stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortIdentifier())).findFirst().get();
    assert (generatorPort.getValue().equals(1));
    PortValue<?> storagePort = recorderBag
        .getPortValueList().stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortIdentifier())).findFirst().get();
    assert (storagePort.getValue().equals(StorageStateEnum.S0));
    assert (executeExternalTransition2.getTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition2);

    // Expect execute external transition message with no generator output and a storage output
    // of StorageStateEnum.S1
    DevsMessage messag4 = toRecorderProbe.receiveMessage(Duration.ofSeconds(100));
    assert (messag2 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition3 =
        (ExecuteTransition<LongSimTime>) messag4;
    assert (executeExternalTransition3.getModelInputsOption().isPresent());
    Bag recorderBag4 = executeExternalTransition3.getModelInputsOption().get();
    Optional<PortValue<?>> generatorPort4 = recorderBag4
        .getPortValueList().stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortIdentifier())).findFirst();
    assert (generatorPort4.isEmpty());
    PortValue<?> storagePort4 = recorderBag4
        .getPortValueList().stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortIdentifier())).findFirst().get();
    assert (storagePort4.getValue().equals(StorageStateEnum.S1));
    assert (executeExternalTransition2.getTime().getT() == 1L);
    KafkaUtils.deleteTopics(Arrays.asList(generatorInputTopic, coordinatorInputTopic, storageInputTopic), adminClient);
    Thread.sleep(5000);


  }


}
