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

package devs.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.PDevsCoordinator;
import devs.PDevsCouplings;
import devs.PDevsSimulator;
import devs.RootCoordinator;
import devs.msg.*;
import devs.msg.time.LongSimTime;
import devs.simulation.recorder.GenStoreRecorderOutputCouplingHandler;
import devs.simulation.recorder.RecorderModel;
import devs.utils.DevsObjectMapper;
import example.coordinator.GenStoreInputCouplingHandler;
import example.coordinator.GenStoreOutputCouplingHandler;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

public class PDevsSimulationTest {

  static final ActorTestKit testKit = ActorTestKit.create();
  static ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  @Test
  @DisplayName("Test PDEVS Simulation with RootCoordinator, PDEVSCoordinator, CoupledModelFactory, and PDEVSSimulators working together")
  void pDevsSimulationTest() throws InterruptedException {

    ActorRef<DevsMessage> generatorSim = testKit.spawn(Behaviors.setup(context ->
        new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
            new GeneratorModel(0),
            LongSimTime.builder().t(0L).build(),
            context
        )));

    ActorRef<DevsMessage> storageSim = testKit.spawn(Behaviors.setup(context ->
        new PDevsSimulator<LongSimTime, StorageState, StorageModel>(
            new StorageModel(new StorageState(StorageStateEnum.S0)),
            LongSimTime.builder().t(0L).build(),
            context
        )));

    TestProbe<DevsMessage> toRecorderProbe = testKit.createTestProbe("toRecorderProbe");

    ActorRef<DevsMessage> recorderSim = testKit.spawn(Behaviors.setup(context ->
        new PDevsSimulator<LongSimTime, Void, RecorderModel>(
            new RecorderModel("recorder"),
            LongSimTime.builder().t(0L).build(),
            context
        )));

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generatorSim);
    modelSimulators.put("storage", storageSim);
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
    ));


    ActorRef<DevsMessage> rootCoordinator = testKit.spawn(Behaviors.setup(context ->
        new RootCoordinator<LongSimTime>(context, LongSimTime.builder().t(2L).build(), coordinator)));
    rootCoordinator.tell(InitSim.builder().time(LongSimTime.builder().t(0L).build()).build());

    // Expect initSim message to recorder
    DevsMessage message1 = toRecorderProbe.receiveMessage();
    assert (message1 instanceof InitSimMessage<?>);
    InitSimMessage<LongSimTime> initSimMessage = (InitSimMessage<LongSimTime>) message1;
    assert (initSimMessage.getInitSim().getTime().getT() == 0L);
    recorderSim.tell(initSimMessage);

    // Expect execute external transition message with generator output of 0
    DevsMessage messag2 = toRecorderProbe.receiveMessage();
    assert (messag2 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition =
        (ExecuteTransition<LongSimTime>) messag2;
    assert (executeExternalTransition.getModelInputsOption().isPresent());
    assert ((Integer) executeExternalTransition.getModelInputsOption().get()
        .getPortValueList().get(0).getValue() == 0);
    assert (executeExternalTransition.getTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition);

    // Expect execute external transition message with generator output of 1 and a storage output
    // of StorageStateEnum.S0
    DevsMessage messag3 = toRecorderProbe.receiveMessage();
    assert (messag3 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition2 =
        (ExecuteTransition<LongSimTime>) messag3;
    assert (executeExternalTransition2.getModelInputsOption().isPresent());
    PortValue<?> generatorPortValue = executeExternalTransition2.getModelInputsOption().get()
        .getPortValueList().stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortIdentifier())).findFirst().get();
    assert ((Integer) generatorPortValue.getValue() == 1);
    PortValue<?> storagePortValue = executeExternalTransition2.getModelInputsOption().get()
        .getPortValueList().stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortIdentifier())).findFirst().get();
    assert (storagePortValue.getValue().equals(StorageStateEnum.S0));
    assert (executeExternalTransition2.getTime().getT() == 1L);
    recorderSim.tell(executeExternalTransition2);

    // Expect execute external transition message with no generator output and a storage output
    // of StorageStateEnum.S1
    DevsMessage messag4 = toRecorderProbe.receiveMessage();
    assert (messag4 instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> executeExternalTransition3 =
        (ExecuteTransition<LongSimTime>) messag4;
    assert (executeExternalTransition3.getModelInputsOption().isPresent());
    Optional<PortValue<?>> generatorPortOption = executeExternalTransition3.getModelInputsOption().get()
        .getPortValueList().stream()
        .filter(pv -> "GENERATOR_OUTPUT".equals(pv.getPortIdentifier())).findFirst();
    assert (generatorPortOption.isEmpty());
    PortValue<?> storagePortValue4 = executeExternalTransition3.getModelInputsOption().get()
        .getPortValueList().stream()
        .filter(pv -> "STORAGE_OUTPUT".equals(pv.getPortIdentifier())).findFirst().get();
    assert (storagePortValue4.getValue().equals(StorageStateEnum.S1));

  }
}
