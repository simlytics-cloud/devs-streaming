package devs.time;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Decimal supplier-native time facts used by enhanced coordination. */
public record TimeSemantics(BigDecimal secondsPerUnit, BigDecimal origin,
                            Optional<TimeGranularity> timeGranularity) {
  public TimeSemantics {
    secondsPerUnit = normalize(Objects.requireNonNull(secondsPerUnit));
    origin = normalize(Objects.requireNonNull(origin));
    Objects.requireNonNull(timeGranularity);
    if (secondsPerUnit.signum() <= 0) throw new IllegalArgumentException("secondsPerUnit must be positive");
  }
  private static BigDecimal normalize(BigDecimal value) { return value.stripTrailingZeros(); }
  public static TimeSemantics of(BigDecimal secondsPerUnit, BigDecimal origin) {
    return new TimeSemantics(secondsPerUnit, origin, Optional.empty());
  }
  public static TimeSemantics of(BigDecimal secondsPerUnit, BigDecimal origin,
      Optional<TimeGranularity> timeGranularity) { return new TimeSemantics(secondsPerUnit, origin, timeGranularity); }

}
