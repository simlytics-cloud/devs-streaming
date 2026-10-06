package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TimeGranularityTest {
  @Test
  void positiveNormalizedSpacingHasValueSemantics() {
    TimeGranularity value = TimeGranularity.of(Rational.of(1, 1000));
    TimeGranularity equal = new TimeGranularity(Rational.of(2, 2000));
    assertEquals(Rational.of(1, 1000), value.spacing());
    assertEquals(value, equal);
    assertEquals(value.hashCode(), equal.hashCode());
  }

  @Test
  void rejectsNullAndNonpositiveSpacing() {
    assertThrows(NullPointerException.class, () -> TimeGranularity.of(null));
    for (Rational invalid : new Rational[]{Rational.ZERO, Rational.of(-1)}) {
      assertThrows(IllegalArgumentException.class, () -> TimeGranularity.of(invalid));
      assertThrows(IllegalArgumentException.class, () -> new TimeGranularity(invalid));
    }
  }
}
