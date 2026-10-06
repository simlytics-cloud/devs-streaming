package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class LongTimeCodecTest {
  private final LongTimeCodec codec = new LongTimeCodec();

  @Test
  void ceilingApiPreservesAcceptedValuesAndUsesIntegerIntersection() {
    for (long value : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE - 1}) {
      assertEquals(value, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(value))).getT().longValue());
    }
    assertEquals(1L, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(1, 3))).getT().longValue());
    assertEquals(0L, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(-1, 3))).getT().longValue());
    assertEquals(-1L, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(-4, 3))).getT().longValue());
    assertEquals(2L, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(3, 2)),
        Rational.ZERO, Rational.of(2, 3)).getT().longValue());
    assertEquals(-1L, codec.encodeCeiling(QuantizedSimTime.of(Rational.of(-3, 2)),
        Rational.of(1, 3), Rational.of(2, 3)).getT().longValue());
    assertThrows(ArithmeticException.class, () -> codec.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 3), Rational.of(1, 2)));
    assertThrows(IllegalArgumentException.class, () -> codec.encodeCeiling(
        QuantizedSimTime.of(Rational.ZERO), Rational.ZERO, Rational.ZERO));
    assertEquals(Long.MIN_VALUE, codec.encodeCeiling(QuantizedSimTime.of(
        Rational.of(Long.MIN_VALUE).subtract(Rational.ONE))).getT().longValue());
    assertThrows(ArithmeticException.class, () -> codec.encodeCeiling(QuantizedSimTime.of(
        Rational.of(Long.MAX_VALUE).subtract(Rational.of(1, 2)))));
    assertEquals(Long.MAX_VALUE, codec.encodeCeiling(QuantizedSimTime.infinity()).getT().longValue());
  }

  @Test
  void exactFiniteAndInfinityRoundTripsPreserveTheNativeSentinel() {
    for (long value : new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE - 1}) {
      QuantizedSimTime decoded = codec.decode(LongSimTime.create(value));
      assertEquals(Rational.of(value), decoded.value());
      assertEquals(value, codec.encode(decoded).getT().longValue());
      assertTrue(codec.isRepresentable(decoded.value()));
    }
    assertTrue(codec.decode(LongSimTime.create(Long.MAX_VALUE)).isInfinite());
    assertEquals(Long.MAX_VALUE, codec.encode(QuantizedSimTime.infinity()).getT().longValue());
    assertEquals(Long.MAX_VALUE,
        codec.nextRepresentable(QuantizedSimTime.infinity(), Rational.ZERO, Rational.ONE).getT().longValue());
  }

  @Test
  void rejectsFractionsOverflowAndFiniteSentinelCollisions() {
    for (Rational invalid : new Rational[]{Rational.of(1, 2), Rational.of(Long.MAX_VALUE),
        Rational.of(Long.MIN_VALUE).subtract(Rational.ONE),
        Rational.of(BigInteger.ONE.shiftLeft(100), BigInteger.ONE)}) {
      assertThrows(ArithmeticException.class, () -> codec.encode(QuantizedSimTime.of(invalid)));
      assertFalse(codec.isRepresentable(invalid));
    }
  }

  @Test
  void nextRepresentableFindsTheEncodingAndLatticeIntersection() {
    assertEquals(2L, codec.nextRepresentable(QuantizedSimTime.of(Rational.of(3, 2)),
        Rational.ZERO, Rational.of(2, 3)).getT().longValue());
    assertEquals(-1L, codec.nextRepresentable(QuantizedSimTime.of(Rational.of(-3, 2)),
        Rational.of(1, 3), Rational.of(2, 3)).getT().longValue());
    assertEquals(2L, codec.nextRepresentable(QuantizedSimTime.of(Rational.of(2)),
        Rational.ZERO, Rational.of(2, 3)).getT().longValue());
    assertEquals(-1L, codec.nextRepresentable(QuantizedSimTime.of(Rational.of(-3, 2)),
        Rational.ZERO, null).getT().longValue());
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(
        QuantizedSimTime.of(Rational.ZERO), Rational.of(1, 3), Rational.of(1, 2)));
  }

  @Test
  void successorRespectsRangeAndNeverReturnsTheSentinelForAFiniteBound() {
    assertEquals(Long.MIN_VALUE, codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Long.MIN_VALUE).subtract(Rational.ONE)), Rational.ZERO, null).getT().longValue());
    assertEquals(Long.MAX_VALUE - 1, codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Long.MAX_VALUE - 1)), Rational.ZERO, null).getT().longValue());
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Long.MAX_VALUE).subtract(Rational.of(1, 2))), Rational.ZERO, null));
    assertThrows(ArithmeticException.class, () -> codec.nextRepresentable(QuantizedSimTime.of(
        Rational.of(Long.MAX_VALUE)), Rational.ZERO, Rational.ONE));
  }
}