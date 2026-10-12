package devs.time;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.SimTime;
import java.math.BigDecimal;

/** Encodes native decimal numerical values before units, origin, or alignment are applied. */
public interface NativeTimeCodec<N extends SimTime> {
  DecimalSimTime decode(N value);
  N encode(DecimalSimTime value);

  default boolean isRepresentable(BigDecimal value) {
    try {
      DecimalSimTime time = DecimalSimTime.of(value);
      return decode(encode(time)).equals(time);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      return false;
    }
  }
}
