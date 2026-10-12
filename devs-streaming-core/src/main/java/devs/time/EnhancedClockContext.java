package devs.time;

import java.math.BigDecimal;
import java.util.Objects;

/** Shared identity and reference origin of an enhanced hierarchy. */
public record EnhancedClockContext(String clockId, BigDecimal compositionOrigin) {
  /**
   * Creates the hierarchy's shared clock identity and reference origin in seconds.
   * Equality includes both fields; the origin is metadata, not an automatic mapping offset.
   * @throws IllegalArgumentException if clockId is blank
   * @throws NullPointerException if either argument is null
   */
  public EnhancedClockContext {
    if (Objects.requireNonNull(clockId).isBlank()) {
      throw new IllegalArgumentException("Clock identity is required");
    }
    compositionOrigin = Objects.requireNonNull(compositionOrigin).stripTrailingZeros();
  }


}