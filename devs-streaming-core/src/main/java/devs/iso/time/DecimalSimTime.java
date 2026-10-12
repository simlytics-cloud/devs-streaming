package devs.iso.time;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable simulation time backed by an exact {@link BigDecimal}, with positive infinity as a
 * distinguished value for an unscheduled event or a passive model.
 *
 * <p>Use this type for models whose native timestamps and durations are finite decimals, such as
 * decimal sampling intervals, when binary floating-point rounding must not affect event ordering.
 * Construct values from decimal strings (for example, {@code new BigDecimal("0.1")}) rather than
 * from a {@code double} to preserve the intended decimal value. Trailing zeros are removed, so
 * {@code 1.0} and {@code 1.00} represent the same time; decimal scale is not retained.
 *
 * <p>The model's time semantics determine the unit. Enhanced coordination uses this type as its
 * canonical decimal-second clock and rejects mappings that would require a nonterminating decimal
 * result. Arithmetic and comparison require another {@code DecimalSimTime}; they do not convert
 * other {@link SimTime} implementations.
 *
 * <p>Register {@link ExactTimeJsonModule} for standalone JSON binding. JSON support alone does not
 * grant permission for remote execution; message transport requires an appropriate wire profile
 * and endpoint policy.
 */
public final class DecimalSimTime extends SimTime {
  private static final DecimalSimTime INFINITY = new DecimalSimTime(null);
  private final BigDecimal value;

  private DecimalSimTime(BigDecimal value) {
    this.value = value == null ? null : value.stripTrailingZeros();
  }

  /**
   * Creates a finite decimal time, removing trailing zeros without rounding.
   *
   * @param value the time in the model's native unit; negative values are permitted
   * @return a normalized finite time
   * @throws NullPointerException if {@code value} is null
   */
  public static DecimalSimTime of(BigDecimal value) {
    return new DecimalSimTime(Objects.requireNonNull(value));
  }



  /**
   * Creates a finite time using the same rules as {@link #of(BigDecimal)}.
   *
   * @param value the time in the model's native unit
   * @return a normalized finite time
   * @throws NullPointerException if {@code value} is null
   */
  public static DecimalSimTime create(BigDecimal value) {
    return of(value);
  }

  /**
   * Returns the shared positive-infinity value, which sorts after every finite time.
   *
   * @return positive infinity
   */
  public static DecimalSimTime infinity() {
    return INFINITY;
  }

  /**
   * Tests whether this time is positive infinity.
   *
   * @return true if this time has no finite decimal value
   */
  public boolean isInfinite() {
    return value == null;
  }

  /**
   * Returns the normalized finite decimal value.
   *
   * @return the time in the model's native unit
   * @throws ArithmeticException if this time is infinite
   */
  public BigDecimal value() {
    if (isInfinite()) {
      throw new ArithmeticException("Infinity has no decimal value");
    }
    return value;
  }

  /**
   * Returns the finite value, as an alias for {@link #value()}.
   *
   * @return the normalized decimal value
   * @throws ArithmeticException if this time is infinite
   */
  public BigDecimal getT() {
    return value();
  }

  /**
   * Adds two decimal times without rounding.
   *
   * @param operand another non-null decimal time in the same unit
   * @return the exact sum, or positive infinity if either time is infinite
   * @throws ClassCastException if {@code operand} is not a {@code DecimalSimTime}
   */
  @Override
  public DecimalSimTime plus(SimTime operand) {
    DecimalSimTime other = (DecimalSimTime) operand;
    return isInfinite() || other.isInfinite() ? infinity() : of(value.add(other.value));
  }

  /**
   * Subtracts a finite decimal time without rounding.
   *
   * @param operand a finite decimal time in the same unit
   * @return the exact difference, or positive infinity if this time is infinite
   * @throws ArithmeticException if {@code operand} is infinite, including infinity minus infinity
   * @throws ClassCastException if {@code operand} is not a {@code DecimalSimTime}
   * @throws NullPointerException if {@code operand} is null
   */
  @Override
  public DecimalSimTime minus(SimTime operand) {
    DecimalSimTime other = (DecimalSimTime) operand;
    if (other.isInfinite()) {
      throw new ArithmeticException("Subtraction of infinity is undefined");
    }
    return isInfinite() ? infinity() : of(value.subtract(other.value));
  }

  /**
   * Compares decimal times numerically, with positive infinity greater than all finite values.
   *
   * @param operand another decimal time in the same unit
   * @return a negative integer, zero, or a positive integer for less than, equal to, or greater than
   * @throws ClassCastException if {@code operand} is not a {@code DecimalSimTime}
   * @throws NullPointerException if {@code operand} is null
   */
  @Override
  public int compareTo(SimTime operand) {
    DecimalSimTime other = (DecimalSimTime) operand;
    return isInfinite() ? (other.isInfinite() ? 0 : 1)
        : other.isInfinite() ? -1 : value.compareTo(other.value);
  }

  /**
   * Returns positive infinity rather than a finite maximum decimal value.
   *
   * @return positive infinity
   */
  @Override
  public DecimalSimTime getMaxValue() {
    return infinity();
  }

  /**
   * Creates a finite decimal zero in the same time representation.
   *
   * @return zero time
   */
  @Override
  public DecimalSimTime createZeroTime() {
    return of(BigDecimal.ZERO);
  }

  /**
   * Returns the interval from this finite time to positive infinity.
   *
   * @return positive infinity
   * @throws ArithmeticException if this time is infinite, since infinity minus infinity is undefined
   */
  @Override
  public DecimalSimTime getTimeUntilMax() {
    return infinity().minus(this);
  }

  /**
   * Computes a hash from the normalized value, consistent with numeric equality.
   *
   * @return the value's hash code, or zero for infinity
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  /**
   * Returns the normalized decimal spelling or {@code Infinity}.
   *
   * @return a diagnostic representation, not a complete JSON timestamp
   */
  @Override
  public String toString() {
    return isInfinite() ? "Infinity" : value.toString();
  }
}