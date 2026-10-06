package devs.time;

import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;

/** Encodes native numerical values, before applying units, origin, or alignment. */
public interface NativeTimeCodec<N extends SimTime> {
  /**
   * Decodes a native timestamp into its exact numerical value or logical infinity.
   * The result is in native units, not canonical seconds; reserved infinity sentinels
   * are determined by the implementation.
   */
  QuantizedSimTime decode(N value);

  /**
   * Encodes an exact native-unit value or logical infinity without rounding.
   * @throws ArithmeticException if a finite value cannot be represented exactly
   */
  N encode(QuantizedSimTime value);

  /**
   * Adapts a native-unit lower bound upward to an accepted finite encoding.
   * Already exactly encodable values are unchanged; logical infinity is preserved.
   * Unlike {@link #encode}, adaptation may round, according to codec-owned policy.
   * Exact encoding and decoding are not affected by that policy.
   * @throws ArithmeticException if no accepted finite encoding satisfies a finite bound
   */
  default N encodeCeiling(QuantizedSimTime lower) {
    return encodeCeiling(lower, Rational.ZERO, null);
  }

  /**
   * Adapts upward on the intersection of the encoding domain and
   * {@code origin + k * quantum}, not by sequential rounding. Null quantum means
   * no additional grid. Decimal fallback precision applies only without a grid
   * and only to nonterminating bounds. Finite bounds never become infinity.
   * @throws IllegalArgumentException if a finite search uses a nonpositive quantum
   * @throws ArithmeticException if the grid has no accepted finite result
   */
  default N encodeCeiling(QuantizedSimTime lower, Rational origin, Rational quantum) {
    return nextRepresentable(lower, origin, quantum);
  }

  /**
   * Least encoded value >= lower on origin + k * quantum. Here quantum is a generic
   * search-grid spacing, not supplier metadata; null means no additional search grid.
   * Representation, range, and sentinel constraints remain mandatory in either case.
   */
  N nextRepresentable(QuantizedSimTime lower, Rational origin, Rational quantum);

  /**
   * Tests whether a finite native-unit value round trips exactly through this codec.
   * Arithmetic and argument validation failures are reported as {@code false}.
   */
  default boolean isRepresentable(Rational value) {
    try {
      return decode(encode(QuantizedSimTime.of(value))).equals(QuantizedSimTime.of(value));
    } catch (ArithmeticException | IllegalArgumentException exception) {
      return false;
    }
  }
}