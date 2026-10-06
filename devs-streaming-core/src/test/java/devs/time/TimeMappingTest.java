package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TimeMappingTest {
  @Test
  void absentGranularityPreservesAllThreeNativeRepresentations() {
    TimeSemantics semantics = TimeSemantics.of(Rational.ONE, Rational.ZERO);
    TimeMapping<LongSimTime> integer = new TimeMapping<>(new LongTimeCodec(), semantics);
    TimeMapping<DoubleSimTime> binary = new TimeMapping<>(new DoubleTimeCodec(), semantics);
    TimeMapping<DecimalSimTime> decimal = new TimeMapping<>(new DecimalTimeCodec(), semantics);
    assertEquals(QuantizedSimTime.of(Rational.of(-7)), integer.toCanonical(LongSimTime.create(-7)));
    assertEquals(QuantizedSimTime.of(Rational.of(0.001)), binary.toCanonical(DoubleSimTime.create(0.001)));
    for (String value : new String[]{"-0.0005", "0", "0.001", "1.234567890123456789"}) {
      DecimalSimTime nativeTime = DecimalSimTime.of(new BigDecimal(value));
      assertEquals(nativeTime, decimal.exact(decimal.toCanonical(nativeTime)));
      assertEquals(nativeTime, decimal.nextRepresentable(decimal.toCanonical(nativeTime)));
    }
    assertThrows(ArithmeticException.class, () -> integer.exact(QuantizedSimTime.of(Rational.of(1, 2))));
    assertThrows(ArithmeticException.class, () -> binary.exact(QuantizedSimTime.of(Rational.of(1, 1000))));
    assertThrows(ArithmeticException.class, () -> decimal.exact(QuantizedSimTime.of(Rational.of(1, 3))));
    assertEquals(DecimalSimTime.of(new BigDecimal("0.333333333333333334")),
        decimal.nextRepresentable(QuantizedSimTime.of(Rational.of(1, 3))));
  }

  @Test
  void decimalAdaptationScaleIsInNativeUnitsAndDoesNotRestrictExactMapping() {
    TimeMapping<DecimalSimTime> mapping = new TimeMapping<>(new DecimalTimeCodec(2),
        TimeSemantics.of(Rational.of(2), Rational.of(1, 7)), Rational.of(-1, 5));
    Rational offset = Rational.of(1, 7).subtract(Rational.of(1, 5));
    for (Rational bound : new Rational[]{Rational.of(1, 3), Rational.of(-1, 3)}) {
      QuantizedSimTime canonical = QuantizedSimTime.of(offset.add(bound.multiply(Rational.of(2))));
      DecimalSimTime expected = DecimalSimTime.of(new BigDecimal(bound.compareTo(Rational.ZERO) > 0
          ? "0.34" : "-0.33"));
      assertEquals(expected, mapping.nextRepresentable(canonical));
      assertTrue(mapping.toCanonical(expected).compareTo(canonical) > 0);
      assertThrows(ArithmeticException.class, () -> mapping.exact(canonical));
    }
    DecimalSimTime precise = DecimalSimTime.of(new BigDecimal("0.1234567890123456789012345"));
    QuantizedSimTime exact = mapping.toCanonical(precise);
    assertEquals(precise, mapping.exact(exact));
    assertEquals(precise, mapping.nextRepresentable(exact));
    assertEquals(DecimalSimTime.infinity(), mapping.nextRepresentable(QuantizedSimTime.infinity()));
  }

  @Test
  void decimalGranularityOverridesFallbackScaleWithoutMovingNativeZero() {
    Rational offset = Rational.of(1, 7).add(Rational.of(2, 5));
    TimeMapping<DecimalSimTime> mapping = new TimeMapping<>(new DecimalTimeCodec(2),
        TimeSemantics.of(Rational.of(1, 1000), Rational.of(1, 7),
            Optional.of(TimeGranularity.of(Rational.of(1, 1000)))), Rational.of(2, 5));
    QuantizedSimTime bound = QuantizedSimTime.of(offset.add(Rational.of(1, 3000)));
    DecimalSimTime result = mapping.nextRepresentable(bound);
    assertEquals(DecimalSimTime.of(new BigDecimal("0.334")), result);
    assertEquals(QuantizedSimTime.of(offset.add(Rational.of(334, 1000000))), mapping.toCanonical(result));
    assertTrue(mapping.toCanonical(result).compareTo(bound) > 0);
    assertEquals(result, mapping.nextRepresentable(mapping.toCanonical(result)));
    assertThrows(ArithmeticException.class, () -> mapping.exact(bound));
  }

  @Test
  void doubleWholeMillisecondsAndDecimalMillisecondBoundariesAreDistinctContracts() {
    TimeMapping<DoubleSimTime> milliseconds = new TimeMapping<>(new DoubleTimeCodec(),
        TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO,
            Optional.of(TimeGranularity.of(Rational.ONE))));
    TimeMapping<DecimalSimTime> seconds = new TimeMapping<>(new DecimalTimeCodec(),
        TimeSemantics.of(Rational.ONE, Rational.ZERO,
            Optional.of(TimeGranularity.of(Rational.of(1, 1000)))));
    for (long value : new long[]{-2, 0, 1}) {
      QuantizedSimTime canonical = QuantizedSimTime.of(Rational.of(value, 1000));
      assertEquals(canonical, milliseconds.toCanonical(DoubleSimTime.create((double) value)));
      assertEquals(canonical, seconds.toCanonical(seconds.exact(canonical)));
    }
    assertThrows(ArithmeticException.class, () -> milliseconds.toCanonical(DoubleSimTime.create(0.5)));
    assertThrows(ArithmeticException.class, () -> seconds.toCanonical(DecimalSimTime.of(new BigDecimal("0.0005"))));
    assertEquals(-1.0, milliseconds.nextRepresentable(QuantizedSimTime.of(Rational.of(-3, 2000))).getT());
    assertEquals(DecimalSimTime.of(new BigDecimal("-0.001")),
        seconds.nextRepresentable(QuantizedSimTime.of(Rational.of(-3, 2000))));
    TimeMapping<DoubleSimTime> binarySeconds = new TimeMapping<>(new DoubleTimeCodec(), seconds.semantics());
    assertThrows(ArithmeticException.class, () -> binarySeconds.toCanonical(DoubleSimTime.create(0.001)));
    assertEquals(QuantizedSimTime.of(Rational.of(1, 8)),
        binarySeconds.toCanonical(binarySeconds.nextRepresentable(QuantizedSimTime.of(Rational.of(1, 1000)))));
  }

  @Test
  void alignmentAndEpochNeverMoveTheNativeZeroGrid() {
    TimeMapping<LongSimTime> mapping = new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.of(1, 10), Rational.of(1, 3),
            Optional.of(TimeGranularity.of(Rational.of(2)))), Rational.of(7, 5));
    Rational offset = Rational.of(1, 3).add(Rational.of(7, 5));
    for (long nativeValue : new long[]{-4, 0, 2}) {
      QuantizedSimTime canonical = QuantizedSimTime.of(offset.add(Rational.of(nativeValue, 10)));
      assertEquals(canonical, mapping.toCanonical(LongSimTime.create(nativeValue)));
      assertEquals(nativeValue, mapping.exact(canonical).getT().longValue());
    }
    assertThrows(ArithmeticException.class, () -> mapping.toCanonical(LongSimTime.create(-1)));
    assertEquals(0L, mapping.nextRepresentable(QuantizedSimTime.of(offset.subtract(Rational.of(1, 10)))).getT().longValue());
  }

  @Test
  void epochOffsetIsAdditiveSecondsAndIndependentOfUnitsAndAlignment() {
    TimeMapping<LongSimTime> mapping = new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.of(1, 1000), Rational.of(-7, 3)), Rational.of(11, 5));
    QuantizedSimTime expected = QuantizedSimTime.of(
        Rational.of(11, 5).add(Rational.of(-7, 3)).add(Rational.of(17, 1000)));
    assertEquals(expected, mapping.toCanonical(LongSimTime.create(17)));
    assertEquals(17L, mapping.exact(expected).getT().longValue());
    assertEquals(Rational.of(11, 5), mapping.alignment());
    assertEquals(Rational.of(-7, 3), mapping.semantics().origin());
    assertInstanceOf(LongTimeCodec.class, mapping.codec());
  }

  @Test
  void epochOffsetDoesNotShiftTheLocalZeroLattice() {
    TimeMapping<LongSimTime> mapping = new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.of(1, 10), Rational.of(1, 3), Optional.of(TimeGranularity.of(Rational.of(2)))));
    assertEquals(QuantizedSimTime.of(Rational.of(1, 3)), mapping.toCanonical(LongSimTime.create(0)));
    assertEquals(QuantizedSimTime.of(Rational.of(11, 15)), mapping.toCanonical(LongSimTime.create(4)));
    assertThrows(ArithmeticException.class, () -> mapping.toCanonical(LongSimTime.create(1)));
    assertThrows(ArithmeticException.class,
        () -> mapping.exact(QuantizedSimTime.of(Rational.of(13, 30))));
    assertEquals(2L, mapping.nextRepresentable(QuantizedSimTime.of(Rational.of(13, 30))).getT().longValue());
  }

  @Test
  void largeOffsetsAreExactWithoutLongIntermediateOverflow() {
    Rational epoch = Rational.of(BigInteger.ONE.shiftLeft(200), BigInteger.ONE);
    TimeMapping<LongSimTime> mapping = new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.of(3, 2), epoch), epoch.negate().add(Rational.of(-7)));
    QuantizedSimTime expected = QuantizedSimTime.of(Rational.of(Long.MIN_VALUE)
        .multiply(Rational.of(3, 2)).subtract(Rational.of(7)));
    assertEquals(expected, mapping.toCanonical(LongSimTime.create(Long.MIN_VALUE)));
    assertEquals(Long.MIN_VALUE, mapping.exact(expected).getT().longValue());
  }

  @Test
  void exactRequiresBothLatticeMembershipAndEncodingRepresentability() {
    TimeMapping<LongSimTime> mapping = new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.ONE, Rational.ZERO, Optional.of(TimeGranularity.of(Rational.of(2, 3)))));
    assertThrows(ArithmeticException.class, () -> mapping.exact(QuantizedSimTime.of(Rational.of(2, 3))));
    assertThrows(ArithmeticException.class, () -> mapping.exact(QuantizedSimTime.of(Rational.ONE)));
    assertEquals(2L, mapping.exact(QuantizedSimTime.of(Rational.of(2))).getT().longValue());
    assertEquals(2L, mapping.nextRepresentable(QuantizedSimTime.of(Rational.of(3, 2))).getT().longValue());
  }

  @Test
  void mappedBinaryAndDecimalIntersectionsNeverDeliverInThePast() {
    TimeSemantics semantics = TimeSemantics.of(Rational.of(1, 1000), Rational.of(-2),
        Optional.of(TimeGranularity.of(Rational.of(1, 3))));
    TimeMapping<DoubleSimTime> binary = new TimeMapping<>(new DoubleTimeCodec(), semantics, Rational.of(2));
    TimeMapping<DecimalSimTime> decimal = new TimeMapping<>(new DecimalTimeCodec(), semantics, Rational.of(2));
    QuantizedSimTime source = QuantizedSimTime.of(Rational.of(1, 3000));
    assertThrows(ArithmeticException.class, () -> binary.exact(source));
    assertThrows(ArithmeticException.class, () -> decimal.exact(source));
    assertEquals(1.0, binary.nextRepresentable(source).getT());
    assertEquals(DecimalSimTime.of(BigDecimal.ONE), decimal.nextRepresentable(source));
    assertEquals(QuantizedSimTime.of(Rational.of(1, 1000)), binary.toCanonical(binary.nextRepresentable(source)));
    assertEquals(QuantizedSimTime.of(Rational.of(1, 1000)), decimal.toCanonical(decimal.nextRepresentable(source)));
  }

  @Test
  void sentinelIsDecodedBeforeApplyingOffsetsUnitsOrLattice() {
    TimeMapping<LongSimTime> mapping = new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.of(1, 3), Rational.of(-1, 7),
            Optional.of(TimeGranularity.of(Rational.of(2)))), Rational.of(-100));
    assertEquals(QuantizedSimTime.infinity(), mapping.toCanonical(LongSimTime.create(Long.MAX_VALUE)));
    assertEquals(Long.MAX_VALUE, mapping.exact(QuantizedSimTime.infinity()).getT().longValue());
    assertEquals(Long.MAX_VALUE, mapping.nextRepresentable(QuantizedSimTime.infinity()).getT().longValue());
    TimeMapping<LongSimTime> identity = new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.ONE, Rational.ZERO));
    assertThrows(ArithmeticException.class, () -> identity.nextRepresentable(
        QuantizedSimTime.of(Rational.of(Long.MAX_VALUE))));
  }

  @Test
  void customCodecCannotSilentlyRoundOrReturnInfinityForFiniteDelivery() {
    NativeTimeCodec<LongSimTime> rounding = new NativeTimeCodec<>() {
      private final LongTimeCodec delegate = new LongTimeCodec();

      @Override
      public QuantizedSimTime decode(LongSimTime value) {
        return delegate.decode(value);
      }

      @Override
      public LongSimTime encode(QuantizedSimTime value) {
        return value.isInfinite() ? delegate.encode(value)
            : LongSimTime.create(value.value().floor().longValueExact());
      }

      @Override
      public LongSimTime nextRepresentable(QuantizedSimTime lower, Rational origin, Rational quantum) {
        return encode(lower);
      }
    };
    TimeMapping<LongSimTime> mapping = new TimeMapping<>(rounding,
        TimeSemantics.of(Rational.ONE, Rational.ZERO));
    assertFalse(rounding.isRepresentable(Rational.of(3, 2)));
    assertThrows(ArithmeticException.class, () -> mapping.exact(QuantizedSimTime.of(Rational.of(3, 2))));
    assertThrows(ArithmeticException.class, () -> mapping.nextRepresentable(QuantizedSimTime.of(Rational.of(3, 2))));
    assertThrows(ArithmeticException.class, () -> mapping.nextRepresentable(
        QuantizedSimTime.of(Rational.of(Long.MAX_VALUE))));
  }
}