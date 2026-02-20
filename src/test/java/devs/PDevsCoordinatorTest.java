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

package devs;

import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.time.LongSimTime;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


/**
 * This class performs unit tests for the PDevsCoordinator, focusing on its interactions with
 * simulated models and root coordinator in a Distributed Discrete Event System (DEVS) simulation.
 * It uses the ActorTestKit for testing Akka actor behaviors, verifies message passing, and checks
 * the correctness of simulation transitions, initialization, and output behaviors.
 * <p>
 * Key functionality being tested: - Initialization of the simulation through InitSimMessage. -
 * Coordination and message passing with coupled models. - Internal, external model transitions and
 * the aggregation of outputs. - Root coordinator's orchestration of simulation cycles.
 * <p>
 * The test validates that the PDevsCoordinator interacts correctly with its sub-models, passes
 * required messages at appropriate times, and ensures simulation progression with correct time
 * values.
 */
public class PDevsCoordinatorTest {

  /**
   * A statically defined {@link ActorTestKit} used for setting up and testing actor-based behaviors
   * within the context of PDEVS simulation testing.
   * <ul>
   * - Simplifies the creation and management of actor-based tests.
   * - Allows spawning actors, creating probes, and managing the lifecycle of test scenarios.
   * - Ensures proper cleanup of testing resources after execution.
   * </ul>
   */
  static final ActorTestKit testKit = ActorTestKit.create();

  /**
   * Cleans up resources used during the test execution. This method shuts down the testkit to
   * release any resources and ensure no lingering threads remain after tests have completed. It is
   * annotated with {@code @AfterAll} to ensure it is executed once after all test methods within
   * this test class have run.
   */
  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  private static final String simulationId = "PDevsCoordinatorTest";
  static final String generatorName = "generator";
  static final String storageName = "storage";

  /**
   * This method tests the functionality of a PDEVS (Parallel DEVS) coordinator and its interaction
   * with model simulators, ensuring proper simulation message flows and transitions.
   * <p>
   * The method validates:
   * <p>
   * 1. Initialization of the coordinator with a coupled model "genStoreCoupled" consisting of
   * generator and storage models. 2. Propagation of the `InitSim` messages to the individual
   * simulators - generator and storage models. 3. Processing of the `NextTime` message by the
   * root-level coordinator indicating the imminent next event time. 4. Handling of `SendOutput`
   * messages, ensuring the imminent generator outputs a value according to its scheduled time. 5.
   * Proper coordination of internal and external transitions, including: - Transition requests sent
   * by the coordinator to simulators. - Processing of received outputs and corresponding model
   * states and inputs. 6. Aggregation of `TransitionDone` responses from simulators and relaying of
   * a `ModelOutputMessage` to the root-level coordinator. 7. Cyclic behavior of the PDEVS
   * simulation, ensuring the second cycle of time-based transitions follows the expected flow. 8.
   * Validation of inputs and outputs at each step, verifying the correctness of state progression
   * and message handling mechanisms across the simulation lifecycle.
   * <p>
   * This test ensures that the PDEVS coordinator behaves as expected under the tested scenario,
   * maintaining synchronization and message accuracy between multiple interacting simulation
   * models.
   */
  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("Test PDEVS Coordinator")
  void pdevsCoordinatorTest() {
    TestProbe<DevsMessage> generatorInProbe = testKit.createTestProbe();
    TestProbe<DevsMessage> storageInProbe = testKit.createTestProbe();
    TestProbe<DevsMessage> rootInProbe = testKit.createTestProbe();

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generatorInProbe.getRef());
    modelSimulators.put("storage", storageInProbe.getRef());

    PDevsCouplings genStoreCoupling = PDevsCouplings.builder()
        .addConnection("generator", "OUTPUT", "storage", "INPUT")
        .build();

