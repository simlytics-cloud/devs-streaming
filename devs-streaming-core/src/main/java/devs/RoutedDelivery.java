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

/**
 * A resolved coupling delivery retaining its source and transformed target message.
 *
 * @param sourceModel identifier of the model that emitted the original value
 * @param sourcePort port from which the original value was emitted
 * @param targetModel identifier of the receiving child or coupled model
 * @param portValue target port and value after coupling resolution
 */
public record RoutedDelivery(String sourceModel, String sourcePort, String targetModel,
                             PortValue<?> portValue) {

  /**
   * Returns the destination port selected by coupling resolution.
   *
   * @return the transformed port value's port name
   */
  public String targetPort() {
    return portValue.getPortName();
  }
}