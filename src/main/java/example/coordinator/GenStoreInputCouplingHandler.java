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

package example.coordinator;

import devs.InputCouplingHandler;
import devs.msg.PortValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code GenStoreInputCouplingHandler} class is a concrete implementation of the
 * {@link InputCouplingHandler} designed specifically for scenarios where a GenStore coordinator
 * requires no input handling. This handler overrides the {@code handlePortValue} method to provide
 * a specialized implementation that does not process any incoming port values.
 * <p>
 * This class is part of a DEVS model coordination system and is utilized to manage input couplings
 * in a way that facilitates the correct routing of input messages between models. For this specific
 * case, it explicitly prevents any input processing for the GenStore coordinator.
 */
public class GenStoreInputCouplingHandler extends InputCouplingHandler {

  /**
   * Constructs a new {@code GenStoreInputCouplingHandler} with no class filter applied.
   * <p>
   * This handler is specifically designed to manage input couplings for a GenStore coordinator in a
   * DEVS model coordination system. In this case, the constructor initializes the handler to not
   * apply any specific input handling, by passing an empty optional as the class filter.
   * <p>
   * The {@code GenStoreInputCouplingHandler} serves a specialized purpose where the GenStore
   * coordinator does not process incoming port values, ensuring no input messages are routed or
   * processed. This behavior is part of the system's design when dealing with specific scenarios
   * for this coordinator type.
   */
  public GenStoreInputCouplingHandler() {
    super(Optional.empty());
  }

  /**
   * Handles input port values for the GenStore coordinator. This implementation does not process
   * any incoming port values, as the GenStore coordinator does not require input handling.
   *
   * @param portValue   the {@link PortValue} to handle, representing the data sent to an input
   *                    port.
   * @param receiverMap a map where the keys are model identifiers and the values are lists of
   *                    {@link PortValue} instances to be routed to the corresponding models.
   */
  @Override
  public void handlePortValue(PortValue<?> portValue, Map<String, List<PortValue<?>>> receiverMap) {
    // GenStore coordinator gets no input
  }
}
