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

import devs.PDEVSModel;
import devs.Port;
import devs.ScheduledDevsModel;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import devs.msg.state.ScheduleState;
import devs.utils.Schedule;
import java.util.List;

/**
 * Model to compute base-2 logarithms for numeric and textual inputs in a discrete event system
 * simulation. Extends the functionality of the ScheduledDevsModel for event-driven modeling with
 * state transitions and scheduled outputs.
 * <p>
 * The model handles two types of input data: 1. Integer input received on the `numberIn` port, for
 * which it computes the base-2 logarithm. 2. String input received on the `wordIn` port,
 * representing enumerated values used for determining corresponding logarithmic output text.
 * <p>
 * The model generates outputs on corresponding ports: - Computed integer output for logarithmic
 * values on the `numberOut` port. - Computed string-based output of logarithmic text on the
 * `wordOut` port.
 */
public class LogBaseTwoCalculatorModel extends ScheduledDevsModel<LongSimTime, ScheduleState<LongSimTime>> {

  /**
   * Represents the unique identifier for the model class `LogBaseTwoCalculatorModel`.
   * <p>
   * This constant is used to uniquely identify instances of the `LogBaseTwoCalculatorModel` within
   * the framework, ensuring that the model can be referenced or distinguished when necessary. The
   * identifier is static and final, reflecting its immutability and universal applicability for all
   * instances of the model class.
   */
  public static final String MODEL_ID = "logBaseTwoCalculatorModel";
  /**
   * Represents an input port for receiving integer values in the LogBaseTwoCalculatorModel.
   * <p>
   * This port is used for processing incoming integer data that serves as the input to compute the
   * logarithm base two in the model. The expected behavior of the port is to receive integer values
   * through the external state transition function of the model and process them as part of the
   * overall system behavior.
   */
  public static final Port<Integer> numberIn = new Port<>("numberIn", Integer.class);
  /**
   * Represents the input port for receiving word-based data within the LogBaseTwoCalculatorModel.
   * This port is uniquely identified by the name "wordIn" and is associated with the String data
   * type.
   * <p>
   * It is used to handle external inputs that consist of word representations or related
   * descriptive values, as required by the model's state transition functions.
   */
  public static final Port<String> wordIn = new Port<>("wordIn", String.class);
  /**
   * Represents an output port in the LogBaseTwoCalculatorModel that emits integer values
   * corresponding to the logarithm base two calculations.
   */
  public static final Port<Integer> numberOut = new Port<>("numberOut", Integer.class);
  /**
   * Represents an output port for emitting string-based descriptive words corresponding to a
   * certain numeric value, typically as part of a simulation model's output function. This port is
   * associated with the "wordOut" identifier.
   */
  public static final Port<String> wordOut = new Port<>("wordOut", String.class);
  

  /**
   * Constructs a new instance of the LogBaseTwoCalculatorModel. This model is designed to operate
   * within a DEVS simulation framework and calculates the base-2 logarithm of given inputs. It
   * extends the ScheduledDevsModel and initializes with a null state, a predefined model
   * identifier, and a new schedule for handling events.
   */
  public LogBaseTwoCalculatorModel() {
    super(new ScheduleState<>(LongSimTime.create(0)), MODEL_ID);
  }

  /**
   * Executes the external state transition function of the DEVS model.
   * <p>
   * This method processes external events that arrive at the simulation model, interpreting the
   * input values from the specified ports and generating appropriate outputs. It performs
   * calculations such as computing the base-2 logarithm for numerical inputs and mapping word
   * inputs to predefined outputs. The results are added to the event schedule for further
   * processing or output.
   *
   * @param elapsedTime The time since the last state transition..
   * @param inputs      A list containing external inputs to the model. This parameter
   *                    includes port-value pairs that represent the input events arriving at this
   *                    simulation time.
   */
  @Override
  public void externalStateTransitionFunction(LongSimTime elapsedTime, List<PortValue<?>> inputs) {
    LongSimTime currentTime = modelState.getCurrentTime().plus(elapsedTime);
    modelState.setCurrentTime(currentTime);
    simulator.getContext().getLog().info("Generating roots at {}", currentTime);
    for (PortValue<?> pv : inputs) {
      if (pv.getPortName().equals(numberIn.getPortName())) {
        int number = numberIn.getValue(pv);
        double log = 0.0;
        if (number != 1) {
          log = Math.log(number) / Math.log(2.0);
        }
        System.out.println("Log for " + number + " is " + log);
        Integer outValue = (int) Math.round(log);
        PortValue<?> outPortValue = PortValue.builder()
            .value(outValue)
            .portName(numberOut.getPortName())
            .build();
        modelState.getSchedule().scheduleOutputEvent(currentTime, outPortValue);
      } else if (pv.getPortName().equals(wordIn.getPortName())) {
        String word = wordIn.getValue(pv);
        String outWord = switch (word) {
          case "One" -> "Zero";
          case "Two" -> "One";
          case "Four" -> "Two";
          case "Eight" -> "Three";
          default -> "N/A";
        };
        System.out.println("Log for word " + word + " is " + outWord);
        PortValue<?> outPortValue = PortValue.builder()
            .value(outWord)
            .portName(wordOut.getPortName())
            .build();
        modelState.getSchedule().scheduleOutputEvent(currentTime, outPortValue);
      } else {
        throw new IllegalArgumentException(
            "LogBaseTwoCalculatorModel did not expect port value with identifier"
                + pv.getPortName());
      }
    }

  }

  @Override
  public void handleScheduledEvents(List<Object> events) {
    // No internal events to handle
  }

  /**
   * Executes the confluent state transition function of the DEVS model.
   * <p>
   * This method is invoked when an external event coincides with an internal event at the same
   * simulation time. It sequentially processes both the external and internal state transitions,
   * ensuring proper prioritization and consistency of the model's state.
   *
   * @param inputs      A list containing external inputs to the model. This parameter
   *                    includes port-value pairs representing the input events arriving at this
   *                    simulation time.
   */
  @Override
  public void confluentStateTransitionFunction(List<PortValue<?>> inputs) {
    // The internal state transition function clears outputs from schedule and sets current time
    internalStateTransitionFunction();
    // Time is already set, so call external state transition with a zero elapsed time
    externalStateTransitionFunction(LongSimTime.create(0), inputs);
  }

}
