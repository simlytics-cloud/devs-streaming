package devs.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import devs.iso.time.DecimalSimTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EnhancedPDevsSimulationTest {
  @Test
  void decimalOnlyTimeNormalizesExactly() {
    assertEquals(DecimalSimTime.of(new BigDecimal("1.25")),
        DecimalSimTime.of(new BigDecimal("1.2500")));
  }
}
