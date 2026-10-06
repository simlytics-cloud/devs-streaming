package devs.time;

import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import java.util.Objects;

/** canonical seconds = alignment + native epoch offset + native value * secondsPerUnit. */
public final class TimeMapping<N extends SimTime> {
  private final NativeTimeCodec<N> codec;
  private final TimeSemantics semantics;
  private final Rational alignment;

  /**
   * Creates a mapping with an additional canonical-clock alignment in seconds.
   * The codec operates in native units; the semantics supply the positive unit scale,
   * epoch offset in seconds, and optional native-unit lattice.
   * @throws NullPointerException if any argument is null
   */
  public TimeMapping(NativeTimeCodec<N> codec, TimeSemantics semantics, Rational alignment) {
    this.codec = Objects.requireNonNull(codec);
    this.semantics = Objects.requireNonNull(semantics);
    this.alignment = Objects.requireNonNull(alignment);
  }

  /** Creates a mapping with zero additional alignment. */
  public TimeMapping(NativeTimeCodec<N> codec, TimeSemantics semantics) {
    this(codec, semantics, Rational.ZERO);
  }

  /** Returns the exact native numerical encoding boundary. */
  public NativeTimeCodec<N> codec() {
    return codec;
  }

  /** Returns the native units, epoch, and accepted timestamp lattice. */
  public TimeSemantics semantics() {
    return semantics;
  }

  /** Returns the additional canonical-clock offset in seconds. */
  public Rational alignment() {
    return alignment;
  }

  private void validateLattice(Rational value) {
    semantics.timeGranularity().ifPresent(granularity -> {
      if (!value.isOnLattice(Rational.ZERO, granularity.spacing())) {
        throw new ArithmeticException("Native time is off its declared granularity grid");
      }
    });
  }

  /**
   * Converts native time to exact canonical seconds, preserving logical infinity.
   * Finite native values must lie on the declared zero-based lattice and round trip
   * exactly through the codec before units and offsets are applied.
   * @throws ArithmeticException if lattice validation or exact round-trip validation fails
   */
  public QuantizedSimTime toCanonical(N nativeTime) {
    QuantizedSimTime decoded = codec.decode(nativeTime);
    if (decoded.isInfinite()) {
      return QuantizedSimTime.infinity();
    }
    validateLattice(decoded.value());
    if (!codec.decode(codec.encode(decoded)).equals(decoded)) {
      throw new ArithmeticException("Native codec does not round trip");
    }
    return QuantizedSimTime.of(decoded.value().multiply(semantics.secondsPerUnit())
        .add(semantics.origin()).add(alignment));
  }

  private QuantizedSimTime toNative(QuantizedSimTime canonical) {
    return canonical.isInfinite() ? canonical : QuantizedSimTime.of(canonical.value()
        .subtract(alignment).subtract(semantics.origin()).divide(semantics.secondsPerUnit()));
  }

  /**
   * Converts canonical seconds to native time without rounding, preserving infinity.
   * @throws ArithmeticException if the native value is off-lattice, not exactly encodable,
   *     or fails the canonical round trip
   */
  public N exact(QuantizedSimTime canonical) {
    QuantizedSimTime nativeValue = toNative(canonical);
    if (!nativeValue.isInfinite()) {
      validateLattice(nativeValue.value());
    }
    N encoded = codec.encode(nativeValue);
    if (!toCanonical(encoded).equals(canonical)) {
      throw new ArithmeticException("Exact mapping does not round trip");
    }
    return encoded;
  }

  /**
   * Adapts to an accepted native timestamp whose canonical time is not below
   * the supplied canonical-second bound, using the codec's ceiling policy. On an
   * explicit granularity grid this is the least accepted timestamp; without a grid,
   * decimal nonterminating bounds use the codec's fallback scale instead.
   * Already accepted times are unchanged; infinity is preserved, but a finite bound
   * must yield a finite result.
   * @throws ArithmeticException if no accepted finite ceiling exists or
   *     codec round-trip or lattice validation fails
   */
  public N nextRepresentable(QuantizedSimTime canonical) {
    N encoded = codec.encodeCeiling(toNative(canonical), Rational.ZERO,
        semantics.timeGranularity().map(TimeGranularity::spacing).orElse(null));
    QuantizedSimTime result = toCanonical(encoded);
    if (result.compareTo(canonical) < 0 || (!canonical.isInfinite() && result.isInfinite())) {
      throw new ArithmeticException("No finite representable successor");
    }
    return encoded;
  }
}