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

import static org.junit.jupiter.api.Assertions.assertEquals;

import devs.Port;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import java.util.List;

/**
 * TestAcceptor is a class that extends the abstract {@code Acceptor} model and validates incoming
 * events based on their port identifiers and associated values. It represents a model in a DEVS
 * (Discrete Event System Specification) simulation that processes external inputs on specific ports
 * and executes validation checks for correctness.
 * <p>
 * The class expects two types of external inputs: - A numeric value on the "acceptNumber" port,
 * which is validated against a calculated expected value. - A string value on the "acceptWord"
 * port, which is validated against an expected word based on the current simulation time.
 * <p>
 * The {@code externalStateTransitionFunction} is implemented to perform the validation logic for
 * the incoming port values.
 * <p>
 * The validation logic for the "acceptNumber" port ensures: - The numeric value received matches a
 * logarithmic function of the current simulation time, except for time t = 1.
 * <p>
 * The validation logic for the "acceptWord" port ensures: - The string value received matches a
 * predefined expected word based on simulation time steps.
 * <p>
 * Any unexpected port identifier in the input throws an {@code IllegalArgumentException}.
 * <p>
 * This model does not produce any output events, as it inherits this behavior from the
 * {@code Acceptor} base class.
 */
public class TestAcceptor extends Acceptor<LongSimTime, Integer> {

  public static String modelIdentifier = "TestAcceptor";
  /**
   * Default constructor for the {@code TestAcceptor} class.
   * <p>
   * This constructor initializes the state of the {@code TestAcceptor} instance by invoking the
   * constructor of its superclass {@code Acceptor} with an initial state value of {@code 0}.
   */
  public TestAcceptor() {
    super(0, modelIdentifier);
  }

  /**
   * Represents a port used in the {@code TestAcceptor} DEVS model for accepting numeric inputs.
   * <p>
   * This port is identified by the name "acceptNumber" and is specifically associated with the data
   * type {@link Integer}. It functions as a communication interface, allowing the model to receive
   * input events consisting of integer values during a simulation.
   * <p>
   * The {@code acceptNumber} port is expected to process numeric inputs as part of the
   * {@code TestAcceptor} model's validation or simulation logic, aligning with the DEVS framework's
   * port-based input/output communication mechanism.
   */
  public static Port<Integer> acceptNumber = new Port<>("acceptNumber", Integer.class);
  /**
   * Represents a port in the {@code TestAcceptor} class that is expected to receive string-based
   * input values during a DEVS simulation. The port is uniquely identified as "acceptWord" and is
   * specifically associated with handling inputs of type {@code String}.
   * <p>
   * This port plays a role in validating string input events within the simulation framework,
   * ensuring that they meet specific criteria as defined in the {@code TestAcceptor} model.
   */
  public static Port<String> acceptWord = new Port<>("acceptWord", String.class);

  /**
   * Processes external events for the simulation model by evaluating port values received during
   * the simulation and transitioning the state accordingly. This method validates the inputs based
   * on simulation time and specific expected criteria.
   *
   * @param currentTime The current simulation time. This parameter represents the time at which the
   *                    external event occurred.
   * @param inputs      The list containing port values received during this external transition.
   *                    Each port value is processed and validated based on its identifier and
   *                    associated data.
   */
  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, List<PortValue<?>> inputs) {
    for (PortValue<?> pv : inputs) {
      if ("acceptNumber".equals(pv.getPortName())) {
        double d = acceptNumber.getValue(pv);
        System.out.println("Got number " + d + " at " + currentTime);
        double expectedValue = 0.0;
        if (currentTime.getT() != 1) {
          expectedValue = Math.log(currentTime.getT().doubleValue()) / Math.log(2.0);
        }
        assertEquals(expectedValue, d, 0.0000001);
      } else if ("acceptWord".equals(pv.getPortName())) {
        String word = acceptWord.getValue(pv);
        System.out.println("Got word " + word + " at " + currentTime);
        String expectedWord = switch (currentTime.getT().intValue()) {
          case 1 -> "Zero";
          case 2 -> "One";
          case 4 -> "Two";
          case 8 -> "Three";
          default -> "N/A";
        };
        assert (word.equals(expectedWord));
      } else {
        throw new IllegalArgumentException(
            "Test acceptor did not expect port value with identifier " + pv.getPortName());
      }
    }

  }

}
