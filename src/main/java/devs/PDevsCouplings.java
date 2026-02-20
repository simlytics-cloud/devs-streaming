/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and 
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

import devs.iso.PortValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A unified coupling handler using a predefined routing map.
 * <p>
 * Internally, it stores routes as a mapping from (sender, outputPort) to a map of
 * (receiver -> receiverPort). When a message comes from a sender/port, it is routed to
 * zero or more (receiver, receiverPort) pairs.
 * <p>
 * Conventions:
 * - Use the special sender key {@link #COUPLED_SENDER} for inputs arriving to the coupled model
 *   from the environment (i.e., for {@link #handleInputs(List, Map)} and
 *   {@link #handleInputMessage(List)}).
 * - Use the special receiver key {@link #COUPLED_OUTPUT} to indicate an external output of the
 *   coupled model (i.e., add to the {@code outputMessages} list in
 *   {@link #handleOutputs(Map, Map, List)} and {@link #handleOutputBag(Map)}).
 */
public class PDevsCouplings {

  /** Special sender identifier used for messages entering the coupled model. */
  public static final String COUPLED_SENDER = "$COUPLED";
  /** Special receiver identifier used for messages that should become external outputs. */
  public static final String COUPLED_OUTPUT = "$OUTPUT";

  /**
   * Represents a single connection from a sender's output port to a receiver's input port.
   *
   * @param sender identifier for the model emitting the message
   * @param senderPort identifier for the output port on the sender
   * @param receiver identifier for the model receiving the message
   * @param receiverPort identifier for the input port on the receiver
   */
  public record Connection(String sender, String senderPort, String receiver, String receiverPort) {}

  /**
   * A builder for {@link PDevsCouplings} that allows for incremental construction of the routing
   * map from {@link Connection} instances.
   */
  public static class Builder {

    private final List<Connection> connections = new ArrayList<>();

    /**
     * Adds a single {@link Connection} to the builder.
     *
     * @param connection the connection to add
     * @return this builder instance
     */
    public Builder addConnection(Connection connection) {
      connections.add(connection);
      return this;
    }

    /**
     * Adds a connection from sender:port to receiver:port to the builder.
     *
     * @param sender identifier for the model emitting the message
     * @param senderPort identifier for the output port on the sender
     * @param receiver identifier for the model receiving the message
     * @param receiverPort identifier for the input port on the receiver
     * @return this builder instance
     */
    public Builder addConnection(String sender, String senderPort, String receiver,
        String receiverPort) {
      connections.add(new Connection(sender, senderPort, receiver, receiverPort));
      return this;
    }

    /**
     * Adds multiple {@link Connection} instances to the builder.
     *
     * @param connections a list of connections to add
     * @return this builder instance
     */
    public Builder addConnections(List<Connection> connections) {
      this.connections.addAll(connections);
      return this;
    }

    /**
     * Builds a {@link PDevsCouplings} instance from the collected connections.
     *
     * @return a new PDevsCouplings instance
     */
    public PDevsCouplings build() {
      Map<String, Map<String, Map<String, String>>> routes = new HashMap<>();

      for (Connection conn : connections) {
        routes.computeIfAbsent(conn.sender(), s -> new HashMap<>())
            .computeIfAbsent(conn.senderPort(), sp -> new HashMap<>())
            .put(conn.receiver(), conn.receiverPort());
      }

      return new PDevsCouplings(routes);
    }
  }

  /**
   * Returns a new {@link Builder} instance.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * The routing map: sender -> outputPort -> (receiver -> receiverPort)
   */
  private final Map<String, Map<String, Map<String, String>>> routes;

  /**
   * Construct a new PDevsCouplings with explicit routing information.
   *
   * @param routes mapping of sender -> outputPort -> (receiver -> receiverPort)
   */
  public PDevsCouplings(Map<String, Map<String, Map<String, String>>> routes) {
    this.routes = routes;
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
  public OutputCouplingMessages handleOutputBag(Map<String, Optional<List<PortValue<?>>>> outputMap) {
    Map<String, List<PortValue<?>>> receiverMap = new HashMap<>();
    List<PortValue<?>> outputMessages = new ArrayList<>();

    handleOutputs(outputMap, receiverMap, outputMessages);

    // Create input lists for all of the internal messages
    Map<String, List<PortValue<?>>> internalMessages = new HashMap<>();
    for (String key : receiverMap.keySet()) {
      List<PortValue<?>> portValues = receiverMap.get(key);
      internalMessages.put(key, portValues);
    }

    // Create an output list because this coupling does not product output messages
    return new OutputCouplingMessages(outputMessages, internalMessages);
  }

  /**
   * Processes the input message for a Parallel DEVS (P-DEVS) coupling system by routing it to
   * appropriate handlers and constructing internal message bags for further processing.
   *
   * @param modelInput A Bag containing the model input messages to be processed.
   * @return A map where the keys are identifiers for target models or components, and the values
   * are Bags containing processed input messages intended for those targets.
   */
  public Map<String, List<PortValue<?>>> handleInputMessage(List<PortValue<?>> modelInput) {
    Map<String, List<PortValue<?>>> receiverMap = new HashMap<>();

    handleInputs(modelInput, receiverMap);

    // Create input bags for all of the internal messages
    Map<String, List<PortValue<?>>> internalMessages = new HashMap<>();
    for (String key : receiverMap.keySet()) {
      List<PortValue<?>> portValues = receiverMap.get(key);
      internalMessages.put(key, portValues);
    }
    return internalMessages;
  }

  /**
   * Handles the input bag coming to the coupled model by routing based on the
   * {@link #routes} using {@link #COUPLED_SENDER} as the sender key.
   *
   * @param inputs the input bag sent to the coupled model
   * @param receiverMap a map of model identifiers and the {@link PortValue}s routed to those models
   */
  public void handleInputs(List<PortValue<?>> inputs, Map<String, List<PortValue<?>>> receiverMap) {
    if (inputs == null || inputs.isEmpty()) {
      return;
    }
    Map<String, Map<String, String>> byPort = routes.get(COUPLED_SENDER);
    if (byPort == null) {
      return;
    }
    for (PortValue<?> portValue : inputs) {
      Map<String, String> destinations = byPort.get(portValue.getPortName());
      if (destinations == null || destinations.isEmpty()) {
        continue;
      }
      for (Map.Entry<String, String> dest : destinations.entrySet()) {
        String receiver = dest.getKey();
        String receiverPort = dest.getValue();
        PortValue<?> mapped = withPortName(portValue, receiverPort);
        addInputPortValue(mapped, receiver, receiverMap);
      }
    }
  }

  /**
   * Processes the outputs of models and routes port values to their corresponding receivers or
   * coupled model output messages, based on {@link #routes}.
   *
   * @param modelOutputs A map where the key represents the sender identifier and the value is an
   *                     optional list containing port values emitted by the sender.
   * @param receiverMap A map where the key represents the receiver identifier and the value is a
   *                    list of port values intended for that receiver.
   * @param outputMessages A list to which processed port values destined for the coupled model
   *                       outputs are appended.
   */
  public void handleOutputs(Map<String, Optional<List<PortValue<?>>>> modelOutputs,
                            Map<String, List<PortValue<?>>> receiverMap,
                            List<PortValue<?>> outputMessages) {
    if (modelOutputs == null || modelOutputs.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Optional<List<PortValue<?>>>> entry : modelOutputs.entrySet()) {
      String sender = entry.getKey();
      Optional<List<PortValue<?>>> maybe = entry.getValue();
      if (maybe.isEmpty()) {
        continue;
      }
      Map<String, Map<String, String>> byPort = routes.get(sender);
      if (byPort == null || byPort.isEmpty()) {
        continue;
      }
      for (PortValue<?> portValue : maybe.get()) {
        Map<String, String> destinations = byPort.get(portValue.getPortName());
        if (destinations == null || destinations.isEmpty()) {
          continue;
        }
        for (Map.Entry<String, String> dest : destinations.entrySet()) {
          String receiver = dest.getKey();
          String receiverPort = dest.getValue();
          PortValue<?> mapped = withPortName(portValue, receiverPort);
          if (COUPLED_OUTPUT.equals(receiver)) {
            outputMessages.add(mapped);
          } else {
            addInputPortValue(mapped, receiver, receiverMap);
          }
        }
      }
    }
  }

  /** Utility method to add a {@link PortValue} to the receiverMap. */
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> PortValue<T> withPortName(PortValue<?> original, String newPortName) {
    // Safe by construction: only the port name changes; value type remains the same
    return ((PortValue) original).withPortName(newPortName);
  }
}
