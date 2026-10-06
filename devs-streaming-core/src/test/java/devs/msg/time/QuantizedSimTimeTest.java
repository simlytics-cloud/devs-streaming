package devs.msg.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.time.Rational;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class QuantizedSimTimeTest {
  @Test
  void finiteArithmeticDoesNotQuantizeOrOverflow() {
    QuantizedSimTime a = QuantizedSimTime.of(Rational.of(1, 3));
    QuantizedSimTime b = QuantizedSimTime.of(Rational.of(1, 7));
    assertEquals(QuantizedSimTime.of(Rational.of(10, 21)), a.plus(b));
    assertEquals(QuantizedSimTime.of(Rational.of(-4, 21)), b.minus(a));
    QuantizedSimTime huge = QuantizedSimTime.of(
        Rational.of(BigInteger.ONE.shiftLeft(200), BigInteger.ONE));
    assertEquals(a, huge.plus(a).minus(huge));
    assertEquals(QuantizedSimTime.of(Rational.ZERO), a.createZeroTime());
  }

  @Test
  void equalityHashAndOrderingUseNormalizedValues() {
    QuantizedSimTime a = QuantizedSimTime.of(Rational.of(2, 6));
    QuantizedSimTime b = QuantizedSimTime.of(Rational.of(1, 3));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(0, a.compareTo(b));
    assertTrue(a.compareTo(QuantizedSimTime.of(Rational.of(-1))) > 0);
    assertNotEquals(a, null);
    assertNotEquals(a, LongSimTime.create(0));
    assertNotEquals(a, QuantizedSimTime.infinity());
    assertThrows(NullPointerException.class, () -> QuantizedSimTime.of(null));
  }

  @Test
  void infinityIsDistinguishedOrderedAndAbsorbsFiniteAddition() {
    QuantizedSimTime infinity = QuantizedSimTime.infinity();
    QuantizedSimTime finite = QuantizedSimTime.of(Rational.of(-99, 7));
    assertTrue(infinity.isInfinite());
    assertFalse(finite.isInfinite());
    assertEquals(0, infinity.compareTo(QuantizedSimTime.infinity()));
    assertTrue(infinity.compareTo(finite) > 0);
    assertTrue(finite.compareTo(infinity) < 0);
    assertEquals(infinity, finite.plus(infinity));
    assertEquals(infinity, infinity.plus(finite));
    assertEquals(infinity, infinity.plus(infinity));
    assertEquals(infinity, infinity.minus(finite));
    assertEquals(infinity, finite.getMaxValue());
    assertEquals(infinity, finite.getTimeUntilMax());
    assertEquals(infinity.hashCode(), finite.getMaxValue().hashCode());
  }

  @Test
  void indeterminateAndNegativeInfinityOperationsFailExplicitly() {
    QuantizedSimTime infinity = QuantizedSimTime.infinity();
    QuantizedSimTime finite = QuantizedSimTime.of(Rational.ONE);
    assertThrows(ArithmeticException.class, () -> infinity.minus(infinity));
    assertThrows(ArithmeticException.class, () -> finite.minus(infinity));
    assertThrows(ArithmeticException.class, infinity::getTimeUntilMax);
    assertThrows(ArithmeticException.class, infinity::value);
  }
}