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

package example.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for verifying the behavior of the StorageModel functionality.
 * <p>
 * This test suite evaluates the output generation, state transitions, and time advance mechanisms
 * of the StorageModel class, using initial states and simulated input/output bags. The tests ensure
 * that the StorageModel behaves as expected under various scenarios.
 */
public class StorageModelTest {

  ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();


  /**
   * Tests the output generation functionality of the {@link StorageModel} class.
   * <p>
   * This test verifies that the initial output of the {@link StorageModel} matches the expected
   * state after being serialized and deserialized using the {@link ObjectMapper}.
   * <p>
   * The test ensures that: - A {@link StorageState} object is correctly serialized to JSON and
   * subsequently deserialized back to a {@link StorageState} instance without data loss. - The
   * {@link StorageModel} correctly initializes with the deserialized state. - The output function
   * of the {@link StorageModel} reflects the expected initial state of
   * {@link StorageStateEnum#S0}.
   *
   * @throws JsonProcessingException if an error occurs during JSON serialization or
   *                                 deserialization
   */
  @Test
  @DisplayName("Test generation of values")
  void outputTest() throws JsonProcessingException {

    StorageState internalState = new StorageState(StorageStateEnum.S0);
    String internalStateJson = objectMapper.writeValueAsString(internalState);
    StorageState initialState = objectMapper.readValue(internalStateJson, StorageState.class);
    StorageModel storageModel = new StorageModel(initialState);
    // Output should be the initial state of 0
    assert (storageModel.outputFunction().get(0)
        .getValue().equals("S0"));
  }

  /**
   * Tests the state transition functionality of the {@link StorageModel} class.
   * <p>
   * This test verifies the behavior of the internal and external state transition functions under
   * different scenarios in the storage model, ensuring consistency between expected and actual
   * outcomes. The test also validates serialization and deserialization processes, time advance
   * calculations, and proper state changes during transitions.
   * <p>
   * Specific validations performed by this test include: - Correct initialization of the
   * {@link StorageModel} with a given initial {@link StorageState}. - Validation that an internal
   * state transition does not change the current state. - Ensuring that the
   * {@link StorageModel#outputFunction()} accurately reflects the most recent state. - Confirmation
   * that external state transitions update the state based on provided input values. - Verification
   * that computed time advances are consistent with expected behavior. - Accurate handling of state
   * transitions when multiple state changes (internal and external) occur sequentially.
   *
   * @throws JsonProcessingException if an error occurs during JSON serialization or deserialization
   *                                 in the test
   */
  @Test
  @DisplayName("Test state transition")
  void stateTransitionTest() throws JsonProcessingException {

    StorageState internalState = new StorageState(StorageStateEnum.S0);
    String internalStateJson = objectMapper.writeValueAsString(internalState);
    StorageState initialState = objectMapper.readValue(internalStateJson, StorageState.class);
    StorageModel storageModel = new StorageModel(initialState);

    // Internal state transition does not change state
    storageModel.internalStateTransitionFunction(LongSimTime.builder().t(1L).build());
    // Enum has serialized to a JSON string
    assert (storageModel.outputFunction().get(0).getValue().equals("S0"));

    // External state transition function changes state value to match input
    // and time advance will yield zero

    storageModel.externalStateTransitionFunction(LongSimTime.builder().t(2L).build(),
        List.of(StorageModel.storageInputPort.createPortValue(0)));
    assert (storageModel.timeAdvanceFunction(LongSimTime.builder().t(2L).build()).getT() == 0L);
    assert (storageModel.outputFunction().get(0)
        .getValue().equals("S0"));

    // After internal state transition, time advance will be max long minus current time
    storageModel.internalStateTransitionFunction(LongSimTime.builder().t(3L).build());
    assert (storageModel.timeAdvanceFunction(LongSimTime.builder().t(3L).build())
        .getT() == Long.MAX_VALUE - 3L);

    storageModel.externalStateTransitionFunction(LongSimTime.builder().t(3L).build(),
        List.of(StorageModel.storageInputPort.createPortValue(1)));
    assert (storageModel.outputFunction().get(0).getValue() == "S1");

  }

  /**
   * Tests the time advance functionality of the {@link StorageModel} class.
   * <p>
   * This test verifies the behavior of the {@code timeAdvanceFunction} method for a storage model
   * initialized with a specific state. The test ensures that the time advance value returned
   * corresponds to the expected behavior of the model in the given state.
   * <p>
   * Key validations performed by this test: - Ensures that initializing the {@link StorageModel}
   * with {@link StorageStateEnum#S0} results in a time advance value of {@code Long.MAX_VALUE}. -
   * Validates the compatibility of the {@link StorageState} and {@link StorageStateEnum} with the
   * {@code timeAdvanceFunction} logic.
   * <p>
   * The test uses an initial state of {@link StorageStateEnum#S0} for the {@link StorageModel} and
   * asserts the correctness of the computed time advance value based on expected outcomes.
   */
  @Test
  @DisplayName("Test time advance")
  void timeAdvanceTest() {
    StorageState internalState = new StorageState(StorageStateEnum.S0);
    StorageModel storageModel = new StorageModel(internalState);
    assert (storageModel.timeAdvanceFunction(LongSimTime.builder().t(0L).build())
        .getT() == Long.MAX_VALUE);
  }
}
