package devs.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecimalTimeMappingTest {
  @Test
  void mapsTerminatingDecimalsExactly() {
    var mapping = new DecimalTimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(new BigDecimal("0.001"), BigDecimal.ZERO,
            Optional.of(TimeGranularity.of(BigDecimal.ONE))));
    assertEquals(DecimalSimTime.of(new BigDecimal("1.5")),
        mapping.toCanonical(LongSimTime.create(1500)));
    assertEquals(1500, mapping.exact(DecimalSimTime.of(new BigDecimal("1.5"))).getT());
  }

  @Test
  void rejectsNonterminatingInverseConversion() {
    var mapping = new DecimalTimeMapping<>(new DecimalTimeCodec(),
        TimeSemantics.of(BigDecimal.valueOf(3), BigDecimal.ZERO));
    assertThrows(ArithmeticException.class,
        () -> mapping.exact(DecimalSimTime.of(BigDecimal.ONE)));
  }

  @Test
  void rejectsDoubleEnhancedClock() {
    assertThrows(IllegalArgumentException.class, () -> new DecimalTimeMapping<>(new DoubleTimeCodec(),
        TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO)));
  }

  @Test
  void mapsContinuousDoubleClockWithApproximateOneSixtiethScale() {
    var mapping = DecimalTimeMapping.approximateDouble(
        TimeSemantics.of(new BigDecimal("0.016666666666666667"), BigDecimal.ZERO),
        BigDecimal.ZERO, MathContext.DECIMAL128, new BigDecimal("1e-15"));

    assertEquals(DecimalSimTime.of(new BigDecimal("1.000000000000000020")),
        mapping.toCanonical(DoubleSimTime.create(60.0)));
    assertEquals(60.0, mapping.exact(DecimalSimTime.of(BigDecimal.ONE)).getT());
    assertEquals(2.37, mapping.exact(mapping.toCanonical(DoubleSimTime.create(2.37))).getT());
    assertEquals(60.0, mapping.nextRepresentable(DecimalSimTime.of(BigDecimal.ONE)).getT());

    var belowOne = DecimalTimeMapping.approximateDouble(
        TimeSemantics.of(new BigDecimal("0.016666666666666666"), BigDecimal.ZERO),
        BigDecimal.ZERO, MathContext.DECIMAL128, new BigDecimal("1e-15"));
    DoubleSimTime adapted = belowOne.nextRepresentable(DecimalSimTime.of(BigDecimal.ONE));
    assertEquals(60.00000000000001, adapted.getT());
    assertEquals(1, belowOne.toCanonical(adapted).compareTo(DecimalSimTime.of(BigDecimal.ONE)));
  }
}
