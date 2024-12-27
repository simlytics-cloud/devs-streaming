/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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
 */

package example.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.Bag;
import devs.msg.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test class for testing the behavior of the {@code GeneratorModel} class.
 * <p>
 * This class verifies the correct functionality of value generation, state transition, and
 * time advance mechanisms in the {@code GeneratorModel}. It utilizes the {@code ObjectMapper}
 * provided by the {@code DevsObjectMapper} utility for JSON serialization and deserialization.
 */
@DisplayName("Generator Model Test")
public class GeneratorModelTest {


  ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();


  /**
   * Tests the output generation behavior of the {@code GeneratorModel} class.
   * <p>
   * This test validates that the initial output state of the model correctly reflects
   * its internal state as defined during construction. The method interacts with the
   * {@code outputFunction()} of the {@code GeneratorModel} and asserts that the output
   * produced matches the expected value.
   *
   * @throws JsonProcessingException if there is an error during JSON processing when
   *                                 serializing or deserializing the output of the model.
   */
  @Test
  @DisplayName("Test generation of values")
  void outputTest() throws JsonProcessingException {

    GeneratorModel generatorModel = new GeneratorModel(0);
    // Output should be the initial state of 0
    assert ((Integer) generatorModel.outputFunction().getPortValueList().get(0).getValue() == 0);
  }

  /**
   * Tests the state transition behavior of the {@code GeneratorModel} class.
   *
   * This method validates the internal state transition logic and corresponding output
   * of the {@code GeneratorModel}. It verifies that the internal state changes as
   * expected and the output function reflects the updated state after invoking the
   * internal state transition function.
   *
   * The test performs the following steps:
   * - Initializes a {@code GeneratorModel} instance with an initial state of 0.
   * - Invokes the internal state transition function to trigger a state change.
   * - Validates that the output function correctly reflects the updated state.
   * - Invokes the internal state transition function again and checks the subsequent state
   *   and corresponding output.
   *
   * @throws JsonProcessingException if there is an error during JSON serialization or
   *                                 deserialization when validating the output of the model.
   */
  @Test
  @DisplayName("Test state transition")
  void stateTransitionTest() throws JsonProcessingException {

    GeneratorModel generatorModel = new GeneratorModel(0);
    generatorModel.internalStateTransitionFunction(LongSimTime.builder().t(1L).build());
    // Output should be 1 after state transition
    Bag o = generatorModel.outputFunction();
    String outputJson = objectMapper.writeValueAsString(o);
    Bag output = objectMapper.readValue(outputJson, Bag.class);
    assert ((Integer) output.getPortValueList().get(0).getValue() == 1);

    generatorModel.internalStateTransitionFunction(LongSimTime.builder().t(2L).build());
    // Output should be 0 after second state transition
    assert ((Integer) generatorModel.outputFunction().getPortValueList().get(0).getValue() == 0);
  }

  /**
   * Validates the time advance behavior of the {@code GeneratorModel} class.
   *
   * This test verifies the correctness of the {@code timeAdvanceFunction} implementation in
   * relation to the model's internal state. Specifically, it ensures that:
   * - When the model's internal state is 0, the time advance value is 1.
   * - When the model's internal state is transitioned to 1, the time advance value is also 1.
   *
   * The method involves the following steps:
   * - Initializes a {@code GeneratorModel} instance with an initial state of 0.
   * - Asserts the time advance value is 1 when the initial state is 0.
   * - Invokes the {@code internalStateTransitionFunction} to change the model state to 1.
   * - Asserts the time advance value remains 1 even after the state change.
   *
   * This test ensures that the time advance functionality is consistent with the model's
   * behavior and internal state transitions.
   */
  @Test
  @DisplayName("Test time advance")
  void timeAdvanceTest() {

    GeneratorModel generatorModel = new GeneratorModel(0);
    // Time advance should be 1 if state is 0
    assert (generatorModel.timeAdvanceFunction(LongSimTime.builder().t(0L).build()).getT() == 1L);
    generatorModel.internalStateTransitionFunction(LongSimTime.builder().t(1L).build());
    // time advance should be 0 if state is 1
    assert (generatorModel.timeAdvanceFunction(LongSimTime.builder().t(1L).build()).getT() == 1L);

  }
}
