package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.LongSimTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LongTimeCodecTest {
  private final LongTimeCodec codec = new LongTimeCodec();
  @Test void roundTripsIntegralDecimalTimes() {
    var value = DecimalSimTime.of(new BigDecimal("-42"));
    assertEquals(value, codec.decode(codec.encode(value)));
  }
  @Test void rejectsNonIntegralFiniteDecimalsAndReservesInfinitySentinel() {
    assertThrows(ArithmeticException.class, () -> codec.encode(DecimalSimTime.of(new BigDecimal("0.1"))));
    assertEquals(DecimalSimTime.infinity(), codec.decode(LongSimTime.create(Long.MAX_VALUE)));
    assertEquals(Long.MAX_VALUE, codec.encode(DecimalSimTime.infinity()).getT());
  }
}
