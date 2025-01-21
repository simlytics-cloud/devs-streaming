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

package example.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the state of storage with a specific value and an optional output indicator. This
 * immutable class captures the state using a predefined enumeration value
 * ({@link StorageStateEnum}) and a boolean flag indicating whether the state has produced an
 * output.
 * <p>
 * Key features: 1. The `stateValue` property defines the specific state, based on the
 * {@link StorageStateEnum} enumeration. 2. The `hasOutput` property indicates whether the current
 * state has generated an output.
 * <p>
 * This class includes constructors to initialize the state with or without an output, and provides
 * getter methods for retrieving the state properties.
 */
public class StorageState {

  private final StorageStateEnum stateValue;
  private final boolean hasOutput;

  /**
   * Constructs a new instance of the StorageState class.
   *
   * @param stateValue the specific state value represented as a {@link StorageStateEnum}
   * @param hasOutput  a boolean indicating whether the current state has produced an output
   */
  @JsonCreator
  public StorageState(@JsonProperty("stateValue") StorageStateEnum stateValue,
      @JsonProperty("hasOutput") boolean hasOutput) {
    this.stateValue = stateValue;
    this.hasOutput = hasOutput;
  }

  /**
   * Constructs a new instance of the StorageState class with a specific state value.
   *
   * @param stateValue the specific state value represented as a {@link StorageStateEnum}
   */
  public StorageState(StorageStateEnum stateValue) {
    this.stateValue = stateValue;
    this.hasOutput = false;
  }

  public StorageStateEnum getStateValue() {
    return stateValue;
  }

  public boolean getHasOutput() {
    return hasOutput;
  }
}
