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

@DisplayName("Generator Model Test")
public class GeneratorModelTest {


  ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();


  @Test
  @DisplayName("Test generation of values")
  void outputTest() throws JsonProcessingException {

    GeneratorModel generatorModel = new GeneratorModel(0);
    // Output should be the initial state of 0
    assert ((Integer) generatorModel.outputFunction().getPortValueList().get(0).getValue() == 0);
  }

  @Test
  @DisplayName("Test state transition")
  void stateTransitionTest() throws JsonProcessingException {

    GeneratorModel generatorModel = new GeneratorModel(0);
    generatorModel.internalStateTransitionFunction(LongSimTime.builder().t(1L).build());
    // Output should be 1 after state transition
    Bag o = generatorModel.outputFunction();
    String oJson = objectMapper.writeValueAsString(o);
    Bag output = objectMapper.readValue(oJson, Bag.class);
    assert ((Integer) output.getPortValueList().get(0).getValue() == 1);

    generatorModel.internalStateTransitionFunction(LongSimTime.builder().t(2L).build());
    // Output should be 0 after second state transition
    assert ((Integer) generatorModel.outputFunction().getPortValueList().get(0).getValue() == 0);
  }

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
