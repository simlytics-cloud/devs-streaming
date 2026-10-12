package devs.time;

import static org.junit.jupiter.api.Assertions.assertSame;

import devs.iso.time.DecimalSimTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalTimeCodecTest {
  @Test
  void preservesFiniteDecimalAndInfinityExactly() {
    var codec = new DecimalTimeCodec();
    var finite = DecimalSimTime.of(new BigDecimal("0.125"));
    assertSame(finite, codec.encode(finite));
    assertSame(finite, codec.decode(finite));
    assertSame(DecimalSimTime.infinity(), codec.encode(DecimalSimTime.infinity()));
  }
}
