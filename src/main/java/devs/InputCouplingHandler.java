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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class InputCouplingHandler {

  private final Optional<Class<?>> classFilter;

  public InputCouplingHandler(Optional<Class<?>> classFilter) {
    this.classFilter = classFilter;
  }

  public void handleInputs(Bag inputs, Map<String,
      List<PortValue<?>>> receiverMap) {
    for (PortValue<?> portValue : inputs.getPortValueList()) {
      if (classFilter.isPresent() && !classFilter.get().isInstance(portValue.getValue())) {
        // do nothing
      } else {
        handlePortValue(portValue, receiverMap);
      }
    }
  }

  public abstract void handlePortValue(PortValue<?> portValue, Map<String,
      List<PortValue<?>>> receiverMap);

  protected void addInputPortValue(PortValue<?> portValue, String receiver, Map<String,
          List<PortValue<?>>> receiverMap) {
    if (receiverMap.containsKey(receiver)) {
      receiverMap.get(receiver).add(portValue);
    } else {
      List<PortValue<?>> portValues = new ArrayList<>();
      portValues.add(portValue);
      receiverMap.put(receiver, portValues);
    }
  }
}
