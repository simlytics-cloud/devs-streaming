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

package devs.simulation;

import devs.PDevsCoordinator;
import devs.PDevsCouplings;
import devs.PDevsSimulator;
import devs.RootCoordinator;
import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ModelIdPayload;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import devs.simulation.recorder.GenStoreRecorderOutputCouplingHandler;
import devs.simulation.recorder.RecorderModel;
import example.coordinator.GenStoreInputCouplingHandler;
import example.coordinator.GenStoreOutputCouplingHandler;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A test class to verify the integration and behavior of a Parallel DEVS (PDEVS) simulation setup
 * involving components like RootCoordinator, PDEVSCoordinator, CoupledModelFactory, and
 * PDEVSSimulators.
 * <p>
 * This class utilizes the ActorTestKit for testing Akka actors used in the PDEVS simulation and
 * verifies their interactions through test probes and assertions. The test primarily focuses on
 * validating: - Initialization of the simulation with an initial time. - Communication and external
 * transitions between PDEVS components (generator, storage, and recorder). - The behavior of
 * coupled models and their interconnections through coupling handlers.
 * <p>
 * The simulation consists of: - GeneratorModel: Produces outputs at specified times during the
 * simulation. - StorageModel: Reacts to generator outputs and updates its state. - RecorderModel:
 * Records the states and outputs for validation.
 * <p>
 * The couplings between these models are tested to ensure correct propagation of events and
 * transitions. The test also validates that appropriate messages are sent and received throughout
 * the simulation.
 */
public class PDevsSimulationTest {

  /**
   * A static and final instance of {@link ActorTestKit} initialized via its factory method. This
   * instance supports testing actor-based systems by providing a dedicated actor system and
   * facilities for simulating and verifying actor interactions.
   * <p>
   * Usage of this test kit enables behavior tests for actors, including the ability to send
   * messages, assert received messages, and test actor lifecycle management in a controlled testing
   * environment.
   * <p>
   * It is managed at the class level to ensure a single test kit instance is reused across all test
   * cases, promoting efficient resource utilization.
   */
  static final ActorTestKit testKit = ActorTestKit.create();

