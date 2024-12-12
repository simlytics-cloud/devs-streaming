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

package devs;

import devs.msg.Bag;
import devs.msg.PortValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class OutputCouplingHandler {

  private final Optional<Class<?>> classFilter;
  private final Optional<String> senderFilter;
  private final Optional<String> portIdentifierFilter;

  public OutputCouplingHandler(Optional<Class<?>> classFilter, Optional<String> senderFilter,
                               Optional<String> portIdentifierFilter) {
    this.classFilter = classFilter;
    this.senderFilter = senderFilter;
    this.portIdentifierFilter = portIdentifierFilter;
  }

  public void handleOutputs(Map<String, Optional<Bag>> modelOutputs,
                            Map<String, List<PortValue<?>>> receiverMap, List<PortValue<?>> outputMessages) {
    for (Map.Entry<String, Optional<Bag>> entry : modelOutputs.entrySet()) {
      if (entry.getValue().isPresent()) {
        String sender = entry.getKey();
        Bag bag = entry.getValue().get();
        for (PortValue<?> portValue : bag.getPortValueList()) {
          if (filterEntry(sender, portValue)) {
            handlePortValue(sender, portValue, receiverMap, outputMessages);
          }
        }
      }

    }
  }

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

  private boolean filterEntry(String senderIdentifier, PortValue<?> portValue) {
    if (senderFilter.isPresent() && !senderIdentifier.equals(senderFilter.get())) {
      return false;
    }
    if (portIdentifierFilter.isPresent()
        && !portValue.getPortIdentifier().equals(portIdentifierFilter.get())) {
      return false;
    }
    if (classFilter.isPresent() && !classFilter.get().isInstance(portValue.getValue())) {
      return false;
    }
    return true;
  }

  public abstract void handlePortValue(String sender, PortValue<?> portValue,
                                       Map<String, List<PortValue<?>>> receiverMap, List<PortValue<?>> outputMessages);
}
