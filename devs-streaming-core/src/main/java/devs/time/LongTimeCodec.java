package devs.time;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.LongSimTime;
import java.math.BigDecimal;

/** Exact integer codec reserving {@link Long#MAX_VALUE} for infinity. */
public final class LongTimeCodec implements NativeTimeCodec<LongSimTime> {
  @Override public DecimalSimTime decode(LongSimTime value) {
    return value.getT() == Long.MAX_VALUE ? DecimalSimTime.infinity()
        : DecimalSimTime.of(BigDecimal.valueOf(value.getT()));
  }
  @Override public LongSimTime encode(DecimalSimTime value) {
    if (value.isInfinite()) return LongSimTime.create(Long.MAX_VALUE);
    long encoded = value.value().longValueExact();
    if (encoded == Long.MAX_VALUE) throw new ArithmeticException("Finite value collides with long infinity sentinel");
    return LongSimTime.create(encoded);
  }
}
