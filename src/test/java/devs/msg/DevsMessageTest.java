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

package devs.msg;

import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializers;
import com.fasterxml.jackson.core.JsonProcessingException;
import devs.msg.time.LongSimTime;
import devs.msg.time.SimTime;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageStateEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class DevsMessageTest {
    LongSimTime zero = LongSimTime.builder().t(0L).build();
    LongSimTime one = LongSimTime.builder().t(1L).build();

    org.apache.pekko.actor.typed.ActorSystem<Void> system =
            org.apache.pekko.actor.typed.ActorSystem.create(Behaviors.empty(), "example");

    // Get the Serialization Extension
    Serialization serialization = SerializationExtension.get(system);

    //@BeforeAll
    //static void registerObjectMapper() {
    //    objectMapper.registerModule(new Jdk8Module());
    //}

    private DevsMessage deserialize(DevsMessage devsMessage, byte[] bytes) {
        int serializerId = serialization.findSerializerFor(devsMessage).identifier();
        String manifest = Serializers.manifestFor(serialization.findSerializerFor(devsMessage), devsMessage);
        // Turn it back into an object
        return (DevsMessage) serialization.deserialize(bytes, serializerId, manifest).get();

    }

    private String serialize(DevsMessage devsMessage) {
        return new String(serialization.serialize(devsMessage).get(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Serialize deserialize InitSim")
    void serdeInitMessage() throws JsonProcessingException {
        InitSim<?> initSim = InitSim.builder().time(zero).build();
        DevsMessage devsMessage = initSim;
        //String json = objectMapper.writeValueAsString(devsMessage);
        byte[] bytes = serialization.serialize(devsMessage).get();
        //DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
        int serializerId = serialization.findSerializerFor(devsMessage).identifier();
        String manifest = Serializers.manifestFor(serialization.findSerializerFor(devsMessage), devsMessage);
        // Turn it back into an object
        DevsMessage deserialized = (DevsMessage) serialization.deserialize(bytes, serializerId, manifest).get();
        assert (deserialized instanceof InitSim<?>);
        InitSim<LongSimTime> desInitSim = (InitSim<LongSimTime>) deserialized;
        assert(desInitSim.getTime().getT() == 0L);
    }

    @Test
    @DisplayName("Serialize deserialize ExecuteTransition")
    void serdeExecuteTransitionMessage() throws JsonProcessingException {
        ExecuteTransition<SimTime> executeTransition =
                ExecuteTransition.builder().time(zero).modelInputsOption(
                        Optional.of(Bag.builder()
                                .addPortValueList(StorageModel.storageInputPort.createPortValue(1))
                                .build())).build();
        DevsMessage devsMessage = executeTransition;
        //String json = objectMapper.writeValueAsString(devsMessage);
        byte[] bytes = serialization.serialize(devsMessage).get();
        DevsMessage deserialized = deserialize(devsMessage, bytes);
        //DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
        assert (deserialized instanceof ExecuteTransition<?>);
        ExecuteTransition<LongSimTime> desExecuteTransition =
                (ExecuteTransition<LongSimTime>) deserialized;
        assert(desExecuteTransition.getTime().getT() == 0L);
        assert ((Integer) desExecuteTransition.getModelInputsOption().get().getPortValueList().get(0).getValue() == 1);
    }

    @Test
    @DisplayName("Serialize deserialize ModelDone")
    void serdeModelDone() throws JsonProcessingException {
        ModelDone<?> modelDone = ModelDone.builder().time(zero).sender(GeneratorModel.identifier).build();
        DevsMessage devsMessage = modelDone;
        //String json = objectMapper.writeValueAsString(devsMessage);
        byte[] bytes = serialization.serialize(devsMessage).get();
        //DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
        DevsMessage deserialized = deserialize(devsMessage, bytes);
        assert (deserialized instanceof ModelDone<?>);
        ModelDone<LongSimTime> desModelDoneMessage = (ModelDone<LongSimTime>) deserialized;
        assert(desModelDoneMessage.getTime().getT() == 0L
                && desModelDoneMessage.getSender().compareTo(GeneratorModel.identifier) == 0);
    }

    @Test
    @DisplayName("Serialize and deserialize ModelOutputs")
    void serdeModelOutputs() throws JsonProcessingException {

        PortValue<Integer> portValue = GeneratorModel.generatorOutputPort.createPortValue(1);
        ModelOutputMessage<?> modelOutputMessage = ModelOutputMessage.builder()
                .modelOutput(Bag.builder().addPortValueList(portValue).build())
                .nextTime(one)
                .time(zero)
                .sender(GeneratorModel.identifier)
                .build();

        DevsMessage devsMessage = modelOutputMessage;
        //String generatorOutputJson = objectMapper.writeValueAsString(devsMessage);
        byte[] bytes = serialization.serialize(devsMessage).get();
        //DevsMessage deserialized = objectMapper.readValue(generatorOutputJson, DevsMessage.class);
        DevsMessage deserialized = deserialize(devsMessage, bytes);
        assert (deserialized instanceof ModelOutputMessage<?>);
        ModelOutputMessage<LongSimTime> desModelOutputMessage = (ModelOutputMessage<LongSimTime>) deserialized;
        assert (desModelOutputMessage.getTime().getT() == 0L);
        assert (desModelOutputMessage.getNextTime().getT() == 1L);
        Bag desGeneratorOutput = desModelOutputMessage.getModelOutput();
        assert ((Integer)desGeneratorOutput.getPortValueList().get(0).getValue() == 1);
    }

    @Test
    @DisplayName("Serialize deserialize NextTime")
    void serdeNextTimeMessage() throws JsonProcessingException {
        NextTime<SimTime> nextTime = NextTime.builder().time(one).sender(GeneratorModel.identifier).build();
        DevsMessage devsMessage = nextTime;
        //String json = objectMapper.writeValueAsString(devsMessage);
        byte[] bytes = serialization.serialize(devsMessage).get();
        // DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
        DevsMessage deserialized = deserialize(devsMessage, bytes);
        assert (deserialized instanceof NextTime<?>);
        NextTime<LongSimTime> desNextTime = (NextTime<LongSimTime>) deserialized;
        assert(desNextTime.getTime().getT() == 1L
                && desNextTime.getSender().compareTo(GeneratorModel.identifier) == 0);
    }

    @Test
    @DisplayName("Serialize deserialize SendOutput")
    void serdeSendOutput() throws JsonProcessingException {
        SendOutput<?> sendOutput = SendOutput.builder().time(zero).build();
        DevsMessage devsMessage = sendOutput;
        //String json = objectMapper.writeValueAsString(devsMessage);
        byte[] bytes = serialization.serialize(devsMessage).get();
        //DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
        DevsMessage deserialized = deserialize(devsMessage, bytes);
        assert (deserialized instanceof SendOutput<?>);
        SendOutput<LongSimTime> desSendOutput = (SendOutput<LongSimTime>) deserialized;
        assert(desSendOutput.getTime().getT() == 0L);
    }

    @Test
    @DisplayName("Serialize deserialize SimulationDone")
    void serdeSimulationDone() throws JsonProcessingException {
        SimulationDone<?> simulationDone = SimulationDone.builder().time(zero).build();
        DevsMessage devsMessage = simulationDone;
        //String json = objectMapper.writeValueAsString(devsMessage);
        byte[] bytes = serialization.serialize(devsMessage).get();
        //DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
        DevsMessage deserialized = deserialize(devsMessage, bytes);
        assert (deserialized instanceof SimulationDone<?>);
        SimulationDone<LongSimTime> desSimulationDone = (SimulationDone<LongSimTime>) deserialized;
        assert(desSimulationDone.getTime().getT() == 0L);
    }

    @Test
    @DisplayName("Serialize deserialize TransitionDone")
    void serdeTransitionDone() throws JsonProcessingException {
        TransitionDone<?> transitionDone = TransitionDone.builder()
                .nextTime(one)
                .time(zero)
                .sender(GeneratorModel.identifier)
                .build();
        DevsMessage devsMessage = transitionDone;
        // String json = objectMapper.writeValueAsString(devsMessage);
        // DevsMessage deserialized = objectMapper.readValue(json, DevsMessage.class);
        byte[] bytes = serialization.serialize(devsMessage).get();
        DevsMessage deserialized = deserialize(devsMessage, bytes);
        assert (deserialized instanceof TransitionDone<?>);
        TransitionDone<LongSimTime> desTransitionDone = (TransitionDone<LongSimTime>) deserialized;
        assert(desTransitionDone.getTime().getT() == 0L);
        assert(desTransitionDone.getNextTime().getT() == 1L);
        assert(desTransitionDone.getSender().compareTo(GeneratorModel.identifier) == 0);
    }

    @Test
    @DisplayName("Write example messages")
    void writeExampleMessages() throws JsonProcessingException, FileNotFoundException {
        //objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        PrintWriter pw = new PrintWriter("data/example-messages.json");
        pw.println("[");

        InitSim<?> initSim =InitSim.builder().time(zero).build();
        String json = serialize(initSim);// objectMapper.writeValueAsString(initSim);
        pw.print(json);
        pw.println(",");

        ExecuteTransition<SimTime> executeTransition =
                ExecuteTransition.builder().time(zero).modelInputsOption(
                        Optional.of(Bag.builder()
                                .addPortValueList(StorageModel.storageInputPort.createPortValue(1)).build())).build();
        json = serialize(executeTransition);//objectMapper.writeValueAsString(executeTransition);
        pw.print(json);
        pw.println(",");

        ModelDone<?> modelDone = ModelDone.builder().time(zero).sender(GeneratorModel.identifier).build();
        json = serialize(modelDone); // objectMapper.writeValueAsString(modelDone);
        pw.print(json);
        pw.println(",");

        ModelOutputMessage<?> modelOutputMessage = ModelOutputMessage.builder()
                .modelOutput(Bag.builder().addPortValueList(StorageModel.storageOutputPort
                        .createPortValue(StorageStateEnum.S1)).build())
                .nextTime(zero)
                .time(one)
                .sender(GeneratorModel.identifier)
                .build();

        json = serialize(modelOutputMessage); // objectMapper.writeValueAsString(modelOutputMessage);
        pw.print(json);
        pw.println(",");

        NextTime<SimTime> nextTime = NextTime.builder().time(one).sender(GeneratorModel.identifier).build();
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

        TransitionDone<?> transitionDone = TransitionDone.builder()
                .nextTime(one)
                .time(zero)
                .sender(GeneratorModel.identifier)
                .build();
        json = serialize(transitionDone); // objectMapper.writeValueAsString(transitionDone);
        pw.println(json);

        pw.println("]");
        pw.close();
    }

}
