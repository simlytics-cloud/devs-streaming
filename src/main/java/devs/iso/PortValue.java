/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and DEVS Streaming Framework
 * Java contributors. All rights reserved.
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

package devs.iso;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Represents a value associated with a specific port in the DEVS Streaming Framework.
 *
 * The {@code PortValue} class encapsulates the value, port identifier, and port type. It provides
 * constructors for initializing these properties and methods for accessing them. The port type is
 * automatically deduced if not explicitly provided.
 *
 * @param <T> The type of the value associated with the port.
 */
public class PortValue<T> {
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
      property = "@class")
  private final T value;

  private final String portName;

  /**
   * Constructs a new {@code PortValue} object representing a value associated with a specific port.
   *
   * @param value the value to be associated with the port
   * @param portIdentifier the identifier of the port to which the value is associated
   */
  @JsonCreator
  public PortValue(
      @JsonProperty("value") @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS,
          include = JsonTypeInfo.As.PROPERTY, property = "@class") T value,
      @JsonProperty("portIdentifier") String portIdentifier) {
    this.value = value;
    this.portName = portIdentifier;
  }

  /**
   * Retrieves the value associated with this {@code PortValue}.
   *
   * @return the value of type {@code T} associated with the port.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,
      property = "@class")
  public T getValue() {
    return value;
  }

  /**
   * Retrieves the identifier of the port associated with this instance.
   *
   * @return the port identifier as a {@code String}.
   */
  public String getPortName() {
    return portName;
  }

  /**
   * Returns a string representation of the {@code PortValue} instance, including the port
   * identifier and associated value.
   *
   * @return a string in the format "PortValue with Port ID [portIdentifier] and value [value]".
   */
  @Override
  public String toString() {
    return "PortValue with Port ID " + portName + " and value " + value;
  }
}
