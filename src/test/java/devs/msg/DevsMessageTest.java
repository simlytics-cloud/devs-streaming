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

package devs.msg;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.log.DevsModelLogMessage;
import devs.msg.log.RunIdMessage;
import devs.msg.log.StateMessage;
import devs.msg.time.LongSimTime;
import devs.msg.time.SimTime;
import devs.utils.DevsObjectMapper;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The `DevsMessageTest` class contains unit tests for serializing and deserializing various types
 * of `DevsMessage` objects using Akka Serialization and custom object mappers. This class verifies
 * proper implementation of serialization and deserialization mechanisms for messages in the DEVS
 * simulation framework.
 * <p>
 * Key methods in this class include: - `serdeInitMessage()`: Tests the serialization and
 * deserialization of the `InitSim` message. - `serdeExecuteTransitionMessage()`: Tests the
 * serialization and deserialization of the `ExecuteTransition` message. - `serdeModelDone()`: Tests
 * the serialization and deserialization of the `ModelDone` message. - `serdeModelOutputs()`: Tests
 * the serialization and deserialization of the `ModelOutputMessage`. - `serdeNextTimeMessage()`:
 * Tests the serialization and deserialization of the `NextTime` message. - `serdeSendOutput()`:
 * Tests the serialization and deserialization of the `SendOutput` message. -
 * `serdeSimulationDone()`: Tests the serialization and deserialization of the `SimulationDone`
 * message. - `serdeTransitionDone()`: Tests the serialization and deserialization of the
 * `TransitionDone` message.
 * <p>
 * Helper methods: - `deserialize`: Converts byte arrays back into `DevsMessage` objects based on
 * the provided serializer ID and manifest. - `serialize`: Converts a `DevsMessage` object into a
 * UTF-8 encoded string.
 * <p>
 * The tests ensure: - Correct mapping of serialized data to their respective message types. -
 * Preservation of message properties, such as time, sender, and additional fields, during
 * serialization and deserialization. - Compatibility with Akka's serialization framework.
 * <p>
 * This class leverages the Jackson ObjectMapper for JSON serialization and Akka Serialization
 * Extension for binary serialization.
 */
public class DevsMessageTest {

