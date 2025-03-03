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

import devs.msg.Bag;
import devs.msg.DevsMessage;
import devs.msg.ExecuteTransition;
import devs.msg.InitSim;
import devs.msg.InitSimMessage;
import devs.msg.ModelOutputMessage;
import devs.msg.NextTime;
import devs.msg.SendOutput;
import devs.msg.time.LongSimTime;
import devs.msg.time.SimTime;
import example.coordinator.GenStoreInputCouplingHandler;
import example.coordinator.GenStoreOutputCouplingHandler;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import java.util.Collections;
import java.util.HashMap;
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

    PDevsCouplings genStoreCoupling =
        new PDevsCouplings(Collections.singletonList(new GenStoreInputCouplingHandler()),
            Collections.singletonList(new GenStoreOutputCouplingHandler()));

    ActorRef<DevsMessage> coordinator = testKit.spawn(
        Behaviors.setup(context -> new PDevsCoordinator<LongSimTime>("genStoreCoupled",
            modelSimulators, genStoreCoupling, context)));

    coordinator.tell(new InitSimMessage<SimTime>(
        InitSim.builder().time(LongSimTime.builder().t(0L).build()).build(),
        rootInProbe.getRef()));

    // PDEVS Coordinator should pass InitSim message to Generator and Storage
    ActorRef<DevsMessage> generatorSim = testKit.spawn(
        Behaviors.setup(context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0), LongSimTime.builder().t(0L).build(), context)));
    DevsMessage message1 = generatorInProbe.receiveMessage();
    assert (message1 instanceof InitSimMessage<?>);
    InitSimMessage<LongSimTime> initSimMessage = (InitSimMessage<LongSimTime>) message1;
    assert (initSimMessage.getInitSim().getTime().getT() == 0L);
    generatorSim.tell(initSimMessage);

    ActorRef<DevsMessage> storageSim = testKit.spawn(Behaviors
        .setup(context -> new PDevsSimulator<LongSimTime, StorageState, StorageModel>(
            new StorageModel(new StorageState(StorageStateEnum.S0)),
            LongSimTime.builder().t(0L).build(), context)));
    DevsMessage message2 = storageInProbe.receiveMessage();
    assert (message2 instanceof InitSimMessage<?>);
    InitSimMessage<LongSimTime> initSimMessage2 = (InitSimMessage<LongSimTime>) message1;
    assert (initSimMessage2.getInitSim().getTime().getT() == 0L);
    storageSim.tell(initSimMessage2);

    // Root coordinator should compile next event messages and tell root coordinator NextTime =
    // 1
    DevsMessage message3 = rootInProbe.receiveMessage();
    assert (message3 instanceof NextTime<?>);
    NextTime<LongSimTime> nextTime = (NextTime<LongSimTime>) message3;
    assert (nextTime.getTime().getT() == 1);
    assert ("genStoreCoupled".equals(nextTime.getSender()));

    // Root coordinator should tell imminent generator to send an output
    coordinator.tell(SendOutput.builder().time(LongSimTime.builder().t(1L).build()).build());
    DevsMessage message4 = generatorInProbe.receiveMessage();
    assert (message4 instanceof SendOutput<?>);
    SendOutput<LongSimTime> sendOutput = (SendOutput<LongSimTime>) message4;
    assert (sendOutput.getTime().getT() == 1L);
    generatorSim.tell(sendOutput);

    // Root coordinator gets an output of 0 from the generator. It then tells the simulator to
    // execute and external transition with an input of 0 and the generator to execute and
    // internal transition
    DevsMessage messag5 = storageInProbe.receiveMessage();
    assert (messag5 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition =
        (ExecuteTransition<LongSimTime>) messag5;
    assert (executeExternalTransition.getModelInputsOption().isPresent());
    Bag modelInputs = executeExternalTransition.getModelInputsOption().get();
    assert ((Integer) modelInputs.getPortValueList().get(0).getValue() == 0);
    assert (executeExternalTransition.getTime().getT() == 1L);
    storageSim.tell(executeExternalTransition);

    DevsMessage message6 = generatorInProbe.receiveMessage();
    assert (message6 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeInternalTransition =
        (ExecuteTransition<LongSimTime>) message6;
    assert (executeInternalTransition.getModelInputsOption().isEmpty());
    assert (executeInternalTransition.getTime().getT() == 1L);
    generatorSim.tell(executeInternalTransition);

    // Each of the models executes transition and sends a TransitionDone to the coordinator
    // Coordinator aggregates and sends a ModelOutputMessage to the root coordinator with a next
    // time of 1
    DevsMessage message7 = rootInProbe.receiveMessage();
    assert (message7 instanceof ModelOutputMessage<?>);
    ModelOutputMessage<LongSimTime> modelOutputMessage =
        (ModelOutputMessage<LongSimTime>) message7;
    assert (modelOutputMessage.getNextTime().getT() == 1L);
    assert ("genStoreCoupled".equals(modelOutputMessage.getSender()));

    // Root coordinator starts a new cycle with a SendOutput message
    // Coordinator sends a SendOutput to the generator and the storage model
    coordinator.tell(SendOutput.builder().time(LongSimTime.builder().t(1L).build()).build());
    DevsMessage message8 = generatorInProbe.receiveMessage();
    assert (message8 instanceof SendOutput<?>);
    SendOutput<LongSimTime> sendOutput3 = (SendOutput<LongSimTime>) message8;
    assert (sendOutput3.getTime().getT() == 1L);
    generatorSim.tell(sendOutput3);
    DevsMessage message8a = storageInProbe.receiveMessage();
    assert (message8a instanceof SendOutput<?>);
    SendOutput<LongSimTime> sendOutput3a = (SendOutput<LongSimTime>) message8a;
    assert (sendOutput3a.getTime().getT() == 1L);
    storageSim.tell(sendOutput3a);

    // Root coordinator gets an output of 1 from the generator. It then tells the storage to
    // execute and external transition with an input of 1 and the generator to execute and
    // internal transition
    DevsMessage message9 = storageInProbe.receiveMessage();
    assert (message9 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition2 =
        (ExecuteTransition<LongSimTime>) message9;
    assert (executeExternalTransition2.getModelInputsOption().isPresent());
    Bag modelInputs2 = executeExternalTransition2.getModelInputsOption().get();
    assert ((Integer) modelInputs2.getPortValueList().get(0).getValue() == 1);
    assert (executeExternalTransition2.getTime().getT() == 1L);
    storageSim.tell(executeExternalTransition);

    DevsMessage message10 = generatorInProbe.receiveMessage();
    assert (message10 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeInternalTransition2 =
        (ExecuteTransition<LongSimTime>) message10;
    assert (executeInternalTransition2.getModelInputsOption().isEmpty());
    assert (executeInternalTransition2.getTime().getT() == 1L);
    generatorSim.tell(executeInternalTransition);

    // Each of the models executes transition and sends a TransitionDone to the coordinator
    // Coordinator aggregates and sends a ModelOutputMessage to the root coordinator with a next
    // time of 1
    DevsMessage message11 = rootInProbe.receiveMessage();
    assert (message11 instanceof ModelOutputMessage<?>);
    ModelOutputMessage<LongSimTime> modelOutputMessage2 =
        (ModelOutputMessage<LongSimTime>) message11;
    assert (modelOutputMessage2.getNextTime().getT() == 1L);
    assert ("genStoreCoupled".equals(modelOutputMessage2.getSender()));

    // Root coordinator starts a new cycle with a SendOutput message
    // Coordinator sends a SendOutput to the imminent storage model
    coordinator.tell(SendOutput.builder().time(LongSimTime.builder().t(1L).build()).build());
    DevsMessage message12 = storageInProbe.receiveMessage();
    assert (message12 instanceof SendOutput<?>);
    SendOutput<LongSimTime> sendOutput4 = (SendOutput<LongSimTime>) message12;
    assert (sendOutput4.getTime().getT() == 1L);
    storageSim.tell(sendOutput4);

    // The storage model sends output to the coordinator, which in turn tells it to execute
    // transition
    DevsMessage message13 = storageInProbe.receiveMessage();
    assert (message13 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeInternalTransition3 =
        (ExecuteTransition<LongSimTime>) message13;
    assert (executeInternalTransition3.getTime().getT() == 1L);
    assert (executeInternalTransition3.getModelInputsOption().isEmpty());
    storageSim.tell(executeInternalTransition3);

    // The storage model executes transition and sends a TransitionDone to the coordinator
    // Coordinator sends a ModelOutputMessage to the root coordinator with a next time of 2
    DevsMessage message14 = rootInProbe.receiveMessage();
    assert (message14 instanceof ModelOutputMessage<?>);
    ModelOutputMessage<LongSimTime> modelOutputMessage3 =
        (ModelOutputMessage<LongSimTime>) message14;
    assert (modelOutputMessage3.getNextTime().getT() == 2L);
    assert ("genStoreCoupled".equals(modelOutputMessage3.getSender()));

  }
}
