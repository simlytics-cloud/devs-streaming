package devs.time;

import java.math.BigDecimal;
import java.util.Objects;

/** Positive decimal native-unit grid spacing, anchored at local zero. */
public record TimeGranularity(BigDecimal spacing) {
  public TimeGranularity {
    spacing = Objects.requireNonNull(spacing).stripTrailingZeros();
    if (spacing.signum() <= 0) throw new IllegalArgumentException("spacing must be positive");
  }
  public static TimeGranularity of(BigDecimal spacing) { return new TimeGranularity(spacing); }
  /** Compatibility overload; nonterminating values are rejected. */
}