    ActorRef<DevsMessage> coordinator = testKit.spawn(
        Behaviors.setup(context -> new PDevsCoordinator<LongSimTime>("genStoreCoupled",
            modelSimulators, genStoreCoupling, context)));

    coordinator.tell(new SimulationInitMessage(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId(simulationId)
        .messageId("SimulationInit")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build(), rootInProbe.getRef()));

    // PDEVS Coordinator should pass InitSim message to Generator and Storage
    ActorRef<DevsMessage> generatorSim = testKit.spawn(
        Behaviors.setup(context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0, generatorName), LongSimTime.builder().t(0L).build(), context)));
    DevsMessage message1 = generatorInProbe.receiveMessage();
    assert (message1 instanceof SimulationInitMessage<?>);
    SimulationInitMessage<LongSimTime> initSimMessage = (SimulationInitMessage<LongSimTime>) message1;
    assert (initSimMessage.getSimulationInit().getEventTime().getT() == 0L);
    generatorSim.tell(initSimMessage);

    ActorRef<DevsMessage> storageSim = testKit.spawn(Behaviors
        .setup(context -> new PDevsSimulator<LongSimTime, StorageState, StorageModel>(
            new StorageModel(new StorageState(StorageStateEnum.S0), storageName),
            LongSimTime.builder().t(0L).build(), context)));
    DevsMessage message2 = storageInProbe.receiveMessage();
    assert (message2 instanceof SimulationInitMessage<?>);
    SimulationInitMessage<LongSimTime> initSimMessage2 = (SimulationInitMessage<LongSimTime>) message1;
    assert (initSimMessage2.getSimulationInit().getEventTime().getT() == 0L);
    storageSim.tell(initSimMessage2);

    // Root coordinator should compile next event messages and tell root coordinator NextTime =
    // 1
    DevsMessage message3 = rootInProbe.receiveMessage();
    assert (message3 instanceof NextInternalTimeReport<?>);
    NextInternalTimeReport<LongSimTime> nextTime = (NextInternalTimeReport<LongSimTime>) message3;
    assert (nextTime.getNextInternalTime().getT() == 1);
    assert ("genStoreCoupled".equals(nextTime.getSenderId()));

    // Root coordinator should tell imminent generator to send an output
    coordinator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message4 = generatorInProbe.receiveMessage();
    assert (message4 instanceof RequestOutput<?>);
    RequestOutput<LongSimTime> sendOutput = (RequestOutput<LongSimTime>) message4;
    assert (sendOutput.getEventTime().getT() == 1L);
    generatorSim.tell(sendOutput);

    // Root coordinator gets an output of 0 from the generator. It then tells the simulator to
    // execute and external transition with an input of 0 and the generator to execute and
    // internal transition
    DevsMessage messag5 = storageInProbe.receiveMessage();
    assert (messag5 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition =
        (ExecuteTransition<LongSimTime>) messag5;
    assert (executeExternalTransition.getPayload().getInputs().size() > 0);
    List<PortValue<?>> modelInputs = executeExternalTransition.getPayload().getInputs();
    assert ((Integer) modelInputs.get(0).getValue() == 0);
    assert (executeExternalTransition.getEventTime().getT() == 1L);
    storageSim.tell(executeExternalTransition);

    DevsMessage message6 = generatorInProbe.receiveMessage();
    assert (message6 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeInternalTransition =
        (ExecuteTransition<LongSimTime>) message6;
    assert (executeInternalTransition.getPayload().getInputs().isEmpty());
    assert (executeInternalTransition.getEventTime().getT() == 1L);
    generatorSim.tell(executeInternalTransition);

    // Each of the models executes transition and sends a TransitionDone to the coordinator
    // Coordinator aggregates and sends a ModelOutputMessage to the root coordinator with a next
    // time of 1
    DevsMessage message7 = rootInProbe.receiveMessage();
    assert (message7 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage =
        (OutputReport<LongSimTime>) message7;
    assert (modelOutputMessage.getNextInternalTime().getT() == 1L);
    assert ("genStoreCoupled".equals(modelOutputMessage.getSenderId()));

    // Root coordinator starts a new cycle with a SendOutput message
    // Coordinator sends a SendOutput to the generator and the storage model
    coordinator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message8 = generatorInProbe.receiveMessage();
    assert (message8 instanceof RequestOutput<?>);
    RequestOutput<LongSimTime> sendOutput3 = (RequestOutput<LongSimTime>) message8;
    assert (sendOutput3.getEventTime().getT() == 1L);
    generatorSim.tell(sendOutput3);
    DevsMessage message8a = storageInProbe.receiveMessage();
    assert (message8a instanceof RequestOutput<?>);
    RequestOutput<LongSimTime> sendOutput3a = (RequestOutput<LongSimTime>) message8a;
    assert (sendOutput3a.getEventTime().getT() == 1L);
    storageSim.tell(sendOutput3a);

    // Root coordinator gets an output of 1 from the generator. It then tells the storage to
    // execute and external transition with an input of 1 and the generator to execute and
    // internal transition
    DevsMessage message9 = storageInProbe.receiveMessage();
    assert (message9 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition2 =
        (ExecuteTransition<LongSimTime>) message9;
    assert (executeExternalTransition2.getPayload().getInputs().size() > 0);
    List<PortValue<?>> modelInputs2 = executeExternalTransition2.getPayload().getInputs();
    assert ((Integer) modelInputs2.get(0).getValue() == 1);
    assert (executeExternalTransition2.getEventTime().getT() == 1L);
    storageSim.tell(executeExternalTransition);

    DevsMessage message10 = generatorInProbe.receiveMessage();
    assert (message10 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeInternalTransition2 =
        (ExecuteTransition<LongSimTime>) message10;
    assert (executeInternalTransition2.getPayload().getInputs().isEmpty());
    assert (executeInternalTransition2.getEventTime().getT() == 1L);
    generatorSim.tell(executeInternalTransition);

    // Each of the models executes transition and sends a TransitionDone to the coordinator
    // Coordinator aggregates and sends a ModelOutputMessage to the root coordinator with a next
    // time of 1
    DevsMessage message11 = rootInProbe.receiveMessage();
    assert (message11 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage2 =
        (OutputReport<LongSimTime>) message11;
    assert (modelOutputMessage2.getNextInternalTime().getT() == 1L);
    assert ("genStoreCoupled".equals(modelOutputMessage2.getSenderId()));

    // Root coordinator starts a new cycle with a SendOutput message
    // Coordinator sends a SendOutput to the imminent storage model
    coordinator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message12 = storageInProbe.receiveMessage();
    assert (message12 instanceof RequestOutput<?>);
    RequestOutput<LongSimTime> sendOutput4 = (RequestOutput<LongSimTime>) message12;
    assert (sendOutput4.getEventTime().getT() == 1L);
    storageSim.tell(sendOutput4);

    // The storage model sends output to the coordinator, which in turn tells it to execute
    // transition
    DevsMessage message13 = storageInProbe.receiveMessage();
    assert (message13 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeInternalTransition3 =
        (ExecuteTransition<LongSimTime>) message13;
    assert (executeInternalTransition3.getEventTime().getT() == 1L);
    assert (executeInternalTransition3.getPayload().getInputs().isEmpty());
    storageSim.tell(executeInternalTransition3);

    // The storage model executes transition and sends a TransitionDone to the coordinator
    // Coordinator sends a ModelOutputMessage to the root coordinator with a next time of 2
    DevsMessage message14 = rootInProbe.receiveMessage();
    assert (message14 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage3 =
        (OutputReport<LongSimTime>) message14;
    assert (modelOutputMessage3.getNextInternalTime().getT() == 2L);
    assert ("genStoreCoupled".equals(modelOutputMessage3.getSenderId()));

  }
}
