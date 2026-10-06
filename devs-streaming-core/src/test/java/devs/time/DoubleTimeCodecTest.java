package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.DoubleSimTime;
import devs.iso.time.QuantizedSimTime;
import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DoubleTimeCodecTest {
  private final DoubleTimeCodec codec = new DoubleTimeCodec();

  @Test
  void ceilingApiPreservesBinaryValuesAndIntersectsNonbinaryGrids() {
    for (double value : new double[]{-Double.MAX_VALUE, Math.nextDown(Double.MAX_VALUE),
        -Double.MIN_NORMAL, Double.MIN_NORMAL, -Double.MIN_VALUE, Double.MIN_VALUE,
        -0.1, 0.0, 0.1, Math.nextUp(1.0), Math.scalb(1.0, 53)}) {
      assertEquals(value, codec.encodeCeiling(codec.decode(DoubleSimTime.create(value))).getT());
    }
    double thirdCeiling = Math.nextUp(1.0 / 3.0);
    assertEquals(thirdCeiling, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(1, 3))).getT());
    assertTrue(Rational.of(thirdCeiling).compareTo(Rational.of(1, 3)) > 0);
    assertTrue(Rational.of(Math.nextDown(thirdCeiling)).compareTo(Rational.of(1, 3)) < 0);
    assertEquals(-1.0 / 3.0, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(-1, 3))).getT());
    assertEquals(1.0, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(1, 10)),
        Rational.ZERO, Rational.of(1, 3)).getT());
    assertEquals(1.0, codec.encodeCeiling(QuantizedSimTime.of(Rational.ZERO),
        Rational.of(1, 3), Rational.of(2, 3)).getT());
    assertThrows(ArithmeticException.class, () -> codec.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 3), Rational.of(1, 2)));
    assertThrows(IllegalArgumentException.class, () -> codec.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.ZERO, Rational.ZERO));
  }

  @Test
  void ceilingApiRespectsSubnormalBinadeRangeAndSentinelBoundaries() {
    Rational tiny = Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(1075));
    assertEquals(Double.MIN_VALUE, codec.encodeCeiling(QuantizedSimTime.of(tiny)).getT());
    assertEquals(0.0, codec.encodeCeiling(QuantizedSimTime.of(tiny.negate())).getT());
    assertEquals(Double.MIN_NORMAL, codec.encodeCeiling(QuantizedSimTime.of(
        Rational.of(Double.MIN_NORMAL).subtract(tiny))).getT());
    assertEquals(2.0, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(Math.nextDown(2.0))
        .add(Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(54))))).getT());
    assertEquals(-Double.MAX_VALUE, codec.encodeCeiling(QuantizedSimTime.of(
        Rational.of(-Double.MAX_VALUE).subtract(Rational.ONE))).getT());
    assertThrows(ArithmeticException.class, () -> codec.encodeCeiling(QuantizedSimTime.of(
        Rational.of(Math.nextDown(Double.MAX_VALUE)).add(Rational.ONE))));
    assertThrows(ArithmeticException.class, () -> codec.encodeCeiling(QuantizedSimTime.of(
        Rational.of(Double.MAX_VALUE))));
    assertEquals(Double.MAX_VALUE, codec.encodeCeiling(QuantizedSimTime.infinity()).getT());
  }

  @Test
  void decodingUsesExactBinaryValuesNotDisplayDecimals() {
    assertEquals(Rational.of(3602879701896397L, 36028797018963968L),
        codec.decode(DoubleSimTime.create(0.1)).value());
    assertNotEquals(Rational.of(1, 10), codec.decode(DoubleSimTime.create(0.1)).value());
    assertEquals(Rational.ZERO, codec.decode(DoubleSimTime.create(-0.0)).value());
    assertTrue(codec.decode(DoubleSimTime.create(Double.MAX_VALUE)).isInfinite());
    assertEquals(Double.MAX_VALUE, codec.encode(QuantizedSimTime.infinity()).getT());
  }

  @Test
  void roundTripsBoundaryAndSeededFiniteBinaryValues() {
    for (double value : new double[]{-Double.MAX_VALUE, Math.nextDown(Double.MAX_VALUE),
        -Double.MIN_NORMAL, Double.MIN_NORMAL, -Double.MIN_VALUE, Double.MIN_VALUE,
        -1.5, 0.0, 0.1, Math.nextUp(1.0), Math.scalb(1.0, 53)}) {
      assertEquals(value, codec.encode(codec.decode(DoubleSimTime.create(value))).getT());
    }
    Random random = new Random(9147);
    for (int i = 0; i < 100; i++) {
      double value = Double.longBitsToDouble(random.nextLong());
      if (Double.isFinite(value) && value != Double.MAX_VALUE) {
        assertEquals(value, codec.encode(codec.decode(DoubleSimTime.create(value))).getT());
      }
    }
  }

  @Test
  void rejectsMalformedNonfiniteValuesAndInexactOrOutOfRangeEncoding() {
    for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> codec.decode(DoubleSimTime.create(invalid)));
    }
    for (Rational invalid : new Rational[]{Rational.of(1, 10), Rational.of(1, 3),
        Rational.of(BigInteger.ONE.shiftLeft(53).add(BigInteger.ONE), BigInteger.ONE),
        Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(1075)),
        Rational.of(BigInteger.ONE.shiftLeft(1024), BigInteger.ONE), Rational.of(Double.MAX_VALUE)}) {
      assertThrows(ArithmeticException.class, () -> codec.encode(QuantizedSimTime.of(invalid)));
      assertFalse(codec.isRepresentable(invalid));
    }
  }

  @Test
  void successorOfDecimalLowerBoundIsTheFirstBinaryValueNotBelowIt() {
    assertEquals(0.1, codec.nextRepresentable(QuantizedSimTime.of(Rational.of(1, 10)),
        Rational.ZERO, null).getT());
    assertEquals(Math.nextUp(-0.1), codec.nextRepresentable(QuantizedSimTime.of(Rational.of(-1, 10)),
        Rational.ZERO, null).getT());
  }

  @Test
  void successorIntersectsTheLatticeRatherThanRoundingItsFirstPoint() {
    assertEquals(1.0, codec.nextRepresentable(QuantizedSimTime.of(Rational.of(1, 10)),
        Rational.ZERO, Rational.of(1, 3)).getT());
    assertEquals(-1.0, codec.nextRepresentable(QuantizedSimTime.of(Rational.of(-4, 3)),
        Rational.ZERO, Rational.of(1, 3)).getT());
    assertEquals(1.0, codec.nextRepresentable(QuantizedSimTime.of(Rational.ZERO),
        Rational.of(1, 3), Rational.of(2, 3)).getT());
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 3), Rational.of(1, 2)));
  }

  @Test
  void successorHandlesSubnormalExponentAndPrecisionBoundaries() {
    Rational tiny = Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(1075));
    assertEquals(Double.MIN_VALUE, codec.nextRepresentable(QuantizedSimTime.of(tiny),
        Rational.ZERO, null).getT());
    assertEquals(0.0, codec.nextRepresentable(QuantizedSimTime.of(tiny.negate()),
        Rational.ZERO, null).getT());
    Rational beforeNormal = Rational.of(Double.MIN_NORMAL).subtract(tiny);
    assertEquals(Double.MIN_NORMAL, codec.nextRepresentable(QuantizedSimTime.of(beforeNormal),
        Rational.ZERO, null).getT());
    assertEquals(2.0, codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Math.nextDown(2.0)).add(Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(54)))),
        Rational.ZERO, null).getT());
    Rational beyondPrecision = Rational.of(BigInteger.ONE.shiftLeft(53).add(BigInteger.ONE), BigInteger.ONE);
    assertEquals(Math.scalb(1.0, 53) + 2, codec.nextRepresentable(QuantizedSimTime.of(beyondPrecision),
        Rational.ZERO, Rational.ONE).getT());
  }

  @Test
  void successorHandlesFiniteRangeWithoutTreatingSentinelAsFinite() {
    assertEquals(-Double.MAX_VALUE, codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(-Double.MAX_VALUE).subtract(Rational.ONE)), Rational.ZERO, null).getT());
    assertEquals(Math.nextDown(Double.MAX_VALUE), codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Math.nextDown(Double.MAX_VALUE))), Rational.ZERO, null).getT());
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Math.nextDown(Double.MAX_VALUE)).add(Rational.ONE)), Rational.ZERO, null));
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Double.MAX_VALUE)), Rational.ZERO, null));
    assertEquals(Double.MAX_VALUE, codec.nextRepresentable(QuantizedSimTime.infinity(),
        Rational.ZERO, null).getT());
  }
}