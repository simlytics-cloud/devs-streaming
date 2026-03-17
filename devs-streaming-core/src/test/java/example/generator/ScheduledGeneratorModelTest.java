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

package example.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import devs.utils.DevsObjectMapper;

import java.util.ArrayList;
import java.util.List;
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
public class ScheduledGeneratorModelTest {
  

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
    // Schedule is in initial state of 0 with a FlipState scheduled for t=0
    ScheduledGeneratorModel generatorModel = new ScheduledGeneratorModel(0, "generator");
    LongSimTime nextTime = generatorModel.timeAdvanceFunction();
    assert nextTime.getT() == 1L;

    // Output should be 0 before state transition
    List<PortValue<?>> output = generatorModel.outputFunction();
    assert ((Integer) output.get(0).getValue() == 0);

    // Execute the internal transition
    generatorModel.internalStateTransitionFunction();
    assert generatorModel.getModelState().getiState() == 1;
    nextTime = nextTime.plus(generatorModel.timeAdvanceFunction());
    // Time advance should be 0 it internal state is 1
    assert nextTime.getT() == 1L;

    // Output should be 1 after state transition
    output = generatorModel.outputFunction();
    assert ((Integer) output.get(0).getValue() == 1);


    // Should flip state to 0L
    generatorModel.internalStateTransitionFunction();
    assert generatorModel.getModelState().getiState() == 0;
    nextTime = nextTime.plus(generatorModel.timeAdvanceFunction());
    assert nextTime.getT() == 2L;
    // Output should be 0 after second state transition
    assert ((Integer) generatorModel.outputFunction().get(0).getValue() == 0);
  }


}
