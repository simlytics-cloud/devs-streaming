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
 * Simulation time represented as a double value.
 */
@Value.Immutable
@JsonSerialize(as = DoubleSimTime.class)
@JsonDeserialize(as = DoubleSimTime.class)
public abstract class AbstractDoubleSimTime extends SimTime {

  public static DoubleSimTime maxValue = DoubleSimTime.builder().t(Double.MAX_VALUE).build();

  /**
   * Creates an immutable instance of {@link DoubleSimTime} with the specified simulation time
   * value.
   *
   * @param t the simulation time value to set in the created {@link DoubleSimTime} instance
   * @return a new {@link DoubleSimTime} instance with the specified time value
   */
  public static DoubleSimTime create(double t) {
    return DoubleSimTime.builder().t(t).build();
  }

  /**
   * Retrieves the simulation time value represented as a Double.
   *
   * @return the simulation time value
   */
  public abstract Double getT();

  /**
   * Returns a string representation of this {@code AbstractDoubleSimTime} instance. The string
   * includes the class name and the simulation time value.
   *
   * @return a string representation of the simulation time instance
   */
  @Override
  public String toString() {
    return "DoubleSimTime: " + getT();
  }

  /**
   * Adds the simulation time value of the given {@code SimTime} operand to the current instance's
   * simulation time value, and returns a new instance of {@code AbstractDoubleSimTime} representing
   * the result.
   *
   * @param operand the {@code SimTime} instance whose simulation time value is to be added
   * @return a new {@code AbstractDoubleSimTime} instance representing the sum of the current
   * instance's simulation time value and the given operand's simulation time value
   */
  @Override
  public AbstractDoubleSimTime plus(SimTime operand) {
    return DoubleSimTime.builder().t(getT() + ((AbstractDoubleSimTime) operand).getT()).build();
  }

  /**
   * Subtracts the simulation time value of the given {@code SimTime} operand from the current
   * instance's simulation time value, and returns a new instance of {@code AbstractDoubleSimTime}
   * representing the result.
   *
   * @param operand the {@code SimTime} instance whose simulation time value is to be subtracted
   * @return a new {@code AbstractDoubleSimTime} instance representing the difference between the
   * current instance's simulation time value and the given operand's simulation time value
   */
  @Override
  public AbstractDoubleSimTime minus(SimTime operand) {
    return DoubleSimTime.builder().t(getT() - ((AbstractDoubleSimTime) operand).getT()).build();
  }


  /**
   * Returns the maximum simulation time value that can be represented as a
   * {@link AbstractDoubleSimTime} instance. The maximum value is equivalent to
   * {@code Double.MAX_VALUE}.
   *
   * @return an {@link AbstractDoubleSimTime} instance representing the maximum simulation time
   * value
   */
  @Override
  @JsonIgnore
  public AbstractDoubleSimTime getMaxValue() {
    return DoubleSimTime.builder().t(Double.MAX_VALUE).build();
  }

  /**
   * Compares this {@code AbstractDoubleSimTime} instance with the specified {@code SimTime} operand
   * for order. Returns a negative integer, zero, or a positive integer if this instance is less
   * than, equal to, or greater than the specified operand, respectively.
   *
   * @param operand the {@code SimTime} instance to be compared
   * @return a negative integer, zero, or a positive integer if this instance is less than, equal
   * to, or greater than the specified operand, respectively
   */
  @Override
  public int compareTo(SimTime operand) {
    return getT().compareTo(((AbstractDoubleSimTime) operand).getT());
  }

  /**
   * Generates the hash code for the current instance based on the simulation time value.
   *
   * @return the hash code value for the current instance
   */
  @Override
  public int hashCode() {
    return Objects.hash(getT());
  }

  /**
   * Compares this instance with the specified object for equality.
   *
   * @param object the object to be compared for equality with this instance
   * @return true if the specified object is equal to this instance, false otherwise
   */
  @Override
  public boolean equals(Object object) {
    return super.equals(object);
  }


}
