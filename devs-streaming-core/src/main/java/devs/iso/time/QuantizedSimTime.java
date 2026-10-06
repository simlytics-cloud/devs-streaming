package devs.iso.time;

import devs.time.Rational;
import java.util.Objects;

/**
 * Immutable rational simulation time used to represent exact canonical seconds, with a
 * distinguished positive infinity for an unscheduled event or a passive model.
 *
 * <p>Use this type for canonical scheduling when coupled models have different native time units,
 * origins, or declared time granularity. Rational arithmetic preserves exact event ordering and
 * simultaneity, including values such as one third of a second that neither finite decimals nor binary
 * floating-point values can represent exactly. Native-time codecs also use this type as an exact
 * intermediate before unit and origin mapping.
 *
 * <p>Despite its name, this value type does not choose a quantum, round values, or enforce a time
 * lattice. A model's optional {@code TimeGranularity} declares positive rational spacing in native
 * units anchored at native zero; absence leaves native codec constraints intact. Declare it when
 * off-grid timestamps are unsupported, not as a minimum advance, tolerance, or common tick.
 * Exact initialization respects the native mapping and grid without adaptation. Delivery adaptation
 * belongs to explicit causal composition rules, keeping source and effective times separate. Choose
 * {@link DecimalSimTime} for a model whose native values are finite decimals; use time mappings
 * to convert between native values and canonical seconds rather than mixing types in arithmetic.
 * Arithmetic and comparison here require another {@code QuantizedSimTime}.
 *
 * <p>Register {@link ExactTimeJsonModule} for standalone JSON binding. Rational transport requires
 * an explicitly compatible or recorder endpoint policy; JSON support alone does not make this a
 * supplier-native time encoding.
 */
public final class QuantizedSimTime extends SimTime {
  private static final QuantizedSimTime INFINITY = new QuantizedSimTime(null);
  private final Rational value;

  private QuantizedSimTime(Rational value) {
    this.value = value;
  }

  /**
   * Creates a finite rational time without rounding or enforcing a quantum.
   *
   * @param value the exact time, normally in canonical seconds; negative values are permitted
   * @return a finite rational time
   * @throws NullPointerException if {@code value} is null
   */
  public static QuantizedSimTime of(Rational value) {
    return new QuantizedSimTime(Objects.requireNonNull(value));
  }

  /**
   * Returns the shared positive-infinity value, which sorts after every finite time.
   *
   * @return positive infinity
   */
  public static QuantizedSimTime infinity() {
    return INFINITY;
  }

  /**
   * Tests whether this time is positive infinity.
   *
   * @return true if this time has no finite rational value
   */
  public boolean isInfinite() {
    return value == null;
  }

  /**
   * Returns the exact finite rational value.
   *
   * @return the time, normally in canonical seconds
   * @throws ArithmeticException if this time is infinite
   */
  public Rational value() {
    if (isInfinite()) {
      throw new ArithmeticException("Infinity has no rational value");
    }
    return value;
  }

  /**
   * Adds two rational times exactly.
   *
   * @param operand another non-null rational time in the same unit and coordinate system
   * @return the exact sum, or positive infinity if either time is infinite
   * @throws ClassCastException if {@code operand} is not a {@code QuantizedSimTime}
   */
  @Override
  public QuantizedSimTime plus(SimTime operand) {
    QuantizedSimTime other = (QuantizedSimTime) operand;
    return isInfinite() || other.isInfinite() ? infinity() : of(value.add(other.value));
  }

  /**
   * Subtracts a finite rational time exactly.
   *
   * @param operand a finite rational time in the same unit and coordinate system
   * @return the exact difference, or positive infinity if this time is infinite
   * @throws ArithmeticException if {@code operand} is infinite, including infinity minus infinity
   * @throws ClassCastException if {@code operand} is not a {@code QuantizedSimTime}
   * @throws NullPointerException if {@code operand} is null
   */
  @Override
  public QuantizedSimTime minus(SimTime operand) {
    QuantizedSimTime other = (QuantizedSimTime) operand;
    if (other.isInfinite()) {
      throw new ArithmeticException("Subtraction of infinity is not a finite or positive infinite time");
    }
    return isInfinite() ? infinity() : of(value.subtract(other.value));
  }

  /**
   * Compares exact rational values, with positive infinity greater than all finite values.
   *
   * @param operand another rational time in the same unit and coordinate system
   * @return a negative integer, zero, or a positive integer for less than, equal to, or greater than
   * @throws ClassCastException if {@code operand} is not a {@code QuantizedSimTime}
   * @throws NullPointerException if {@code operand} is null
   */
  @Override
  public int compareTo(SimTime operand) {
    QuantizedSimTime other = (QuantizedSimTime) operand;
    return isInfinite() ? (other.isInfinite() ? 0 : 1)
        : other.isInfinite() ? -1 : value.compareTo(other.value);
  }

  /**
   * Returns positive infinity rather than a finite maximum rational value.
   *
   * @return positive infinity
   */
  @Override
  public QuantizedSimTime getMaxValue() {
    return infinity();
  }

  /**
   * Creates a finite rational zero in the same time representation.
   *
   * @return zero time
   */
  @Override
  public QuantizedSimTime createZeroTime() {
    return of(Rational.ZERO);
  }

  /**
   * Returns the interval from this finite time to positive infinity.
   *
   * @return positive infinity
   * @throws ArithmeticException if this time is infinite, since infinity minus infinity is undefined
   */
  @Override
  public QuantizedSimTime getTimeUntilMax() {
    return infinity().minus(this);
  }

  /**
   * Computes a hash from the rational value, consistent with numeric equality.
   *
   * @return the value's hash code, or zero for infinity
   */
  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  /**
   * Returns the rational value's spelling or {@code Infinity}.
   *
   * @return a diagnostic representation, not a complete JSON timestamp
   */
  @Override
  public String toString() {
    return isInfinite() ? "Infinity" : value.toString();
  }
}