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

  /**
   * Creates a new {@link LongSimTime} instance with the specified simulation time.
   *
   * @param t the simulation time value to set
   * @return a new immutable {@link LongSimTime} instance with the specified time
   */
  public static LongSimTime create(long t) {
    return LongSimTime.builder().t(t).build();
  }

  /**
   * Retrieves the simulation time value.
   *
   * @return the simulation time as a Long
   */
  public abstract Long getT();

  /**
   * Returns a string representation of the LongSimTime object.
   *
   * @return a string in the format "LongSimTime: [simulation time value]"
   */
  @Override
  public String toString() {
    return "LongSimTime: " + getT();
  }

  /**
   * Adds the given simulation time to this instance and returns a new {@link LongSimTime} object
   * representing the result of the addition.
   *
   * @param operand the simulation time to add to this instance
   * @return a new {@link LongSimTime} object with the sum of this instance's simulation time and
   * the given operand
   */
  @Override
  public LongSimTime plus(SimTime operand) {
    return LongSimTime.builder().t(getT() + ((LongSimTime) operand).getT()).build();
  }

  /**
   * Subtracts the simulation time represented by the given operand from this instance and returns a
   * new {@link LongSimTime} object representing the result of the subtraction.
   *
   * @param operand the {@link SimTime} object whose simulation time is to be subtracted from this
   *                instance
   * @return a new {@link LongSimTime} object with the simulation time obtained after subtraction
   */
  @Override
  public LongSimTime minus(SimTime operand) {
    return LongSimTime.builder().t(getT() - ((LongSimTime) operand).getT()).build();
  }

  /**
   * Retrieves the maximum possible value for simulation time represented by this class.
   *
   * @return an {@link AbstractLongSimTime} instance representing the maximum simulation time value
   */
  @Override
  @JsonIgnore
  public AbstractLongSimTime getMaxValue() {
    return LongSimTime.builder().t(Long.MAX_VALUE).build();
  }

  /**
   * Compares this instance of simulation time with the specified {@link SimTime} instance.
   *
   * @param operand the {@link SimTime} instance to be compared with this instance
   * @return a negative integer, zero, or a positive integer as this instance is less than, equal
   * to, or greater than the specified instance
   */
  @Override
  public int compareTo(SimTime operand) {
    return getT().compareTo(((LongSimTime) operand).getT());
  }

  /**
   * Generates a hash code for the object based on its simulation time value.
   *
   * @return the hash code generated using the simulation time value
   */
  @Override
  public int hashCode() {
    return Objects.hash(getT());
  }

  /**
   * Compares this object with the specified object for equality.
   *
   * @param object the object to be compared for equality with this instance
   * @return true if the specified object is equal to this instance, otherwise false
   */
  @Override
  public boolean equals(Object object) {
    return super.equals(object);
  }


}
