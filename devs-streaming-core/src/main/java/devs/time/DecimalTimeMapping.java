package devs.time;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.SimTime;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * Affine mapping to decimal canonical seconds for enhanced coordination.
 * Exact mappings support long and decimal native clocks; continuous double clocks must use the
 * explicitly bounded {@link #approximateDouble(TimeSemantics, BigDecimal, MathContext, BigDecimal)}
 * factory.
 */
public final class DecimalTimeMapping<N extends SimTime> {
  private static final MathContext DEFAULT_APPROXIMATE_CONTEXT = MathContext.DECIMAL128;
  private static final BigDecimal DEFAULT_APPROXIMATE_ERROR = new BigDecimal("1e-15");

  private final NativeTimeCodec<N> codec;
  private final TimeSemantics semantics;
  private final BigDecimal alignment;
  private final MathContext inverseContext;
  private final BigDecimal maximumError;

  public DecimalTimeMapping(NativeTimeCodec<N> codec, TimeSemantics semantics, BigDecimal alignment) {
    this.codec = Objects.requireNonNull(codec);
    if (codec instanceof DoubleTimeCodec) {
      throw new IllegalArgumentException(
          "Double native clocks require DecimalTimeMapping.approximateDouble");
    }
    this.semantics = Objects.requireNonNull(semantics);
    this.alignment = Objects.requireNonNull(alignment).stripTrailingZeros();
    this.inverseContext = null;
    this.maximumError = null;
  }
  public DecimalTimeMapping(NativeTimeCodec<N> codec, TimeSemantics semantics) { this(codec, semantics, BigDecimal.ZERO); }

  private DecimalTimeMapping(DoubleTimeCodec codec, TimeSemantics semantics, BigDecimal alignment,
      MathContext inverseContext, BigDecimal maximumError) {
    @SuppressWarnings("unchecked")
    NativeTimeCodec<N> doubleCodec = (NativeTimeCodec<N>) codec;
    this.codec = doubleCodec;
    this.semantics = Objects.requireNonNull(semantics);
    this.alignment = Objects.requireNonNull(alignment).stripTrailingZeros();
    this.inverseContext = Objects.requireNonNull(inverseContext);
    if (inverseContext.getPrecision() == 0) {
      throw new IllegalArgumentException("Approximate double mappings require finite precision");
    }
    this.maximumError = Objects.requireNonNull(maximumError).stripTrailingZeros();
    if (this.maximumError.signum() < 0) {
      throw new IllegalArgumentException("maximumError must be nonnegative");
    }
  }

  /**
   * Creates an opt-in bounded-error mapping for a continuous native double clock.
   * The finite decimal scale is an approximation; no native granularity is implied.
   *
   * @param semantics native unit scale, origin, and optional supplier grid
   * @param alignment composition-level canonical offset
   * @param inverseContext precision and rounding used when a canonical timestamp is inverted
   * @param maximumError largest permitted canonical displacement after conversion
   */
  public static DecimalTimeMapping<DoubleSimTime> approximateDouble(
      TimeSemantics semantics, BigDecimal alignment, MathContext inverseContext,
      BigDecimal maximumError) {
    return new DecimalTimeMapping<>(new DoubleTimeCodec(), semantics, alignment,
        inverseContext, maximumError);
  }

  /** Creates an approximate double mapping with DECIMAL128 inversion and a 1e-15 error limit. */
  public static DecimalTimeMapping<DoubleSimTime> approximateDouble(
      TimeSemantics semantics, BigDecimal alignment) {
    return approximateDouble(semantics, alignment, DEFAULT_APPROXIMATE_CONTEXT,
        DEFAULT_APPROXIMATE_ERROR);
  }

  public NativeTimeCodec<N> codec() { return codec; }
  public TimeSemantics semantics() { return semantics; }
  public BigDecimal alignment() { return alignment; }
  /** Returns whether canonical-to-native conversion has an explicitly bounded approximation. */
  public boolean isApproximate() { return inverseContext != null; }
  /** Returns the configured canonical error limit, or null for an exact mapping. */
  public BigDecimal maximumError() { return maximumError; }

  private void validateLattice(BigDecimal value) {
    semantics.timeGranularity().ifPresent(grid -> {
      try { value.divide(grid.spacing()).toBigIntegerExact(); }
      catch (ArithmeticException e) { throw new ArithmeticException("Native time is off its declared granularity grid"); }
    });
  }
  public DecimalSimTime toCanonical(N nativeTime) {
    var decoded = codec.decode(nativeTime);
    if (decoded.isInfinite()) return DecimalSimTime.infinity();
    BigDecimal local = decoded.value();
    validateLattice(local);
    if (!codec.decode(codec.encode(decoded)).equals(decoded)) throw new ArithmeticException("Native codec does not round trip");
    return DecimalSimTime.of(local.multiply(semantics.secondsPerUnit()).add(semantics.origin()).add(alignment));
  }
  private DecimalSimTime toNative(DecimalSimTime canonical) {
    if (canonical.isInfinite()) return canonical;
    BigDecimal numerator = canonical.value().subtract(alignment).subtract(semantics.origin());
    return DecimalSimTime.of(inverseContext == null
        ? numerator.divide(semantics.secondsPerUnit())
        : numerator.divide(semantics.secondsPerUnit(), inverseContext));
  }

  private void validateError(DecimalSimTime requested, DecimalSimTime effective) {
    if (isApproximate() && !requested.isInfinite() && !effective.isInfinite()
        && requested.value().subtract(effective.value()).abs().compareTo(maximumError) > 0) {
      throw new ArithmeticException("Approximate mapping exceeds configured canonical error");
    }
  }

  private N encode(DecimalSimTime local) {
    if (isApproximate() && codec instanceof DoubleTimeCodec doubleCodec) {
      @SuppressWarnings("unchecked")
      N encoded = (N) doubleCodec.encodeApproximate(local);
      return encoded;
    }
    return codec.encode(local);
  }

  public N exact(DecimalSimTime canonical) {
    DecimalSimTime local = toNative(canonical);
    if (local.isInfinite()) return encode(DecimalSimTime.infinity());
    validateLattice(local.value());
    N encoded = encode(local);
    DecimalSimTime effective = toCanonical(encoded);
    if (!isApproximate() && !effective.equals(canonical)) {
      throw new ArithmeticException("Exact mapping does not round trip");
    }
    validateError(canonical, effective);
    return encoded;
  }

  /**
   * Converts a canonical delivery time. Exact mappings reject unrepresentable values; approximate
   * double mappings move at most to the next finite native double when needed to preserve causality.
   */
  public N nextRepresentable(DecimalSimTime canonical) {
    N encoded = exact(canonical);
    if (!isApproximate() || canonical.isInfinite()) {
      return encoded;
    }
    DecimalSimTime effective = toCanonical(encoded);
    if (effective.compareTo(canonical) >= 0) {
      return encoded;
    }
    if (!(encoded instanceof DoubleSimTime doubleTime)) {
      throw new IllegalStateException("Approximate mapping did not encode a double timestamp");
    }
    double next = Math.nextUp(doubleTime.getT());
    if (!Double.isFinite(next) || next == Double.MAX_VALUE) {
      throw new ArithmeticException("No finite double timestamp at or after delivery time");
    }
    @SuppressWarnings("unchecked")
    N advanced = (N) DoubleSimTime.create(next);
    effective = toCanonical(advanced);
    if (effective.compareTo(canonical) < 0) {
      throw new ArithmeticException("Approximate double conversion did not preserve causality");
    }
    validateError(canonical, effective);
    return advanced;
  }
}
