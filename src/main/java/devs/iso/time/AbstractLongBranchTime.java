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
import org.immutables.value.Value;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/**
 * Simulation time represented as a long value.
 */
@Value.Immutable
@JsonSerialize(as = LongBranchTime.class)
@JsonDeserialize(as = LongBranchTime.class)
public abstract class AbstractLongBranchTime extends AbstractBranchTime {

  /**
   * Calculates the maximum possible simulation time value by subtracting the current simulation
   * time from the maximum allowable value for a long. This method returns a new instance of
   * {@link LongBranchTime} with the calculated value.
   *
   * @param currentTime the current simulation time as a {@link LongBranchTime} instance
   * @return a new {@link LongBranchTime} instance representing the maximum simulation time value
   * derived from the given current time
   */
  public static LongBranchTime maxValue(LongBranchTime currentTime) {
    return LongBranchTime.builder()
            .t(Long.MAX_VALUE - currentTime.getT())
            .branchId(currentTime.getBranchId())
            .build();
  }

  public static LongBranchTime buildMaxValue() {
    return LongBranchTime.builder()
            .t(Long.MAX_VALUE)
            .build();
  }
  
  public static LongBranchTime buildMaxValue(String branchId) {
    return LongBranchTime.builder()
            .t(Long.MAX_VALUE)
            .branchId(branchId)
            .build();
  }

  /**
   * Creates a new {@link LongBranchTime} instance with the specified simulation time and branch id.
   *
   * @param t the simulation time value to set
   * @param branchId the branch id to set
   * @return a new immutable {@link LongBranchTime} instance with the specified time and branch id
   */
  public static LongBranchTime create(long t, String branchId) {
    return LongBranchTime.builder().t(t)
            .branchId(branchId)
            .build();
  }

  /**
   * Creates a new {@link LongBranchTime} instance with the specified simulation time and no branch id.
   *
   * @param t the simulation time value to set
   * @return a new immutable {@link LongBranchTime} instance with the specified time
   */
  public static LongBranchTime create(long t) {
    return LongBranchTime.builder().t(t)
            .build();
  }
  
  public static long clamp(BigInteger t) {
    if (t.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
      return Long.MAX_VALUE;
    }
    
    if (t.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
      return Long.MIN_VALUE;
    }
    
    return t.longValue();
  }

  /**
   * Retrieves the simulation time value.
   *
   * @return the simulation time as a Long
   */
  public abstract Long getT();

  /**
   * 
   * @return the id of the simulation branch for this time
   */
  public abstract Optional<String> getBranchId();

  /**
   * Returns a string representation of the LongSimTime object.
   *
   * @return a string in the format "LongSimTime: [simulation time value]"
   */
  @Override
  public String toString() {
    String branchId =  ", Branch id: " + getBranchId().orElse("");
    return "LonBranchTime: " + getT() + branchId;
  }

  /**
   * Adds the given simulation time to this instance and returns a new {@link LongBranchTime} object
   * representing the result of the addition.
   *
   * @param operand the simulation time to add to this instance
   * @return a new {@link LongBranchTime} object with the sum of this instance's simulation time and
   * the given operand
   */
  @Override
  public LongBranchTime plus(SimTime operand) {
    LongBranchTime operandLong = (LongBranchTime) operand;
    checkBranchId(operandLong);
    BigInteger operandT = BigInteger.valueOf(operandLong.getT());
    BigInteger sum = operandT.add(BigInteger.valueOf(getT()));
    return LongBranchTime.builder()
            .t(clamp(sum))
            .branchId(getBranchId())
            .build();
  }

  /**
   * Subtracts the simulation time represented by the given operand from this instance and returns a
   * new {@link LongBranchTime} object representing the result of the subtraction.
   *
   * @param operand the {@link SimTime} object whose simulation time is to be subtracted from this
   *                instance
   * @return a new {@link LongBranchTime} object with the simulation time obtained after subtraction
   */
  @Override
  public LongBranchTime minus(SimTime operand) {
    LongBranchTime operandLong = (LongBranchTime) operand;
    checkBranchId(operandLong);
    BigInteger operandT = BigInteger.valueOf(operandLong.getT());
    BigInteger difference = BigInteger.valueOf(getT()).subtract(operandT);
    return LongBranchTime.builder()
            .t(clamp(difference))
            .branchId(getBranchId())
            .build();
  }

  /**
   * Retrieves the maximum possible value for simulation time represented by this class.
   *
   * @return an {@link AbstractLongBranchTime} instance representing the maximum simulation time value
   */
  @Override
  @JsonIgnore
  public AbstractLongBranchTime getMaxValue() {
    return LongBranchTime.builder().t(Long.MAX_VALUE).build();
  }

  @Override
  public SimTime getTimeUntilMax() {
    return LongBranchTime.maxValue((LongBranchTime) this);
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
    LongBranchTime operandLong = (LongBranchTime) operand;
    checkBranchId(operandLong);
    return getT().compareTo(operandLong.getT());
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