  /**
   * Performs cleanup operations after all tests have been executed.
   * <p>
   * This method ensures that the testing environment is properly shut down by invoking the
   * `shutdownTestKit` method on the `testKit` instance.
   * <p>
   * It is annotated with `@AfterAll`, meaning the method will be executed once after all test
   * methods in the class have run.
   * <p>
   * Usage of this method ensures that resources and testing infrastructure used during the tests
   * are properly released, preventing potential resource leaks.
   */
  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  /**
   * Test method to verify the simulation behavior of a PDEVS model using actors such as
   * RootCoordinator, PDEVSCoordinator, CoupledModelFactory, and PDEVSSimulators.
   * <p>
   * This method performs the following key operations: - Spawns simulator actors for
   * GeneratorModel, StorageModel, and RecorderModel. - Configures a `PDevsCoordinator` with coupled
   * models and assigns couplings for interaction. - Sets up a `RootCoordinator` to manage the
   * entire simulation across the generated models. - Sends and validates messages such as
   * initialization (`InitSim`) and external transitions (`ExecuteTransition`) based on expected
   * behavioral outputs from the simulation.
   * <p>
   * Assertions are employed to ensure that generated simulation messages and transitions behave as
   * expected: - Validates that initial simulation state starts correctly. - Confirms expected
   * inputs and outputs from individual models during external transitions.
   * <p>
   * This test ensures all models and their interactions adhere to the expected logical flow and
   * proper states are propagated through the simulation in a coordinated manner.
   *
   * @throws InterruptedException if the simulation execution is interrupted
   */
  @Test
  @DisplayName("Test PDEVS Simulation with RootCoordinator, PDEVSCoordinator, CoupledModelFactory, "
      + "and PDEVSSimulators working together")
  void parallelDevsSimulationTest() throws InterruptedException {

    ActorRef<DevsMessage> generatorSim = testKit.spawn(
        Behaviors.setup(context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0), LongSimTime.builder().t(0L).build(), context)));

    ActorRef<DevsMessage> storageSim = testKit.spawn(Behaviors
        .setup(context -> new PDevsSimulator<LongSimTime, StorageState, StorageModel>(
            new StorageModel(new StorageState(StorageStateEnum.S0)),
            LongSimTime.builder().t(0L).build(), context)));

    TestProbe<DevsMessage> toRecorderProbe = testKit.createTestProbe("toRecorderProbe");

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generatorSim);
    modelSimulators.put("storage", storageSim);
    modelSimulators.put("recorder", toRecorderProbe.getRef());

    PDevsCouplings genStoreCoupling =
        new PDevsCouplings(Collections.singletonList(new GenStoreInputCouplingHandler()),
            Arrays.asList(new GenStoreOutputCouplingHandler(),
                new GenStoreRecorderOutputCouplingHandler()));

    ActorRef<DevsMessage> coordinator = testKit.spawn(
        Behaviors.setup(context -> new PDevsCoordinator<LongSimTime>("genStoreCoupled",
            modelSimulators, genStoreCoupling, context)));

    ActorRef<DevsMessage> rootCoordinator =
        testKit.spawn(Behaviors.setup(context -> new RootCoordinator<LongSimTime>(context,
            LongSimTime.builder().t(2L).build(), coordinator, "genStoreCoupled")));
    rootCoordinator.tell(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .payload(ModelIdPayload.builder().modelId("root").build())
        .simulationId("PDevsSimulationTest")
        .messageId("SimulationInit")
        .senderId("TestActor")
        .build());

    ActorRef<DevsMessage> recorderSim = testKit.spawn(
        Behaviors.setup(context -> new PDevsSimulator<LongSimTime, Void, RecorderModel>(
            new RecorderModel("recorder"), LongSimTime.builder().t(0L).build(),
            context)));

    // Expect initSim message to recorder
    DevsMessage message1 = toRecorderProbe.receiveMessage();
    assert (message1 instanceof SimulationInitMessage<?>);
    SimulationInitMessage<LongSimTime> initSimMessage = (SimulationInitMessage<LongSimTime>) message1;
    assert (initSimMessage.getSimulationInit().getEventTime().getT() == 0L);
    recorderSim.tell(initSimMessage);

    // Expect execute external transition message with generator output of 0
    DevsMessage messag2 = toRecorderProbe.receiveMessage();
    assert (messag2 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition =
        (ExecuteTransition<LongSimTime>) messag2;
    assert (executeExternalTransition.getPayload().getInputs().size() > 0);
    assert ((Integer) executeExternalTransition.getPayload().getInputs()
        .get(0).getValue() == 0);
    assert (executeExternalTransition.getEventTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition);

    // Expect execute external transition message with generator output of 1 and a storage
    // output
    // of StorageStateEnum.S0
    DevsMessage messag3 = toRecorderProbe.receiveMessage();
    assert (messag3 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition2 =
        (ExecuteTransition<LongSimTime>) messag3;
    assert (executeExternalTransition2.getPayload().getInputs().size() > 0);
    PortValue<?> generatorPortValue = executeExternalTransition2.getPayload().getInputs()
        .stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert ((Integer) generatorPortValue.getValue() == 1);
    PortValue<?> storagePortValue = executeExternalTransition2.getPayload().getInputs()
        .stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert (storagePortValue.getValue().equals("S0"));
    assert (executeExternalTransition2.getEventTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition2);

    // Expect execute external transition message with no generator output and a storage output
    // of StorageStateEnum.S1
    DevsMessage messag4 = toRecorderProbe.receiveMessage();
    assert (messag4 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition3 =
        (ExecuteTransition<LongSimTime>) messag4;
    assert (executeExternalTransition3.getPayload().getInputs().size() > 0);
    Optional<PortValue<?>> generatorPortOption = executeExternalTransition3
        .getPayload().getInputs().stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortName())).findFirst();
    assert (generatorPortOption.isEmpty());
    PortValue<?> storagePortValue4 = executeExternalTransition3.getPayload().getInputs()
        .stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert (storagePortValue4.getValue().equals("S1"));

  }
}
