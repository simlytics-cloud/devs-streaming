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

import devs.msg.PortValue;

/**
 * Represents a port within a DEVS model. A port is identified by a unique identifier and is
 * associated with a specific data type represented by the class type T.
 *
 * @param <T> the type of data associated with the port
 */
public class Port<T> {

  private final String portIdentifier;
  protected final Class<T> clazz;

  /**
   * Constructs a Port instance with a specified identifier and associated data type.
   *
   * @param portIdentifier the unique identifier of the port
   * @param clazz          the class type representing the data type associated with the port
   */
  public Port(String portIdentifier, Class<T> clazz) {
    this.portIdentifier = portIdentifier;
    this.clazz = clazz;
  }

  public T getValue(PortValue<?> portValue) {
    return clazz.cast(portValue.getValue());
  }

  /**
   * Creates a new instance of {@link PortValue} using the provided value and the port's
   * identifier.
   *
   * @param value the value to be associated with the port
   * @return a new {@link PortValue} instance containing the specified value and port identifier
   */
  public PortValue<T> createPortValue(T value) {
    return new PortValue<>(value, portIdentifier);
  }

  public String getPortIdentifier() {
    return portIdentifier;
  }
}
