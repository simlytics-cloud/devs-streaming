package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TimeSemanticsTest {
  @Test
  void rejectsNonpositiveUnits() {
    for (Rational invalid : new Rational[]{Rational.ZERO, Rational.of(-1)}) {
      assertThrows(IllegalArgumentException.class,
          () -> TimeSemantics.of(invalid, Rational.ZERO));
    }
  }

  @Test
  void acceptsNegativeEpochAndExplicitAbsence() {
    TimeSemantics semantics = TimeSemantics.of(Rational.of(1, 1000), Rational.of(-3, 2));
    assertEquals(Rational.of(1, 1000), semantics.secondsPerUnit());
    assertEquals(Rational.of(-3, 2), semantics.origin());
    assertTrue(semantics.timeGranularity().isEmpty());
    assertEquals(semantics, new TimeSemantics(Rational.of(1, 1000), Rational.of(-3, 2),
        Optional.empty()));
  }

  @Test
  void retainsPresentGranularityAndValueEquality() {
    TimeSemantics semantics = TimeSemantics.of(Rational.ONE, Rational.ZERO,
        Optional.of(TimeGranularity.of(Rational.of(1, 3))));
    assertEquals(Rational.of(1, 3), semantics.timeGranularity().orElseThrow().spacing());
    TimeSemantics equal = new TimeSemantics(Rational.ONE, Rational.ZERO,
        Optional.of(TimeGranularity.of(Rational.of(2, 6))));
    assertEquals(semantics, equal);
    assertEquals(semantics.hashCode(), equal.hashCode());
  }

  @Test
  void requiredFactsAndMappingDependenciesCannotBeNull() {
    assertThrows(NullPointerException.class, () -> TimeSemantics.of(null, Rational.ZERO));
    assertThrows(NullPointerException.class, () -> TimeSemantics.of(Rational.ONE, null));
    assertThrows(NullPointerException.class,
        () -> TimeSemantics.of(Rational.ONE, Rational.ZERO, null));
    assertThrows(NullPointerException.class,
        () -> new TimeSemantics(Rational.ONE, Rational.ZERO, null));
    TimeSemantics semantics = TimeSemantics.of(Rational.ONE, Rational.ZERO);
    assertThrows(NullPointerException.class, () -> new TimeMapping<>(null, semantics));
    assertThrows(NullPointerException.class, () -> new TimeMapping<>(new LongTimeCodec(), null));
    assertThrows(NullPointerException.class, () -> new TimeMapping<>(new LongTimeCodec(), semantics, null));
  }
}
