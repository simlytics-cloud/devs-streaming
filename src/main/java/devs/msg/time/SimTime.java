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

package devs.msg.time;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;

/**
 * An abstract class to represent a message about time.  Time values can be used
 * in addition, subtraction, and comparison operations.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    property = "timeType"
)
public abstract class SimTime implements Comparable<SimTime> {

  public abstract SimTime plus(SimTime operand);

  public abstract SimTime minus(SimTime operand);

  public abstract int compareTo(@NotNull SimTime operand);

  @Override
  public abstract int hashCode();

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (!(this.getClass().isInstance(object))) {
      return false;
    }
    SimTime simTime = (SimTime) object;
    return this.compareTo(simTime) == 0;
  }

}
