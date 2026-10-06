package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class RationalTest {
  @Test
  void normalizesSignsZeroAndEquality() {
    assertEquals(Rational.of(-2, 3), Rational.of(4, -6));
    assertEquals(Rational.of(2, 3), Rational.of(-4, -6));
    assertEquals(BigInteger.ONE, Rational.of(0, -99).denominator());
    assertEquals(Rational.of(2, 3).hashCode(), Rational.of(4, 6).hashCode());
    assertEquals("-2/3", Rational.of(4, -6).toString());
    assertEquals("0", Rational.ZERO.toString());
    assertThrows(ArithmeticException.class, () -> Rational.of(1, 0));
  }

  @Test
  void arithmeticAndOrderingAreExactBeyondLongRange() {
    Rational a = Rational.of(2, 3);
    Rational b = Rational.of(-5, 7);
    assertEquals(Rational.of(-1, 21), a.add(b));
    assertEquals(Rational.of(29, 21), a.subtract(b));
    assertEquals(Rational.of(-10, 21), a.multiply(b));
    assertEquals(Rational.of(-14, 15), a.divide(b));
    assertThrows(ArithmeticException.class, () -> a.divide(Rational.ZERO));
    BigInteger large = BigInteger.ONE.shiftLeft(300);
    Rational huge = Rational.of(large, BigInteger.ONE);
    assertEquals(Rational.of(large.multiply(large), BigInteger.ONE), huge.multiply(huge));
    assertEquals(Rational.ONE, huge.add(Rational.ONE).subtract(huge));
    assertTrue(huge.add(a).compareTo(huge) > 0);
    assertTrue(huge.negate().compareTo(b) < 0);
  }

  @Test
  void floorsAndCeilingsWorkForNegativeAndIntegralValues() {
    assertEquals(BigInteger.valueOf(2), Rational.of(4, 3).ceil());
    assertEquals(BigInteger.ONE, Rational.of(4, 3).floor());
    assertEquals(BigInteger.valueOf(-1), Rational.of(-4, 3).ceil());
    assertEquals(BigInteger.valueOf(-2), Rational.of(-4, 3).floor());
    assertEquals(BigInteger.valueOf(-2), Rational.of(-2).ceil());
    assertEquals(BigInteger.ZERO, Rational.ZERO.floor());
  }

  @Test
  void latticeCeilingUsesTheSuppliedOriginWithoutAdvancingAnExactPoint() {
    Rational origin = Rational.of(-1, 3);
    Rational quantum = Rational.of(1, 4);
    assertTrue(origin.isOnLattice(origin, quantum));
    assertFalse(Rational.ZERO.isOnLattice(origin, quantum));
    assertEquals(Rational.of(1, 6), Rational.ZERO.ceilToLattice(origin, quantum));
    assertEquals(origin, origin.ceilToLattice(origin, quantum));
    assertEquals(Rational.of(-7, 12), Rational.of(-3, 4).ceilToLattice(origin, quantum));
    assertThrows(IllegalArgumentException.class,
        () -> Rational.ZERO.ceilToLattice(origin, Rational.ZERO));
    assertThrows(IllegalArgumentException.class,
        () -> Rational.ZERO.isOnLattice(origin, Rational.of(-1)));
  }

  @Test
  void decimalConversionHandlesPositiveNegativeAndZeroScaleExactly() {
    assertEquals(Rational.of(123, 100), Rational.of(new BigDecimal("1.2300")));
    assertEquals(Rational.of(-12000), Rational.of(new BigDecimal("-1.2E+4")));
    assertEquals(new BigDecimal("0.125"), Rational.of(1, 8).toBigDecimal());
    assertThrows(ArithmeticException.class, () -> Rational.of(1, 3).toBigDecimal());
  }

  @Test
  void binaryConversionRetainsTheExactBitsIncludingSubnormals() {
    assertEquals(Rational.of(3602879701896397L, 36028797018963968L), Rational.of(0.1));
    assertNotEquals(Rational.of(1, 10), Rational.of(0.1));
    assertEquals(Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(1074)),
        Rational.of(Double.MIN_VALUE));
    assertEquals(Rational.ZERO, Rational.of(-0.0));
    assertEquals(Rational.of(-3, 2), Rational.of(-1.5));
    for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> Rational.of(invalid));
    }
  }

  @Test
  void intersectionCeilingSolvesCompatibleAndIncompatibleGrids() {
    assertEquals(Rational.ONE,
        Rational.intersectionCeiling(Rational.of(1, 10), Rational.ZERO,
            Rational.of(1, 3), Rational.of(1, 8)));
    assertEquals(Rational.of(-1),
        Rational.intersectionCeiling(Rational.of(-3, 2), Rational.of(1, 3),
            Rational.of(2, 3), Rational.ONE));
    assertNull(Rational.intersectionCeiling(Rational.ZERO, Rational.of(1, 3),
        Rational.of(1, 2), Rational.ONE));
    assertEquals(Rational.of(1, 4), Rational.intersectionCeiling(Rational.of(1, 5),
        Rational.ZERO, null, Rational.of(1, 4)));
  }

  @Test
  void intersectionMatchesAnIndependentSmallIntegerGridSearch() {
    for (int offset = -6; offset <= 6; offset++) {
      for (int step = 1; step <= 8; step++) {
        Rational origin = Rational.of(offset, 6);
        Rational quantum = Rational.of(step, 6);
        Rational lower = Rational.of(-7, 5);
        Rational expected = null;
        for (int integer = -1; integer <= 20; integer++) {
          Rational point = Rational.of(integer);
          if (point.isOnLattice(origin, quantum)) {
            expected = point;
            break;
          }
        }
        assertEquals(expected, Rational.intersectionCeiling(lower, origin, quantum, Rational.ONE),
            "origin=" + origin + ", quantum=" + quantum);
      }
    }
  }
}