  LongSimTime zero = LongSimTime.builder().t(0L).build();
  LongSimTime one = LongSimTime.builder().t(1L).build();

  ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "example");

  // Get the Serialization Extension
  Serialization serialization = SerializationExtension.get(system);
  ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();


  private DevsMessage deserialize(byte[] bytes) {
    return serialization.deserialize(bytes, DevsMessage.class).get();

  }

  private String serialize(DevsMessage devsMessage) {
    return new String(serialization.serialize(devsMessage).get(), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("Serialize deserialize InitSim")
  void serdeInitMessage() throws JsonProcessingException {
    InitSim<?> initSim = InitSim.builder().time(zero).build();
    DevsMessage devsMessage = initSim;
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    assert (deserialized instanceof InitSim<?>);
    InitSim<LongSimTime> desInitSim = (InitSim<LongSimTime>) deserialized;
    assert (desInitSim.getTime().getT() == 0L);

    String json = objectMapper.writeValueAsString(devsMessage);
    deserialized = objectMapper.readValue(json, DevsMessage.class);
    assert (deserialized instanceof InitSim<?>);
    desInitSim = (InitSim<LongSimTime>) deserialized;
    assert (desInitSim.getTime().getT() == 0L);
  }

  @Test
  @DisplayName("Serialize deserialize ExecuteTransition")
  void serdeExecuteTransitionMessage() throws JsonProcessingException {
    ExecuteTransition<?> executeTransition =
        ExecuteTransition.builder().time(zero)
            .modelInputsOption(Optional.of(Bag.builder()
                .addPortValueList(StorageModel.storageInputPort.createPortValue(1)).build()))
            .build();
    DevsMessage devsMessage = executeTransition;
    String json = objectMapper.writeValueAsString(devsMessage);
    devsMessage = objectMapper.readValue(json, DevsMessage.class);
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    // DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
    assert (deserialized instanceof ExecuteTransition<?>);
    ExecuteTransition<LongSimTime> desExecuteTransition =
        (ExecuteTransition<LongSimTime>) deserialized;
    assert (desExecuteTransition.getTime().getT() == 0L);
    assert ((Integer) desExecuteTransition.getModelInputsOption().get().getPortValueList().get(0)
        .getValue() == 1);
  }

  @Test
  @DisplayName("Serialize deserialize ModelDone")
  void serdeModelDone() throws JsonProcessingException {
    ModelDone<?> modelDone =
        ModelDone.builder().time(zero).sender(GeneratorModel.identifier).build();
    DevsMessage devsMessage = modelDone;
    String json = objectMapper.writeValueAsString(devsMessage);
    devsMessage = objectMapper.readValue(json, DevsMessage.class);
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    assert (deserialized instanceof ModelDone<?>);
    ModelDone<LongSimTime> desModelDoneMessage = (ModelDone<LongSimTime>) deserialized;
    assert (desModelDoneMessage.getTime().getT() == 0L
        && desModelDoneMessage.getSender().compareTo(GeneratorModel.identifier) == 0);
  }

  @Test
  @DisplayName("Serialize and deserialize ModelOutputs")
  void serdeModelOutputs() throws JsonProcessingException {

    PortValue<Integer> portValue = GeneratorModel.generatorOutputPort.createPortValue(1);
    ModelOutputMessage<?> modelOutputMessage =
        ModelOutputMessage.builder().modelOutput(Bag.builder().addPortValueList(portValue).build())
            .nextTime(one).time(zero).sender(GeneratorModel.identifier).build();

    DevsMessage devsMessage = modelOutputMessage;
    String generatorOutputJson = objectMapper.writeValueAsString(devsMessage);
    devsMessage = objectMapper.readValue(generatorOutputJson, DevsMessage.class);
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    assert (deserialized instanceof ModelOutputMessage<?>);
    ModelOutputMessage<LongSimTime> desModelOutputMessage =
        (ModelOutputMessage<LongSimTime>) deserialized;
    assert (desModelOutputMessage.getTime().getT() == 0L);
    assert (desModelOutputMessage.getNextTime().getT() == 1L);
    Bag desGeneratorOutput = desModelOutputMessage.getModelOutput();
    assert ((Integer) desGeneratorOutput.getPortValueList().get(0).getValue() == 1);
  }

  @Test
  @DisplayName("Serialize deserialize NextTime")
  void serdeNextTimeMessage() throws JsonProcessingException {
    NextTime<SimTime> nextTime =
        NextTime.builder().time(one).sender(GeneratorModel.identifier).build();
    DevsMessage devsMessage = nextTime;
    String json = objectMapper.writeValueAsString(devsMessage);
    devsMessage = objectMapper.readValue(json, DevsMessage.class);
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    assert (deserialized instanceof NextTime<?>);
    NextTime<LongSimTime> desNextTime = (NextTime<LongSimTime>) deserialized;
    assert (desNextTime.getTime().getT() == 1L
        && desNextTime.getSender().compareTo(GeneratorModel.identifier) == 0);
  }

  @Test
  @DisplayName("Serialize deserialize SendOutput")
  void serdeSendOutput() throws JsonProcessingException {
    SendOutput<?> sendOutput = SendOutput.builder().time(zero).build();
    DevsMessage devsMessage = sendOutput;
    String json = objectMapper.writeValueAsString(devsMessage);
    devsMessage = objectMapper.readValue(json, DevsMessage.class);
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    assert (deserialized instanceof SendOutput<?>);
    SendOutput<LongSimTime> desSendOutput = (SendOutput<LongSimTime>) deserialized;
    assert (desSendOutput.getTime().getT() == 0L);
  }

  @Test
  @DisplayName("Serialize deserialize SimulationDone")
  void serdeSimulationDone() throws JsonProcessingException {
    SimulationDone<?> simulationDone = SimulationDone.builder().time(zero).build();
    DevsMessage devsMessage = simulationDone;
    String json = objectMapper.writeValueAsString(devsMessage);
    devsMessage = objectMapper.readValue(json, DevsMessage.class);
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    assert (deserialized instanceof SimulationDone<?>);
    SimulationDone<LongSimTime> desSimulationDone = (SimulationDone<LongSimTime>) deserialized;
    assert (desSimulationDone.getTime().getT() == 0L);
  }

  @Test
  @DisplayName("Serialize deserialize TransitionDone")
  void serdeTransitionDone() throws JsonProcessingException {
    TransitionDone<?> transitionDone =
        TransitionDone.builder().nextTime(one).time(zero).sender(GeneratorModel.identifier).build();
    DevsMessage devsMessage = transitionDone;
    String json = objectMapper.writeValueAsString(devsMessage);
    devsMessage = objectMapper.readValue(json, DevsMessage.class);
    byte[] bytes = serialization.serialize(devsMessage).get();
    DevsMessage deserialized = deserialize(bytes);
    assert (deserialized instanceof TransitionDone<?>);
    TransitionDone<LongSimTime> desTransitionDone = (TransitionDone<LongSimTime>) deserialized;
    assert (desTransitionDone.getTime().getT() == 0L);
    assert (desTransitionDone.getNextTime().getT() == 1L);
    assert (desTransitionDone.getSender().compareTo(GeneratorModel.identifier) == 0);
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("Serialize and deserialize DevsRunIdMessage")
  void serdeDevsRunIdMessage() throws JsonProcessingException {
    StorageState storageState = new StorageState(StorageStateEnum.S1);
    StateMessage<SimTime, Object> stateMessage = StateMessage.builder().modelId("storage")
        .modelState(storageState).time(LongSimTime.create(1)).build();
    RunIdMessage runIdMessage =
        RunIdMessage.builder().runId("testId").devsLogMessage(stateMessage).build();

    String json = objectMapper.writeValueAsString(runIdMessage);
    RunIdMessage deserialized = objectMapper.readValue(json, RunIdMessage.class);
    //byte[] bytes = serialization.serialize(deserialized).get();
    //deserialized = serialization.deserialize(bytes, RunIdMessage.class).get();
    assertTrue(deserialized.getDevsLogMessage() instanceof StateMessage<?, ?>);
    StateMessage<?, ?> desStateMessage = (StateMessage<?, ?>) deserialized.getDevsLogMessage();
    String stateMessageJson = objectMapper.writeValueAsString(desStateMessage.getModelState());

    StorageState desState = objectMapper.readValue(stateMessageJson, StorageState.class);
    assert desState.getStateValue().equals(StorageStateEnum.S1);

    ExecuteTransition<?> executeTransition =
        ExecuteTransition.builder().time(zero)
            .modelInputsOption(Optional.of(Bag.builder()
                .addPortValueList(StorageModel.storageInputPort.createPortValue(1)).build()))
            .build();
    DevsModelLogMessage<?> devsModelLogMessage =
        DevsModelLogMessage.builder().time(executeTransition.getTime()).modelId("storage")
            .devsMessage(executeTransition).build();
    RunIdMessage runIdMessage2 =
        RunIdMessage.builder().runId("testId").devsLogMessage(devsModelLogMessage).build();
    String json2 = objectMapper.writeValueAsString(runIdMessage2);
    RunIdMessage deserialized2 = objectMapper.readValue(json2, RunIdMessage.class);
    assert ("testId".equals(deserialized2.getRunId()));
    assert (deserialized2.getDevsLogMessage() instanceof DevsModelLogMessage);
    DevsModelLogMessage<LongSimTime> desDevsModelLogMessage =
        (DevsModelLogMessage<LongSimTime>) deserialized2.getDevsLogMessage();
    assert desDevsModelLogMessage.getDevsMessage() instanceof ExecuteTransition;
    ExecuteTransition<LongSimTime> desExecuteTransition =
        (ExecuteTransition<LongSimTime>) desDevsModelLogMessage.getDevsMessage();
    assert (desExecuteTransition.getTime().getT() == 0L);
    assert ((Integer) desExecuteTransition.getModelInputsOption().get().getPortValueList().get(0)
        .getValue() == 1);
  }

  @Test
  @DisplayName("Write example messages")
  void writeExampleMessages() throws JsonProcessingException, FileNotFoundException {
    // objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    PrintWriter pw = new PrintWriter("data/example-messages.json");
    pw.println("[");

    InitSim<?> initSim = InitSim.builder().time(zero).build();
    String json = serialize(initSim); // objectMapper.writeValueAsString(initSim);
    pw.print(json);
    pw.println(",");

    ExecuteTransition<SimTime> executeTransition =
        ExecuteTransition.builder().time(zero)
            .modelInputsOption(Optional.of(Bag.builder()
                .addPortValueList(StorageModel.storageInputPort.createPortValue(1)).build()))
            .build();
    json = serialize(executeTransition); // objectMapper.writeValueAsString(executeTransition);
    pw.print(json);
    pw.println(",");

    ModelDone<?> modelDone =
        ModelDone.builder().time(zero).sender(GeneratorModel.identifier).build();
    json = serialize(modelDone); // objectMapper.writeValueAsString(modelDone);
    pw.print(json);
    pw.println(",");

    ModelOutputMessage<?> modelOutputMessage =
        ModelOutputMessage.builder()
            .modelOutput(Bag.builder()
                .addPortValueList(
                    StorageModel.storageOutputPort.createPortValue(StorageStateEnum.S1))
                .build())
            .nextTime(zero).time(one).sender(GeneratorModel.identifier).build();

    json = serialize(modelOutputMessage); // objectMapper.writeValueAsString(modelOutputMessage);
    pw.print(json);
    pw.println(",");

    NextTime<SimTime> nextTime =
        NextTime.builder().time(one).sender(GeneratorModel.identifier).build();
    json = serialize(nextTime); // objectMapper.writeValueAsString(nextTime);
    pw.print(json);
    pw.println(",");

    SendOutput<?> sendOutput = SendOutput.builder().time(zero).build();
    json = serialize(sendOutput); // objectMapper.writeValueAsString(sendOutput);
    pw.print(json);
    pw.println(",");

    SimulationDone<?> simulationDone = SimulationDone.builder().time(zero).build();
    json = serialize(simulationDone); // objectMapper.writeValueAsString(simulationDone);
    pw.print(json);
    pw.println(",");

    TransitionDone<?> transitionDone =
        TransitionDone.builder().nextTime(one).time(zero).sender(GeneratorModel.identifier).build();
    json = serialize(transitionDone); // objectMapper.writeValueAsString(transitionDone);
    pw.println(json);

    pw.println("]");
    pw.close();
  }

}
