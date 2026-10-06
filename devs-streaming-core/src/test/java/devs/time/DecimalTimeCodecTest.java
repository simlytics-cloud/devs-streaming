package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.QuantizedSimTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalTimeCodecTest {
  private final DecimalTimeCodec codec = new DecimalTimeCodec();

  @Test
  void ceilingPreservesEveryTerminatingBoundWithoutRestrictingExactPrecision() {
    DecimalTimeCodec coarse = new DecimalTimeCodec(2);
    assertEquals(18, codec.adaptationScale());
    assertEquals(2, coarse.adaptationScale());
    assertThrows(IllegalArgumentException.class, () -> new DecimalTimeCodec(-1));
    for (String text : new String[]{"0", "0.12345678901234567890123456789",
        "-0.0000000000000000000000000001", "1E+100", "1E-100"}) {
      DecimalSimTime expected = DecimalSimTime.of(new BigDecimal(text));
      QuantizedSimTime bound = codec.decode(expected);
      assertEquals(expected, coarse.encodeCeiling(bound));
      assertEquals(expected, coarse.encodeCeiling(bound, Rational.of(7), null));
      assertEquals(expected, coarse.encode(bound));
      assertEquals(bound, coarse.decode(expected));
    }
  }

  @Test
  void nonterminatingCeilingsUseImmutableScaleAndRoundTowardPositiveInfinity() {
    DecimalTimeCodec coarse = new DecimalTimeCodec(2);
    assertEquals(DecimalSimTime.of(new BigDecimal("0.333333333333333334")),
        codec.encodeCeiling(QuantizedSimTime.of(Rational.of(1, 3))));
    assertEquals(DecimalSimTime.of(new BigDecimal("-0.333333333333333333")),
        codec.encodeCeiling(QuantizedSimTime.of(Rational.of(-1, 3))));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.34")),
        coarse.encodeCeiling(QuantizedSimTime.of(Rational.of(1, 3))));
    assertEquals(DecimalSimTime.of(new BigDecimal("-0.33")),
        coarse.encodeCeiling(QuantizedSimTime.of(Rational.of(-1, 3))));
    assertEquals(DecimalSimTime.of(new BigDecimal("-1.33")),
        coarse.encodeCeiling(QuantizedSimTime.of(Rational.of(-4, 3))));
    assertEquals(DecimalSimTime.of(BigDecimal.ZERO),
        coarse.encodeCeiling(QuantizedSimTime.of(Rational.of(-1, 3000))));
    assertEquals(DecimalSimTime.of(BigDecimal.ONE),
        new DecimalTimeCodec(0).encodeCeiling(QuantizedSimTime.of(Rational.of(1, 3))));
    assertFalse(coarse.isRepresentable(Rational.of(1, 3)));
    assertThrows(ArithmeticException.class,
        () -> coarse.encode(QuantizedSimTime.of(Rational.of(1, 3))));
    assertEquals(DecimalSimTime.infinity(), coarse.encodeCeiling(QuantizedSimTime.infinity()));
  }

  @Test
  void ceilingUsesExactGridIntersectionNotTheFallbackScale() {
    DecimalTimeCodec coarse = new DecimalTimeCodec(2);
    assertEquals(DecimalSimTime.of(new BigDecimal("0.334")), coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.of(1, 3)), Rational.ZERO, Rational.of(1, 1000)));
    assertEquals(DecimalSimTime.of(new BigDecimal("-0.333")), coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.of(-1, 3)), Rational.ZERO, Rational.of(1, 1000)));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.334")), coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.of(3331, 10000)), Rational.ZERO, Rational.of(1, 1000)));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.333")), coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.of(333, 1000)), Rational.ZERO, Rational.of(1, 1000)));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.3345")), coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.of(1, 3)), Rational.of(1, 2000), Rational.of(2, 1000)));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.001")), coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.of(1, 3000)), Rational.ZERO, Rational.of(1, 3000)));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.0005")), coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 6000), Rational.of(1, 3000)));
    assertThrows(ArithmeticException.class, () -> coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 3), Rational.of(1, 2)));
    assertThrows(IllegalArgumentException.class, () -> coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.ZERO, Rational.ZERO));
    assertThrows(IllegalArgumentException.class, () -> coarse.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.ZERO, Rational.ONE.negate()));
    assertEquals(DecimalSimTime.infinity(), coarse.encodeCeiling(
        QuantizedSimTime.infinity(), Rational.ZERO, Rational.of(1, 3000)));
  }

  @Test
  void finiteDecimalsRoundTripExactlyWithNormalizedScale() {
    for (String text : new String[]{"0.000", "-0.1250", "1E+100", "1E-100", "123.4500"}) {
      DecimalSimTime nativeTime = DecimalSimTime.of(new BigDecimal(text));
      assertEquals(nativeTime, codec.encode(codec.decode(nativeTime)));
      assertEquals(Rational.of(new BigDecimal(text)), codec.decode(nativeTime).value());
      assertTrue(codec.isRepresentable(codec.decode(nativeTime).value()));
    }
    assertEquals(DecimalSimTime.of(new BigDecimal("0.125")),
        codec.encode(QuantizedSimTime.of(Rational.of(1, 8))));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.04")),
        codec.encode(QuantizedSimTime.of(Rational.of(1, 25))));
  }

  @Test
  void rejectsNonterminatingDecimalsIncludingContinuousSuccessorsWithNoMinimum() {
    assertFalse(codec.isRepresentable(Rational.of(1, 3)));
    assertThrows(ArithmeticException.class, () -> codec.encode(QuantizedSimTime.of(Rational.of(1, 3))));
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(
        QuantizedSimTime.of(Rational.of(1, 3)), Rational.ZERO, null));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.1")), codec.nextRepresentable(
        QuantizedSimTime.of(Rational.of(1, 10)), Rational.ZERO, null));
  }

  @Test
  void successorIntersectsDecimalEncodingAndTheLatticeExactly() {
    assertEquals(DecimalSimTime.of(BigDecimal.ONE), codec.nextRepresentable(
        QuantizedSimTime.of(Rational.of(1, 3)), Rational.ZERO, Rational.of(1, 3)));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.1")), codec.nextRepresentable(
        QuantizedSimTime.of(Rational.of(1, 30)), Rational.ZERO, Rational.of(1, 30)));
    assertEquals(DecimalSimTime.of(new BigDecimal("-0.1")), codec.nextRepresentable(
        QuantizedSimTime.of(Rational.of(-2, 15)), Rational.ZERO, Rational.of(1, 30)));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.5")), codec.nextRepresentable(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 6), Rational.of(1, 3)));
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 3), Rational.of(1, 2)));
  }

  @Test
  void decimalTimeArithmeticEqualityAndInfinityAreConsistent() {
    DecimalSimTime a = DecimalSimTime.of(new BigDecimal("1.200"));
    DecimalSimTime b = DecimalSimTime.of(new BigDecimal("1.2"));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(0, a.compareTo(b));
    assertNotEquals(a, null);
    assertEquals(DecimalSimTime.of(new BigDecimal("2.4")), a.plus(b));
    assertEquals(DecimalSimTime.of(BigDecimal.ZERO), a.minus(b));
    assertEquals(DecimalSimTime.of(BigDecimal.ZERO), a.createZeroTime());
    DecimalSimTime infinity = DecimalSimTime.infinity();
    assertTrue(codec.decode(infinity).isInfinite());
    assertEquals(infinity, codec.encode(QuantizedSimTime.infinity()));
    assertEquals(infinity, codec.nextRepresentable(QuantizedSimTime.infinity(), Rational.ZERO, null));
    assertTrue(infinity.compareTo(a) > 0);
    assertTrue(a.compareTo(infinity) < 0);
    assertEquals(0, infinity.compareTo(DecimalSimTime.infinity()));
    assertEquals(infinity, a.plus(infinity));
    assertEquals(infinity, infinity.plus(a));
    assertEquals(infinity, infinity.plus(infinity));
    assertEquals(infinity, infinity.minus(a));
    assertEquals(infinity, a.getTimeUntilMax());
    assertEquals(infinity, a.getMaxValue());
    assertThrows(ArithmeticException.class, () -> infinity.minus(infinity));
    assertThrows(ArithmeticException.class, () -> a.minus(infinity));
    assertThrows(ArithmeticException.class, infinity::getTimeUntilMax);
    assertThrows(ArithmeticException.class, infinity::value);
  }
}