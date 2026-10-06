package devs.time;

import java.util.Objects;
import java.util.Optional;

/**
 * Supplier-native facts. Origin is the native epoch offset in seconds; optional granularity
 * restricts timestamps to a native-zero grid independently of codec representation.
 * Alignment and delivery adaptation belong to the composition, not this declaration.
 */
public record TimeSemantics(Rational secondsPerUnit, Rational origin,
                            Optional<TimeGranularity> timeGranularity) {
  public TimeSemantics {
    Objects.requireNonNull(secondsPerUnit);
    Objects.requireNonNull(origin);
    Objects.requireNonNull(timeGranularity);
    Rational.requirePositive(secondsPerUnit);
  }

  /** No additional supplier-imposed grid; codec constraints still apply. */
  public static TimeSemantics of(Rational secondsPerUnit, Rational origin) {
    return new TimeSemantics(secondsPerUnit, origin, Optional.empty());
  }

  public static TimeSemantics of(Rational secondsPerUnit, Rational origin,
                                 Optional<TimeGranularity> timeGranularity) {
    return new TimeSemantics(secondsPerUnit, origin, timeGranularity);
  }
}
