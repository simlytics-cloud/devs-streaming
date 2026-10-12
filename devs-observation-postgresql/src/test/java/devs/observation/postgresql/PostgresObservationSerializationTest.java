package devs.observation.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devs.iso.time.DecimalSimTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PostgresObservationSerializationTest {
  @Test
  void decimalObservationTimestampNormalizesExactly() {
    assertEquals(DecimalSimTime.of(new BigDecimal("42.5")),
        DecimalSimTime.of(new BigDecimal("42.500")));
  }
}
