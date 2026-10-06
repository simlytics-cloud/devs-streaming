package devs.time;

import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import java.math.BigInteger;

/**
 * Exact integer native-time codec reserving {@link Long#MAX_VALUE} for logical infinity.
 * Finite timestamps range from {@link Long#MIN_VALUE} through {@code Long.MAX_VALUE - 1};
 * units are supplied separately by {@link TimeSemantics}.
 */
public final class LongTimeCodec implements NativeTimeCodec<LongSimTime> {
  /** Decodes an integer exactly, treating the maximum long as logical infinity. */
  @Override
  public QuantizedSimTime decode(LongSimTime value) {
    return value.getT() == Long.MAX_VALUE ? QuantizedSimTime.infinity()
        : QuantizedSimTime.of(Rational.of(value.getT()));
  }

  /**
   * Encodes infinity as the maximum long and finite values without rounding.
   * @throws ArithmeticException for fractions, overflow, or a finite sentinel collision
   */
  @Override
  public LongSimTime encode(QuantizedSimTime value) {
    if (value.isInfinite()) {
      return LongSimTime.create(Long.MAX_VALUE);
    }
    if (!value.value().isInteger()) {
      throw new ArithmeticException("Nonintegral long time");
    }
    long encoded = value.value().numerator().longValueExact();
    if (encoded == Long.MAX_VALUE) {
      throw new ArithmeticException("Finite value collides with long infinity sentinel");
    }
    return LongSimTime.create(encoded);
  }

  /**
   * Returns the least finite integer at or above the native-unit bound, on the requested
   * generic search grid if quantum is nonnull. Integer encoding spacing is mandatory,
   * independently of any supplier declaration. Infinity maps directly to its sentinel.
   * @throws IllegalArgumentException if a finite search uses a nonpositive quantum
   * @throws ArithmeticException if no finite integer satisfies the bound and lattice
   */
  @Override
  public LongSimTime nextRepresentable(QuantizedSimTime lower, Rational origin, Rational quantum) {
    if (lower.isInfinite()) {
      return encode(lower);
    }
    Rational bound = lower.value().compareTo(Rational.of(Long.MIN_VALUE)) < 0
        ? Rational.of(Long.MIN_VALUE) : lower.value();
    Rational candidate = Rational.intersectionCeiling(bound, origin, quantum, Rational.ONE);
    if (candidate == null || candidate.compareTo(Rational.of(
        BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.ONE), BigInteger.ONE)) > 0) {
      throw new ArithmeticException("No finite long time on the requested lattice");
    }
    return encode(QuantizedSimTime.of(candidate));
  }
}