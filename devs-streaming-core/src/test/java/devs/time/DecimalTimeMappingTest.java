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
  void adaptsLongDeliveriesUpwardWithoutWeakeningExactConversion() {
    var mapping = new DecimalTimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO));

    assertEquals(10001, mapping.nextRepresentable(
        DecimalSimTime.of(new BigDecimal("10000.0131"))).getT());
    assertEquals(10001, mapping.nextRepresentable(
        DecimalSimTime.of(new BigDecimal("10001"))).getT());
    assertEquals(-1, mapping.nextRepresentable(
        DecimalSimTime.of(new BigDecimal("-1.2"))).getT());
    assertThrows(ArithmeticException.class,
        () -> mapping.exact(DecimalSimTime.of(new BigDecimal("10000.0131"))));
  }

  @Test
  void adaptsLongDeliveriesWithNonterminatingInverse() {
    var mapping = new DecimalTimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(BigDecimal.valueOf(3), BigDecimal.ZERO));

    LongSimTime adapted = mapping.nextRepresentable(DecimalSimTime.of(BigDecimal.ONE));
    assertEquals(1, adapted.getT());
    assertEquals(1, mapping.toCanonical(adapted).compareTo(DecimalSimTime.of(BigDecimal.ONE)));
    assertThrows(ArithmeticException.class,
        () -> mapping.exact(DecimalSimTime.of(BigDecimal.ONE)));
  }

  @Test
  void adaptsToIntegerIntersectionOfDeclaredGrid() {
    var mapping = new DecimalTimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO,
            Optional.of(TimeGranularity.of(new BigDecimal("1.5")))));

    assertEquals(6, mapping.nextRepresentable(
        DecimalSimTime.of(new BigDecimal("3.1"))).getT());
    assertEquals(-3, mapping.nextRepresentable(
        DecimalSimTime.of(new BigDecimal("-3.1"))).getT());
  }

  @Test
  void adaptsUsingScaleOriginAndAlignment() {
    var mapping = new DecimalTimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(new BigDecimal("0.5"), new BigDecimal("2.25")),
        new BigDecimal("0.25"));

    LongSimTime adapted = mapping.nextRepresentable(DecimalSimTime.of(new BigDecimal("7.1")));
    assertEquals(10, adapted.getT());
    assertEquals(0, mapping.toCanonical(adapted).compareTo(DecimalSimTime.of(new BigDecimal("7.5"))));
  }

  @Test
  void rejectsLongDeliveryCeilingsOutsideFiniteRange() {
    var mapping = new DecimalTimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO));

    BigDecimal maximum = BigDecimal.valueOf(Long.MAX_VALUE);
    assertThrows(ArithmeticException.class, () -> mapping.nextRepresentable(
        DecimalSimTime.of(maximum.subtract(new BigDecimal("0.5")))));
    assertThrows(ArithmeticException.class, () -> mapping.nextRepresentable(
        DecimalSimTime.of(maximum.add(BigDecimal.ONE))));
    assertThrows(ArithmeticException.class, () -> mapping.nextRepresentable(
        DecimalSimTime.of(BigDecimal.valueOf(Long.MIN_VALUE).subtract(BigDecimal.valueOf(2)))));
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
