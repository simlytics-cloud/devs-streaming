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

package devs;

import devs.msg.Bag;
import devs.msg.PortValue;

import java.util.*;

public class PDevsCouplings {

  private final List<InputCouplingHandler> inputHandlers;
  private final List<OutputCouplingHandler> outputHandlers;

  public PDevsCouplings(List<InputCouplingHandler> inputHandlers,
      List<OutputCouplingHandler> outputHandlers) {
    this.inputHandlers = inputHandlers;
    this.outputHandlers = outputHandlers;
  }

  public OutputCouplingMessages handleOutputBag(
      Map<String, Optional<Bag>> outputMap) {
    Map<String, List<PortValue<?>>> receiverMap = new HashMap<>();
    List<PortValue<?>> outputMessages = new ArrayList<>();

    for (OutputCouplingHandler outputHandler: outputHandlers) {
      outputHandler.handleOutputs(outputMap, receiverMap, outputMessages);
    }

    // Create input bags for all of the internal messages
    Map<String, Bag> internalMessages = new HashMap<>();
    for (String key: receiverMap.keySet()) {
      List<PortValue<?>> portValues = receiverMap.get(key);
      internalMessages.put(key, Bag.builder().addAllPortValueList(portValues).build());
    }

    // Create an output bag because this coupling does not product output messages
    Bag outputBag = Bag.builder().addAllPortValueList(outputMessages).build();
    return new OutputCouplingMessages(outputBag, internalMessages);
  }

  public Map<String, Bag> handleInputMessage(Bag modelInput) {
    Map<String, List<PortValue<?>>> receiverMap = new HashMap<>();

    for (InputCouplingHandler inputHandler: inputHandlers) {
      inputHandler.handleInputs(modelInput, receiverMap);
    }

    // Create input bags for all of the internal messages
    Map<String, Bag> internalMessages = new HashMap<>();
    for (String key: receiverMap.keySet()) {
      List<PortValue<?>> portValues = receiverMap.get(key);
      internalMessages.put(key, Bag.builder().addAllPortValueList(portValues).build());
    }
    return internalMessages;
  }
}
