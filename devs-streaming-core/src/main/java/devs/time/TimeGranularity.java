package devs.time;

import java.util.Objects;

/**
 * Additional supplier restriction: finite timestamps are integer multiples of positive exact
 * spacing in native units, anchored at native local zero. Not a minimum advance or tolerance.
 */
public record TimeGranularity(Rational spacing) {
  public TimeGranularity {
    Objects.requireNonNull(spacing);
    Rational.requirePositive(spacing);
  }

  public static TimeGranularity of(Rational spacing) {
    return new TimeGranularity(spacing);
  }
}
