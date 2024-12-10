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

package devs.msg;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PortValue<T> {

  private final T value;

  private final String portIdentifier;

  private final String portType;

  @JsonCreator
  public PortValue(@JsonProperty("value") T value,
      @JsonProperty("portIdentifier") String portIdentifier,
      @JsonProperty("portType") String portType) {
    this.value = value;
    this.portIdentifier = portIdentifier;
    this.portType = portType;
  }

  public PortValue(T value, String portIdentifier) {
    this.value = value;
    this.portIdentifier = portIdentifier;
    this.portType = value.getClass().getCanonicalName();
  }

  public T getValue() {
    return value;
  }

  public String getPortIdentifier() {
    return portIdentifier;
  }

  @JsonProperty("portType")
  public String getPortType() {
    return portType;
  }

  @Override
  public String toString() {
    return "PortValue with Port ID " + portIdentifier + " and value " + value;
  }
}
