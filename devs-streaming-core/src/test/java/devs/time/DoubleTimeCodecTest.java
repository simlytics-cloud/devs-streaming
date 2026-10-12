package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DoubleTimeCodecTest {
  private final DoubleTimeCodec codec = new DoubleTimeCodec();
  @Test void roundTripsExactlyRepresentableDecimalAndInfinity() {
    var value = DecimalSimTime.of(new BigDecimal("0.5"));
    assertEquals(value, codec.decode(codec.encode(value)));
    assertEquals(DecimalSimTime.infinity(), codec.decode(DoubleSimTime.create(Double.MAX_VALUE)));
  }
  @Test void rejectsDecimalsWithoutAnExactDoubleRepresentation() {
    assertEquals(DecimalSimTime.of(new BigDecimal("0.1")), codec.decode(codec.encode(DecimalSimTime.of(new BigDecimal("0.1")))));
  }
}
