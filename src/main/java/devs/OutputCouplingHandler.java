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
 * An abstract class representing an output coupling handler for processing model outputs. It is
 * used by a PDevsCoordinator to process model outputs and route them to the appropriate subordinate
 * model input ports or coupled model output ports.  It optionally has filters to process model
 * outputs based on output class, sender of the output, or port identifier.
 */
public abstract class OutputCouplingHandler {

  /**
   * Represents an optional filter for specific classes.
   * <p>
   * This filter is used in the {@link OutputCouplingHandler} class to selectively process port
   * values based on their class type.
   */
  private final Optional<Class<?>> classFilter;
  /**
   * Represents an optional filter for specific sender identifiers.
   * <p>
   * The senderFilter is used in the OutputCouplingHandler class to selectively process port values
   * based on the sender of the message.
   */
  private final Optional<String> senderFilter;
  /**
   * Represents an optional filter for specific port identifiers.
   */
  private final Optional<String> portIdentifierFilter;

  /**
   * Constructs an OutputCouplingHandler with optional filters for specific classes, sender
   * identifiers, and port identifiers.
   *
   * @param classFilter          An optional filter for specific classes.
   * @param senderFilter         An optional filter for specific sender identifiers.
   * @param portIdentifierFilter An optional filter for specific port identifiers.
   */
  public OutputCouplingHandler(Optional<Class<?>> classFilter, Optional<String> senderFilter,
      Optional<String> portIdentifierFilter) {
    this.classFilter = classFilter;
    this.senderFilter = senderFilter;
    this.portIdentifierFilter = portIdentifierFilter;
  }

  /**
   * Processes the outputs of a model and routes port values to their corresponding receivers or
   * output messages, based on defined filtering and handling logic.
   *
   * @param modelOutputs   A map where the key represents the sender identifier and the value is an
   *                       optional Bag containing port values emitted by the sender.
   * @param receiverMap    A map where the key represents the receiver identifier and the value is a
   *                       list of port values intended for that receiver.
   * @param outputMessages A list to which processed port values are appended for further handling
   *                       or dispatching.
   */
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

  /**
   * Adds a given {@link PortValue} to the specified receiver's list of port values in the provided
   * receiver map. If the receiver does not already have an entry in the map, a new entry is
   * created.
   *
   * @param portValue   the {@link PortValue} to be added to the receiver's list
   * @param receiver    the identifier of the receiver to which the port value belongs
   * @param receiverMap a map where each key represents a receiver identifier and each value is a
   *                    list of {@link PortValue} instances targeted for that receiver
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

  /**
   * Filters a {@link PortValue} based on the optional filters for sender identifier, port
   * identifier, and object type defined in the containing class.
   *
   * @param senderIdentifier the identifier of the sender emitting the {@link PortValue}
   * @param portValue        the {@link PortValue} to be evaluated against the filters
   * @return {@code true} if the {@link PortValue} satisfies all the defined filters, {@code false}
   * otherwise
   */
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

  /**
   * Handles a given port value by processing it according to specific logic and routing it to
   * appropriate receivers or appending it to a list of output messages.
   *
   * @param sender         the identifier of the entity emitting the port value
   * @param portValue      the port value to be processed
   * @param receiverMap    a map where each key represents a receiver identifier and each value is a
   *                       list of {@link PortValue} instances targeted for that receiver
   * @param outputMessages a list to which processed port values are appended for further handling
   *                       or forwarding
   */
  public abstract void handlePortValue(String sender, PortValue<?> portValue,
      Map<String, List<PortValue<?>>> receiverMap, List<PortValue<?>> outputMessages);
}
