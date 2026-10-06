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

import devs.iso.PortValue;
import java.util.List;
import java.util.Map;


/**
 * Contains external outputs and child input bags produced by routing a coupled model's messages.
 * Ordered source-aware deliveries additionally retain coupling provenance for enhanced scheduling.
 */
public class OutputCouplingMessages {

  final List<PortValue<?>> externalOutputMessages;
  final Map<String, List<PortValue<?>>> internalMessages;
  private final List<RoutedDelivery> routedDeliveries;

  /**
   * Constructs legacy routing results without source-aware deliveries.
   *
   * @param externalOutputMessages messages emitted by the coupled model
   * @param internalMessages input bags keyed by target model identifier
   */
  public OutputCouplingMessages(List<PortValue<?>> externalOutputMessages, Map<String, List<PortValue<?>>> internalMessages) {
    this(externalOutputMessages, internalMessages, List.of());
  }

  /**
   * Constructs routing results with an immutable snapshot of the ordered deliveries.
   * The external output list and internal message map are retained without copying.
   *
   * @param externalOutputMessages messages emitted by the coupled model
   * @param internalMessages input bags keyed by target model identifier
   * @param routedDeliveries resolved internal and external deliveries in routing order
   */
  public OutputCouplingMessages(List<PortValue<?>> externalOutputMessages,
      Map<String, List<PortValue<?>>> internalMessages, List<RoutedDelivery> routedDeliveries) {
    this.externalOutputMessages = externalOutputMessages;
    this.internalMessages = internalMessages;
    this.routedDeliveries = List.copyOf(routedDeliveries);
  }

  /**
   * Returns the coupled model's external output bag.
   *
   * @return external output messages
   */
  public List<PortValue<?>> getExternalOutputMessages() {
    return externalOutputMessages;
  }

  /**
   * Returns routed input bags by child model identifier.
   *
   * @return the internal message map
   */
  public Map<String, List<PortValue<?>>> getInternalMessages() {
    return internalMessages;
  }

  /**
   * Returns the immutable source-aware delivery list in routing order.
   *
   * @return resolved deliveries, or an empty list for legacy-only routing results
   */
  public List<RoutedDelivery> getRoutedDeliveries() {
    return routedDeliveries;
  }
}
