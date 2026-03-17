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

package devs.iso.time;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Simulation time represented as a double value with a branch ID.
 */
@Value.Immutable
@JsonSerialize(as = DoubleBranchTime.class)
@JsonDeserialize(as = DoubleBranchTime.class)
public abstract class AbstractDoubleBranchTime extends AbstractBranchTime {

  /**
   * Calculates the maximum possible simulation time value by subtracting the current
   * simulation time value from {@code Double.MAX_VALUE}.
   *
   * @param currentTime the current simulation time represented as a {@link DoubleBranchTime}
   * @return a new {@link DoubleBranchTime} instance representing the maximum simulation time
   *         value relative to the given {@code currentTime}
   */
  public static DoubleBranchTime maxValue(DoubleBranchTime currentTime) {
    return DoubleBranchTime.builder()
        .t(Double.MAX_VALUE - currentTime.getT())
        .branchId(currentTime.getBranchId())
        .build();
  }

  public static DoubleBranchTime buildMaxValue() {
    return DoubleBranchTime.builder().t(Double.MAX_VALUE).build();
  }

  public static DoubleBranchTime buildMaxValue(String branchId) {
    return DoubleBranchTime.builder()
        .t(Double.MAX_VALUE)
        .branchId(branchId)
        .build();
  }

  /**
   * Creates an immutable instance of {@link DoubleBranchTime} with the specified simulation time
   * value and branch ID.
   *
   * @param t the simulation time value to set in the created {@link DoubleBranchTime} instance
   * @param branchId the branch ID to set
   * @return a new {@link DoubleBranchTime} instance with the specified time and branch ID
   */
  public static DoubleBranchTime create(double t, String branchId) {
    return DoubleBranchTime.builder().t(t).branchId(branchId).build();
  }

  /**
   * Creates an immutable instance of {@link DoubleBranchTime} with the specified simulation time
   * value and no branch ID.
   *
   * @param t the simulation time value to set in the created {@link DoubleBranchTime} instance
   * @return a new {@link DoubleBranchTime} instance with the specified time
   */
  public static DoubleBranchTime create(double t) {
    return DoubleBranchTime.builder().t(t).build();
  }

  static double clamp(double v) {
    if (Double.isNaN(v)) {
      throw new ArithmeticException("NaN encountered");
    }
    if (v == Double.POSITIVE_INFINITY) {
      return Double.MAX_VALUE;
    }
    if (v == Double.NEGATIVE_INFINITY) {
      return -Double.MAX_VALUE;
    }
    return v;
  }

  /**
   * Retrieves the simulation time value represented as a Double.
   *
   * @return the simulation time value
   */
  public abstract Double getT();

  /**
   * Returns a string representation of this {@code AbstractDoubleBranchTime} instance. The string
   * includes the class name and the simulation time value and branch ID.
   *
   * @return a string representation of the simulation time instance
   */
  @Override
  public String toString() {
    String branchId = ", Branch id: " + getBranchId().orElse("");
    return "DoubleBranchTime: " + getT() + branchId;
  }

  /**
   * Adds the simulation time value of the given {@code SimTime} operand to the current instance's
   * simulation time value, and returns a new instance of {@code DoubleBranchTime} representing
   * the result.
   *
   * @param operand the {@code SimTime} instance whose simulation time value is to be added
   * @return a new {@code DoubleBranchTime} instance representing the sum of the current
   * instance's simulation time value and the given operand's simulation time value
   */
  @Override
  public DoubleBranchTime plus(SimTime operand) {
    DoubleBranchTime operandDouble = (DoubleBranchTime) operand;
    checkBranchId(operandDouble);
    double sum = clamp(getT() + operandDouble.getT());
    return DoubleBranchTime.builder()
        .t(clamp(sum))
        .branchId(getBranchId())
        .build();
  }

  /**
   * Subtracts the simulation time value of the given {@code SimTime} operand from the current
   * instance's simulation time value, and returns a new instance of {@code DoubleBranchTime}
   * representing the result.
   *
   * @param operand the {@code SimTime} instance whose simulation time value is to be subtracted
   * @return a new {@code DoubleBranchTime} instance representing the difference between the
   * current instance's simulation time value and the given operand's simulation time value
   */
  @Override
  public DoubleBranchTime minus(SimTime operand) {
    DoubleBranchTime operandDouble = (DoubleBranchTime) operand;
    checkBranchId(operandDouble);
    double difference = clamp(getT() - operandDouble.getT());
    return DoubleBranchTime.builder()
        .t(difference)
        .branchId(getBranchId())
        .build();
  }

  /**
   * Returns the maximum simulation time value that can be represented as a
   * {@link AbstractDoubleBranchTime} instance. The maximum value is equivalent to
   * {@code Double.MAX_VALUE}.
   *
   * @return an {@link AbstractDoubleBranchTime} instance representing the maximum simulation time
   * value
   */
  @Override
  @JsonIgnore
  public AbstractDoubleBranchTime getMaxValue() {
    return DoubleBranchTime.builder().t(Double.MAX_VALUE).build();
  }

  @Override
  public SimTime getTimeUntilMax() {
    return DoubleBranchTime.maxValue((DoubleBranchTime) this);
  }

  /**
   * Compares this {@code AbstractDoubleBranchTime} instance with the specified {@code SimTime} operand
   * for order. Returns a negative integer, zero, or a positive integer if this instance is less
   * than, equal to, or greater than the specified operand, respectively.
   *
   * @param operand the {@code SimTime} instance to be compared
   * @return a negative integer, zero, or a positive integer if this instance is less than, equal
   * to, or greater than the specified operand, respectively
   */
  @Override
  public int compareTo(SimTime operand) {
    DoubleBranchTime operandDouble = (DoubleBranchTime) operand;
    checkBranchId(operandDouble);
    return getT().compareTo(operandDouble.getT());
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
