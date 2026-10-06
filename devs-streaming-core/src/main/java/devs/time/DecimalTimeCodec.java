package devs.time;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.QuantizedSimTime;
import java.math.BigInteger;

/** Exact decimal codec for local use only. */
public final class DecimalTimeCodec implements NativeTimeCodec<DecimalSimTime> {
  private final int adaptationScale;

  /** Creates a codec with 18 fractional digits for nonterminating-bound adaptation. */
  public DecimalTimeCodec() {
    this(18);
  }

  /**
   * Configures the {@code 10^-adaptationScale} fallback grid for nonterminating bounds
   * without an additional search grid. Exact encode/decode remain unrestricted;
   * accepted terminating bounds and explicit grid intersections never use this scale.
   * @throws IllegalArgumentException if adaptationScale is negative
   */
  public DecimalTimeCodec(int adaptationScale) {
    if (adaptationScale < 0) {
      throw new IllegalArgumentException("Adaptation scale must be nonnegative");
    }
    this.adaptationScale = adaptationScale;
  }

  /** Returns the immutable fallback fractional-digit scale. */
  public int adaptationScale() {
    return adaptationScale;
  }

  /** Decodes the decimal exactly in native units, preserving logical infinity. */
  @Override
  public QuantizedSimTime decode(DecimalSimTime value) {
    return value.isInfinite() ? QuantizedSimTime.infinity()
        : QuantizedSimTime.of(Rational.of(value.value()));
  }

  /**
   * Encodes a terminating decimal without rounding, or preserves logical infinity.
   * @throws ArithmeticException if the finite rational has a nonterminating decimal expansion
   */
  @Override
  public DecimalSimTime encode(QuantizedSimTime value) {
    return value.isInfinite() ? DecimalSimTime.infinity()
        : DecimalSimTime.of(value.value().toBigDecimal());
  }

  /**
   * Preserves accepted terminating bounds regardless of precision. Without a search
   * grid, nonterminating bounds round upward on {@code 10^-adaptationScale}.
   * With a grid, selects the earliest exact terminating grid point instead, even
   * if that point needs more fractional digits than the configured scale.
   * This policy does not change exact encoding, decoding, or grid-search semantics.
   */
  @Override
  public DecimalSimTime encodeCeiling(QuantizedSimTime lower, Rational origin, Rational quantum) {
    if (lower.isInfinite() || quantum != null) {
      return nextRepresentable(lower, origin, quantum);
    }
    if (decimalPart(lower.value().denominator()).equals(lower.value().denominator())) {
      return encode(lower);
    }
    Rational spacing = Rational.of(BigInteger.ONE, BigInteger.TEN.pow(adaptationScale));
    return encode(QuantizedSimTime.of(lower.value().ceilToLattice(Rational.ZERO, spacing)));
  }

  private static BigInteger decimalPart(BigInteger denominator) {
    BigInteger result = BigInteger.ONE.shiftLeft(denominator.getLowestSetBit());
    denominator = denominator.divide(result);
    BigInteger five = BigInteger.valueOf(5);
    while (denominator.mod(five).signum() == 0) {
      result = result.multiply(five);
      denominator = denominator.divide(five);
    }
    return result;
  }

  /**
   * Returns the least exact decimal on the generic native-unit search grid not below lower.
   * Terminating-decimal representation remains mandatory, independent of supplier metadata.
   * With null quantum, only an exactly encodable lower bound is returned: terminating
   * decimals are dense, so a nondecimal bound has no least decimal successor.
   * Use {@link #encodeCeiling} for policy-based adaptation without a search grid.
   * Explicit grids intersect all terminating decimals without restricting their scale.
   * Infinity is preserved without a lattice search.
   * @throws IllegalArgumentException if a finite search uses a nonpositive quantum
   * @throws ArithmeticException if no exact decimal exists on the lattice, or a
   *     nondecimal bound is supplied without a lattice
   */
  @Override
  public DecimalSimTime nextRepresentable(QuantizedSimTime lower, Rational origin, Rational quantum) {
    if (lower.isInfinite()) {
      return encode(lower);
    }
    if (quantum == null) {
      return encode(lower);
    }
    Rational.requirePositive(quantum);
    BigInteger scale = origin.denominator().multiply(quantum.denominator())
        .divide(origin.denominator().gcd(quantum.denominator()));
    Rational candidate = Rational.intersectionCeiling(lower.value(), origin, quantum,
        Rational.of(BigInteger.ONE, decimalPart(scale)));
    if (candidate == null) {
      throw new ArithmeticException("No exact decimal on the requested lattice");
    }
    return encode(QuantizedSimTime.of(candidate));
  }
}