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

import devs.OutputCouplingHandler;
import devs.iso.PortValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * TestOutputCouplingHandler is a concrete implementation of the OutputCouplingHandler class. It is
 * designed to handle specific port values emitted by a model and route them to their corresponding
 * receivers or output messages by applying custom logic. This class specifically processes port
 * values associated with identifiers like "numbers", "words", or identifiers from the
 * LogBaseTwoCalculatorModel and TestAcceptor models.
 */
public class TestOutputCouplingHandler extends OutputCouplingHandler {

  /**
   * Constructs a new instance of {@code TestOutputCouplingHandler}.
   * <p>
   * This constructor initializes the coupling handler with empty optional filters for class type,
   * sender identifiers, and port identifiers, effectively allowing it to process a wide range of
   * port values without any predefined restrictions. It is specifically tailored for handling port
   * values in the context of the related models (e.g., LogBaseTwoCalculatorModel and
   * TestAcceptor).
   */
  public TestOutputCouplingHandler() {
    super(Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Handles the processing of a given {@code PortValue} object by matching its port identifier to
   * known values and performing specific actions based on the identifier.
   * <p>
   * The method routes the processed port values to the appropriate receivers by adding them to the
   * {@code receiverMap}. Port values that cannot be handled are rejected with an
   * {@code IllegalArgumentException}.
   *
   * @param sender         the unique identifier of the sender emitting the port value
   * @param portValue      the {@code PortValue} instance to be handled, containing the value and
   *                       port identifier
   * @param receiverMap    a map where keys are receiver identifiers and values are lists of port
   *                       values to be routed to respective receivers
   * @param outputMessages a list used to collect any output messages generated during the
   *                       processing of the port value
   * @throws IllegalArgumentException if the port identifier of {@code portValue} cannot be handled
   */
  @Override
  public void handlePortValue(String sender, PortValue<?> portValue,
      Map<String, List<PortValue<?>>> receiverMap, List<PortValue<?>> outputMessages) {
    if ("numbers".equals(portValue.getPortName())) {
      Integer number = LogBaseTwoCalculatorModel.numberIn.getValue(portValue);
      PortValue<?> pv = LogBaseTwoCalculatorModel.numberIn.createPortValue(number);
      addInputPortValue(pv, LogBaseTwoCalculatorModel.MODEL_ID, receiverMap);
    } else if ("words".equals(portValue.getPortName())) {
      String word = LogBaseTwoCalculatorModel.wordIn.getValue(portValue);
      PortValue<?> pv = LogBaseTwoCalculatorModel.wordIn.createPortValue(word);
      addInputPortValue(pv, LogBaseTwoCalculatorModel.MODEL_ID, receiverMap);
    } else if (portValue.getPortName()
        .equals(LogBaseTwoCalculatorModel.numberOut.getPortName())) {
      Integer number = TestAcceptor.acceptNumber.getValue(portValue);
      PortValue<?> pv = TestAcceptor.acceptNumber.createPortValue(number);
      addInputPortValue(pv, TestAcceptor.modelIdentifier, receiverMap);
    } else if (portValue.getPortName()
        .equals(LogBaseTwoCalculatorModel.wordOut.getPortName())) {
      String word = TestAcceptor.acceptWord.getValue(portValue);
      PortValue<?> pv = TestAcceptor.acceptWord.createPortValue(word);
      addInputPortValue(pv, TestAcceptor.modelIdentifier, receiverMap);
    } else {
      throw new IllegalArgumentException(
          "Couplings cannot handle port value with identifier " + portValue.getPortName());
    }

  }


}
