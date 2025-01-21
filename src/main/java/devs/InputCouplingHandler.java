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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class handles input couplings.  It routes messages from the input ports of a
 * {@link devs.PDevsCoordinator} to the appropriate input ports of subordinate models.  It
 * optionally includes a class filter that will cause this handler to only process messages of a
 * certain class.  When a {@link PDevsCoordinator} processes
 */
public abstract class InputCouplingHandler {

  private final Optional<Class<?>> classFilter;

  protected InputCouplingHandler(Optional<Class<?>> classFilter) {
    this.classFilter = classFilter;
  }

  /**
   * Handles the input bag coming to the coupled model.
   *
   * @param inputs      the input bag sent to the coupled model
   * @param receiverMap a map of model identifiers and the {@link devs.msg.PortValue}s routed to
   *                    those models.
   */
  public void handleInputs(Bag inputs, Map<String, List<PortValue<?>>> receiverMap) {
    for (PortValue<?> portValue : inputs.getPortValueList()) {
      if (classFilter.isPresent() && !classFilter.get().isInstance(portValue.getValue())) {
        // do nothing
      } else {
        handlePortValue(portValue, receiverMap);
      }
    }
  }

  /**
   * This method should be overridden to specify handling on inputs.
   *
   * @param portValue   the {@link devs.msg.PortValue} to handle
   * @param receiverMap a map of model identifiers and the {@link devs.msg.PortValue}s routed to
   *                    those models.
   */
  public abstract void handlePortValue(PortValue<?> portValue,
                                       Map<String, List<PortValue<?>>> receiverMap);

  /**
   * A utility method to add a {@link devs.msg.PortValue} the the recieverMap.
   *
   * @param portValue   the PortValue to add
   * @param receiver    the model identifier for the receiver model
   * @param receiverMap a map of model identifiers and the {@link devs.msg.PortValue}s routed to
   *                    those models.
   */
  protected void addInputPortValue(PortValue<?> portValue, String receiver,
                                   Map<String, List<PortValue<?>>> receiverMap) {
    if (receiverMap.containsKey(receiver)) {
      receiverMap.get(receiver).add(portValue);
    } else {
      List<PortValue<?>> portValues = new ArrayList<>();
      portValues.add(portValue);
      receiverMap.put(receiver, portValues);
    }
  }
}
