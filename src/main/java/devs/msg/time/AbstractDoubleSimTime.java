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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Objects;

/**
 * Simulation time represented as a double value.
 */
@Value.Immutable
@JsonSerialize(as = DoubleSimTime.class)
@JsonDeserialize(as = DoubleSimTime.class)
public abstract class AbstractDoubleSimTime extends SimTime {

  public static DoubleSimTime maxValue = DoubleSimTime.builder().t(Double.MAX_VALUE).build();

  public static DoubleSimTime create(double t) {
    return DoubleSimTime.builder().t(t).build();
  }

  public abstract Double getT();

  @Override
  public String toString() {
    return "DoubleSimTime: " + getT();
  }

  @Override
  public AbstractDoubleSimTime plus(SimTime operand) {
    return DoubleSimTime.builder().t(getT() + ((AbstractDoubleSimTime) operand).getT()).build();
  }

  @Override
  public AbstractDoubleSimTime minus(SimTime operand) {
    return DoubleSimTime.builder().t(getT() - ((AbstractDoubleSimTime) operand).getT()).build();
  }


  @Override
  @JsonIgnore
  public AbstractDoubleSimTime getMaxValue() {
    return DoubleSimTime.builder().t(Double.MAX_VALUE).build();
  }

  @Override
  public int compareTo(SimTime operand) {
    return getT().compareTo(((AbstractDoubleSimTime) operand).getT());
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
