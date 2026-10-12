package devs.time;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import java.math.BigDecimal;

/** Legacy binary floating-point codec. It is not permitted in decimal-closed enhanced mappings. */
public final class DoubleTimeCodec implements NativeTimeCodec<DoubleSimTime> {
  @Override public DecimalSimTime decode(DoubleSimTime value) {
    double number = value.getT();
    if (number == Double.MAX_VALUE) return DecimalSimTime.infinity();
    if (!Double.isFinite(number)) throw new ArithmeticException("Non-finite double time");
    return DecimalSimTime.of(BigDecimal.valueOf(number));
  }
  @Override public DoubleSimTime encode(DecimalSimTime value) {
    if (value.isInfinite()) return DoubleSimTime.create(Double.MAX_VALUE);
    double number = value.value().doubleValue();
    if (!Double.isFinite(number) || number == Double.MAX_VALUE
        || BigDecimal.valueOf(number).compareTo(value.value()) != 0) {
      throw new ArithmeticException("Decimal cannot be represented exactly as a double time");
    }
    return DoubleSimTime.create(number);
  }

  /** Encodes the nearest finite native double for an explicitly approximate mapping. */
  public DoubleSimTime encodeApproximate(DecimalSimTime value) {
    if (value.isInfinite()) {
      return DoubleSimTime.create(Double.MAX_VALUE);
    }
    double number = value.value().doubleValue();
    if (!Double.isFinite(number) || number == Double.MAX_VALUE) {
      throw new ArithmeticException("Decimal is outside finite double time range");
    }
    return DoubleSimTime.create(number);
  }
}
