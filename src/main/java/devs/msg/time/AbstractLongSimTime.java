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

package devs.msg.time;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;
import org.immutables.value.Value;

/**
 * Simulation time represented as a long value.
 */
@Value.Immutable
@JsonSerialize(as = LongSimTime.class)
@JsonDeserialize(as = LongSimTime.class)
public abstract class AbstractLongSimTime extends SimTime {

  public static LongSimTime maxValue = LongSimTime.builder().t(Long.MAX_VALUE).build();

  public static LongSimTime create(long t) {
    return LongSimTime.builder().t(t).build();
  }

  public abstract Long getT();

  @Override
  public String toString() {
    return "LongSimTime: " + getT();
  }

  @Override
  public LongSimTime plus(SimTime operand) {
    return LongSimTime.builder().t(getT() + ((LongSimTime) operand).getT()).build();
  }

  @Override
  public LongSimTime minus(SimTime operand) {
    return LongSimTime.builder().t(getT() - ((LongSimTime) operand).getT()).build();
  }

  @Override
  @JsonIgnore
  public AbstractLongSimTime getMaxValue() {
    return LongSimTime.builder().t(Long.MAX_VALUE).build();
  }

  @Override
  public int compareTo(SimTime operand) {
    return getT().compareTo(((LongSimTime) operand).getT());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getT());
  }

  @Override
  public boolean equals(Object object) {
    return super.equals(object);
  }


}
