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

package example.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.Bag;
import devs.msg.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class StorageModelTest {
  ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();


  @Test
  @DisplayName("Test generation of values")
  void outputTest() throws JsonProcessingException {

    StorageState iState = new StorageState(StorageStateEnum.S0);
    String iStateJson = objectMapper.writeValueAsString(iState);
    StorageState initialState = objectMapper.readValue(iStateJson, StorageState.class);
    StorageModel storageModel = new StorageModel(initialState);
    // Output should be the initial state of 0
    assert(storageModel.outputFunction().getPortValueList().get(0).getValue() == StorageStateEnum.S0);
  }

  @Test
  @DisplayName("Test state transition")
  void stateTransitionTest() throws JsonProcessingException {

    StorageState iState = new StorageState(StorageStateEnum.S0);
    String iStateJson = objectMapper.writeValueAsString(iState);
    StorageState initialState = objectMapper.readValue(iStateJson, StorageState.class);
    StorageModel storageModel = new StorageModel(initialState);

    // Internal state transition does not change state
    storageModel.internalStateTransitionFunction(LongSimTime.builder().t(1L).build());
    Bag o1 = storageModel.outputFunction();
    String oJson = objectMapper.writeValueAsString(o1);
    Bag output = objectMapper.readValue(oJson, Bag.class);
    // Enum has serialized to a JSON string
    assert(output.getPortValueList().get(0).getValue().equals(StorageStateEnum.S0));

    // External state transition function changes state value to match input
    // and time advance will yield current time

    storageModel.externalStateTransitionFunction(LongSimTime.builder().t(2L).build(),
        Bag.builder().addPortValueList(StorageModel.storageInputPort.createPortValue(0)).build());
    assert(storageModel.timeAdvanceFunction(LongSimTime.builder().t(2L).build()).getT() == 2L);
    assert(storageModel.outputFunction().getPortValueList().get(0).getValue() == StorageStateEnum.S0);

    // After internal state transition, time advance will be max long
    storageModel.internalStateTransitionFunction(LongSimTime.builder().t(3L).build());
    assert(storageModel.timeAdvanceFunction(LongSimTime.builder().t(3L).build()).getT() == Long.MAX_VALUE);

    storageModel.externalStateTransitionFunction(LongSimTime.builder().t(3L).build(),
        Bag.builder().addPortValueList(StorageModel.storageInputPort.createPortValue(1)).build());
    Bag storageOutput = storageModel.outputFunction();
    assert(storageOutput.getPortValueList().get(0).getValue() == StorageStateEnum.S1);

  }

  @Test
  @DisplayName("Test time advance")
  void timeAdvanceTest() {
    StorageState iState = new StorageState(StorageStateEnum.S0);
    StorageModel storageModel = new StorageModel(iState);
    assert (storageModel.timeAdvanceFunction(LongSimTime.builder().t(0L).build()).getT() == Long.MAX_VALUE);
  }
}
