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

package devs.experimentalframe;


import devs.Port;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The GeneratorTest class contains unit tests for verifying the functionality of a generator which
 * is expected to output values and perform state transitions according to specific rules. The tests
 * validate that the generator behaves as expected at various simulation times.
 * <p>
 * Key functionalities tested include: - Correct computation of the time advance function according
 * to simulation time. - Proper output generation, associating each power of two with a
 * corresponding numeric value and a descriptive word. - Valid internal state transitions, ensuring
 * outputs are cleared after a transition.
 * <p>
 * This class leverages JUnit for testing, utilizing annotations such as @Test and @DisplayName.
 * Assertions and test logic ensure that the generator conforms to the expected behavior.
 */
public class GeneratorTest {

  /**
   * Represents a generator instance used in testing simulation scenarios. This generator is
   * responsible for producing outputs and computing state transitions based on specific logic tied
   * to simulation times.
   * <p>
   * The generator adheres to particular rules for its behavior: - Computes time advance for
   * simulation time progression. - Outputs associated values (e.g., a power of two and
   * corresponding descriptive word) when its output function is invoked. - Ensures proper internal
   * state transitions and output clearance after each transition.
   * <p>
   * In the context of the GeneratorTest class, this generator is tested to ensure its correctness
   * in producing outputs and managing state transitions at different simulation times.
   */
  private final Generator<LongSimTime> generator;

  /**
   * Constructs a new instance of the GeneratorTest class.
   * <p>
   * This constructor initializes the `generator` field with an instance of PowerOfTwoGenerator,
   * which is responsible for producing a predefined schedule of values based on powers of two
   * logic. The `PowerOfTwoGenerator` is used for automated testing and simulation purposes, serving
   * as the data source in the experimental framework.
   */
  public GeneratorTest() {
    generator = new PowerOfTwoGenerator();
  }

  /**
   * Verifies the behavior of the `generator`'s time advance, output, and internal state transition
   * functions based on a predefined schedule of simulation times using powers of two.
   * <p>
   * This test checks the following: - Ensures the `timeAdvanceFunction` correctly progresses time,
   * starting from `t=0` to subsequent powers of two (1, 2, 4, 8). - Confirms that the
   * `outputFunction` generates expected output values for each scheduled time step. This includes:
   * - An integer output matching the current simulation time. - A word output corresponding to the
   * integer, using a predefined mapping: - 1 -> "One" - 2 -> "Two" - 4 -> "Four" - 8 -> "Eight" -
   * Defaults to "N/A" for other values (though not expected in this test). - Ensures that invoking
   * the `internalStateTransitionFunction` clears the generator's outputs as intended after each
   * internal transition is executed.
   * <p>
   * It is a unit test to validate the internal logic of the `generator` component in an
   * experimental simulation framework, specifically for cases where time progresses based on powers
   * of two.
   */
  @Test
  @DisplayName("Test Generator for experimental frame.")
  void testGenerator() {

    // First scheduled event is for t1
    LongSimTime t0 = LongSimTime.builder().t(0L).build();
    LongSimTime t1 = LongSimTime.builder().t(1L).build();
    assert (generator.timeAdvanceFunction(t0).equals(t1));

    // For each power of two, generator should output an integer and the word
    for (int e = 0; e <= 3; e++) {
      long t = (long) Math.pow(2.0, e);

      System.out.println("Testing generator at t = " + t);
      // Time advance should be zero
      LongSimTime simTime = LongSimTime.builder().t(t).build();
      LongSimTime timeAdvance = generator.timeAdvanceFunction(simTime);
      assert (timeAdvance.equals(t0));
      // Test the output
      for (PortValue<?> pv : generator.outputFunction().getPortValueList()) {
        if ("number".equals(pv.getPortIdentifier())) {
          Port<Integer> integerPort = (Port<Integer>) generator.getPorts().get("number");
          Integer i = integerPort.getValue(pv);
          assert (i == t);
        } else if ("words".equals(pv.getPortIdentifier())) {
          Port<String> wordPort = (Port<String>) generator.getPorts().get("words");
          String word = wordPort.getValue(pv);
          String expectedWord = switch ((int) t) {
            case 1 -> "One";
            case 2 -> "Two";
            case 4 -> "Four";
            case 8 -> "Eight";
            default -> "N/A";
          };
          assert (word.equals(expectedWord));
        }
      }

      // Internal transition will clear outputs
      generator.internalStateTransitionFunction(simTime);
    }


  }


}
