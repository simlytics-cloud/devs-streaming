/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and
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

package devs.iso;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public  class TestCustomer {
  protected final double tEnter;
  protected final double tWait;
  protected final double tLeave;

  @JsonCreator
  public TestCustomer(@JsonProperty("tEnter") double tEnter,
      @JsonProperty("tWait") double tWait,
      @JsonProperty("tLeave") double tLeave) {
    this.tEnter = tEnter;
    this.tWait = tWait;
    this.tLeave = tLeave;
  }

  public double gettEnter() {
    return tEnter;
  }

  public double gettWait() {
    return tWait;
  }

  public double gettLeave() {
    return tLeave;
  }
}
