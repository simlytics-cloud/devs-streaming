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

package devs;

import devs.msg.Bag;
import devs.msg.PortValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the couplings in a Parallel DEVS (P-DEVS) system. This class is responsible for
 * managing the input and output couplings in a coupled model. Couplings are used to route messages
 * between models in the system.
 * <p>
 * The PDevsCouplings class coordinates the input and output couplings by utilizing separate
 * handlers for input and output processing. It provides methods to process output messages and
 * model inputs, routing them to the appropriate internal models or generating external outputs as
 * required.
 */
public class PDevsCouplings {

  private final List<InputCouplingHandler> inputHandlers;
  private final List<OutputCouplingHandler> outputHandlers;

  /**
   * Constructs an instance of the PDevsCouplings class to manage input and output couplings in a
   * Parallel DEVS (P-DEVS) system. The class uses input and output handlers to route messages
   * between input/output ports and models within the system.
   *
   * @param inputHandlers  a list of InputCouplingHandler instances responsible for handling input
   *                       couplings by routing messages to appropriate models.
   * @param outputHandlers a list of OutputCouplingHandler instances responsible for handling output
   *                       couplings by routing messages from models to output ports or other
   *                       targets.
   */
  public PDevsCouplings(List<InputCouplingHandler> inputHandlers,
                        List<OutputCouplingHandler> outputHandlers) {
    this.inputHandlers = inputHandlers;
    this.outputHandlers = outputHandlers;
  }

  /**
   * Handles the output bag by processing the output map, creating internal messages, and
   * constructing an output bag for a Parallel DEVS (P-DEVS) coupling.
   *
   * @param outputMap A map with string keys representing model identifiers and values as Optional
   *                  objects containing Bags of output port values.
   * @return An instance of OutputCouplingMessages which encapsulates the processed output message
   * in a Bag and the internal messages mapped to Bags by their keys.
   */
  public OutputCouplingMessages handleOutputBag(Map<String, Optional<Bag>> outputMap) {
    Map<String, List<PortValue<?>>> receiverMap = new HashMap<>();
    List<PortValue<?>> outputMessages = new ArrayList<>();

    for (OutputCouplingHandler outputHandler : outputHandlers) {
      outputHandler.handleOutputs(outputMap, receiverMap, outputMessages);
    }

    // Create input bags for all of the internal messages
    Map<String, Bag> internalMessages = new HashMap<>();
    for (String key : receiverMap.keySet()) {
      List<PortValue<?>> portValues = receiverMap.get(key);
      internalMessages.put(key, Bag.builder().addAllPortValueList(portValues).build());
    }

    // Create an output bag because this coupling does not product output messages
    Bag outputBag = Bag.builder().addAllPortValueList(outputMessages).build();
    return new OutputCouplingMessages(outputBag, internalMessages);
  }

  /**
   * Processes the input message for a Parallel DEVS (P-DEVS) coupling system by routing it to
   * appropriate handlers and constructing internal message bags for further processing.
   *
   * @param modelInput A Bag containing the model input messages to be processed.
   * @return A map where the keys are identifiers for target models or components, and the values
   * are Bags containing processed input messages intended for those targets.
   */
  public Map<String, Bag> handleInputMessage(Bag modelInput) {
    Map<String, List<PortValue<?>>> receiverMap = new HashMap<>();

    for (InputCouplingHandler inputHandler : inputHandlers) {
      inputHandler.handleInputs(modelInput, receiverMap);
    }

    // Create input bags for all of the internal messages
    Map<String, Bag> internalMessages = new HashMap<>();
    for (String key : receiverMap.keySet()) {
      List<PortValue<?>> portValues = receiverMap.get(key);
      internalMessages.put(key, Bag.builder().addAllPortValueList(portValues).build());
    }
    return internalMessages;
  }
}
