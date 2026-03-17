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
import devs.iso.PortValue;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.time.LongSimTime;
import devs.simulation.recorder.RecorderModel;
import example.generator.ScheduledGeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests a simulation using the {@link ScheduledGeneratorModel} version of the components.
 * It verifies the integration of RootCoordinator, PDEVSCoordinator, and PDEVSSimulators.
 */
public class ScheduledDevsModelSimulationTest {

  static final ActorTestKit testKit = ActorTestKit.create();
  static final String generatorName = "generator";
  static final String storageName = "storage";

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  /**
   * Verifies the simulation behavior by checking initialization and transition messages.
   */
  @Test
  @DisplayName("Parallel DEVS Simulation with Scheduled Models")
  void parallelDevsSimulationTest() throws InterruptedException {

    ActorRef<DevsMessage> generatorSim = testKit.spawn(PDevsSimulator.create(
            new ScheduledGeneratorModel(0, generatorName), LongSimTime.create(0)));

    ActorRef<DevsMessage> storageSim = testKit.spawn(PDevsSimulator.create(
            new StorageModel(new StorageState(StorageStateEnum.S0), storageName),
            LongSimTime.create(0)));

    TestProbe<DevsMessage> toRecorderProbe = testKit.createTestProbe("toRecorderProbe");

    Map<String, ActorRef<DevsMessage>> modelSimulators = Map.of(
            "generator", generatorSim,
            "storage", storageSim,
            "recorder", toRecorderProbe.getRef()
    );

    PDevsCouplings genStoreCoupling = PDevsCouplings.builder("genStoreCoupled")
        .addConnection("generator", "OUTPUT", "storage", "INPUT")
        .addConnection("generator", "OUTPUT", "recorder", "GENERATOR_OUTPUT")
        .addConnection("storage", "OUTPUT", "recorder", "STORAGE_OUTPUT")
        .build();

    ActorRef<DevsMessage> coordinator = testKit.spawn(PDevsCoordinator.create("genStoreCoupled",
            modelSimulators, genStoreCoupling));

    ActorRef<DevsMessage> rootCoordinator =
        testKit.spawn(RootCoordinator.create(LongSimTime.create(2), coordinator, "genStoreCoupled"));

    rootCoordinator.tell(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId("PendingOutputSimulationTest")
        .messageId("SimulationInit")
        .senderId("TestActor")
        .receiverId("root")
        .build());

    ActorRef<DevsMessage> recorderSim = testKit.spawn(PDevsSimulator.create(
            new RecorderModel("recorder"), LongSimTime.create(0)));

    // Expect initSim message to recorder
    SimulationInitMessage<LongSimTime> initSimMessage = toRecorderProbe.expectMessageClass(SimulationInitMessage.class);
    assert (initSimMessage.getSimulationInit().getEventTime().getT() == 0L);
    recorderSim.tell(initSimMessage);

    // Expect execute external transition message with generator output of 0
    ExecuteTransition<LongSimTime> executeExternalTransition = toRecorderProbe.expectMessageClass(ExecuteTransition.class);
    assert (executeExternalTransition.getPayload().getInputs().size() > 0);
    assert ((Integer) executeExternalTransition.getPayload().getInputs().get(0).getValue() == 0);
    assert (executeExternalTransition.getEventTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition);

    // Expect execute external transition message with generator output of 1 and a storage output of S0
    ExecuteTransition<LongSimTime> executeExternalTransition2 = toRecorderProbe.expectMessageClass(ExecuteTransition.class);
    assert (executeExternalTransition2.getPayload().getInputs().size() > 0);
    PortValue<?> generatorPortValue = executeExternalTransition2.getPayload().getInputs().stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert ((Integer) generatorPortValue.getValue() == 1);
    PortValue<?> storagePortValue = executeExternalTransition2.getPayload().getInputs().stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert (storagePortValue.getValue().equals("S0"));
    assert (executeExternalTransition2.getEventTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition2);

    // Expect execute external transition message with no generator output and a storage output of S1
    ExecuteTransition<LongSimTime> executeExternalTransition3 = toRecorderProbe.expectMessageClass(ExecuteTransition.class);
    assert (executeExternalTransition3.getPayload().getInputs().size() > 0);
    assert (executeExternalTransition3.getPayload().getInputs().stream()
        .noneMatch(pv -> "GENERATOR_OUTPUT".equals(pv.getPortName())));
    PortValue<?> storagePortValue4 = executeExternalTransition3.getPayload().getInputs().stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortName())).findFirst().get();
    assert (storagePortValue4.getValue().equals("S1"));
  }
}
