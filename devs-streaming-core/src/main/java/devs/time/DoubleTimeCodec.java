package devs.time;

import devs.iso.time.DoubleSimTime;
import devs.iso.time.QuantizedSimTime;
import java.math.BigInteger;

/**
 * Exact binary floating-point native-time codec, with {@link Double#MAX_VALUE} reserved
 * for logical infinity. IEEE infinities and NaN are not accepted native timestamps;
 * units are supplied separately by {@link TimeSemantics}.
 */
public final class DoubleTimeCodec implements NativeTimeCodec<DoubleSimTime> {
  /**
   * Decodes the exact binary value, collapsing signed zero and recognizing the sentinel.
   * @throws IllegalArgumentException if the native double is NaN or an IEEE infinity
   */
  @Override
  public QuantizedSimTime decode(DoubleSimTime value) {
    double nativeValue = value.getT();
    if (!Double.isFinite(nativeValue)) {
      throw new IllegalArgumentException("Nonfinite native double");
    }
    return nativeValue == Double.MAX_VALUE ? QuantizedSimTime.infinity()
        : QuantizedSimTime.of(Rational.of(nativeValue));
  }

  /**
   * Encodes infinity as the maximum double and finite rationals only if exactly representable.
   * @throws ArithmeticException for a nonbinary fraction, range or precision loss,
   *     or a finite value colliding with the infinity sentinel
   */
  @Override
  public DoubleSimTime encode(QuantizedSimTime value) {
    if (value.isInfinite()) {
      return DoubleSimTime.create(Double.MAX_VALUE);
    }
    Rational rational = value.value();
    // The decimal division is exact for dyadic rationals. Other denominators cannot encode exactly.
    if (rational.denominator().bitCount() != 1) {
      throw new ArithmeticException("Nonbinary rational double time");
    }
    double encoded = rational.toBigDecimal().doubleValue();
    if (!Double.isFinite(encoded) || encoded == Double.MAX_VALUE
        || !Rational.of(encoded).equals(rational)) {
      throw new ArithmeticException("Double time is not exactly representable");
    }
    return DoubleSimTime.create(encoded);
  }

  private static Rational powerOfTwo(int exponent) {
    return exponent >= 0 ? Rational.of(BigInteger.ONE.shiftLeft(exponent), BigInteger.ONE)
        : Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(-exponent));
  }

  private Rational inBin(Rational lower, Rational origin, Rational quantum, Rational min,
      Rational max, Rational spacing) {
    if (lower.compareTo(max) > 0) {
      return null;
    }
    Rational bound = lower.compareTo(min) < 0 ? min : lower;
    Rational candidate = Rational.intersectionCeiling(bound, origin, quantum, spacing);
    return candidate != null && candidate.compareTo(max) <= 0 ? candidate : null;
  }

  /**
   * Returns the least finite double at or above the native-unit bound that also lies on
   * the generic search grid, or on the binary encoding grid alone if quantum is null.
   * Binary spacing varies by magnitude and is not a supplier granularity declaration.
   * Infinity maps directly to its sentinel; finite searches never return infinity.
   * @throws IllegalArgumentException if a finite search uses a nonpositive quantum
   * @throws ArithmeticException if no finite double satisfies the bound and lattice
   */
  @Override
  public DoubleSimTime nextRepresentable(QuantizedSimTime lower, Rational origin, Rational quantum) {
    if (lower.isInfinite()) {
      return encode(lower);
    }
    Rational bound = lower.value();
    // Each exponent bin is a uniform integer grid. Intersect it algebraically with the
    // requested search grid rather than walking doubles or repeatedly rounding grid points.
    for (int exponent = 1023; exponent >= -1022; exponent--) {
      Rational spacing = powerOfTwo(exponent - 52);
      Rational min = exponent == 1023 ? Rational.of(-Double.MAX_VALUE)
          : powerOfTwo(exponent + 1).subtract(spacing).negate();
      Rational candidate = inBin(bound, origin, quantum, min, powerOfTwo(exponent).negate(), spacing);
      if (candidate != null) {
        return encode(QuantizedSimTime.of(candidate));
      }
    }
    Rational candidate = inBin(bound, origin, quantum,
        powerOfTwo(-1022).subtract(powerOfTwo(-1074)).negate(),
        powerOfTwo(-1022).subtract(powerOfTwo(-1074)), powerOfTwo(-1074));
    if (candidate != null) {
      return encode(QuantizedSimTime.of(candidate));
    }
    for (int exponent = -1022; exponent <= 1023; exponent++) {
      Rational spacing = powerOfTwo(exponent - 52);
      Rational max = exponent == 1023 ? Rational.of(Math.nextDown(Double.MAX_VALUE))
          : powerOfTwo(exponent + 1).subtract(spacing);
      candidate = inBin(bound, origin, quantum, powerOfTwo(exponent), max, spacing);
      if (candidate != null) {
        return encode(QuantizedSimTime.of(candidate));
      }
    }
    throw new ArithmeticException("No finite double time on the requested lattice");
  }
}