/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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
 */

package example.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StorageState {
  private final StorageStateEnum stateValue;
  private final boolean hasOutput;

  @JsonCreator
  public StorageState(@JsonProperty("stateValue") StorageStateEnum stateValue,
                      @JsonProperty("hasOutput") boolean hasOutput) {
    this.stateValue = stateValue;
    this.hasOutput = hasOutput;
  }

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
