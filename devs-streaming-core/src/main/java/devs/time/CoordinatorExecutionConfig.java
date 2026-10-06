package devs.time;

import devs.iso.time.QuantizedSimTime;
import java.util.Map;
import java.util.Objects;

/**
 * Local clock and optional timing-error monitoring, independent of child ordering.
 * Developers must validate native input adaptation suitability before coupling;
 * the runtime does not assess modeling validity.
 */
public record CoordinatorExecutionConfig(EnhancedClockContext clock,
    Map<InteractionKey, TimingErrorBudget> timingErrorBudgets) {
  /** Creates a configuration without optional timing-error monitoring. */
  public CoordinatorExecutionConfig(EnhancedClockContext clock) {
    this(clock, Map.of());
  }

  /**
   * Creates a local execution configuration with an immutable snapshot of optional monitors.
   * @param clock shared enhanced hierarchy clock
   * @param timingErrorBudgets optional monitors keyed by directed interactions; missing entries
   *     do not restrict delivery or select adaptation
   * @throws NullPointerException if an argument, map key, or map value is null
   */
  public CoordinatorExecutionConfig {
    Objects.requireNonNull(clock);
    timingErrorBudgets = Map.copyOf(timingErrorBudgets);
  }

  /** Observes a route only when an optional monitor was explicitly supplied. */
  public void observe(InteractionKey interaction, QuantizedSimTime source,
      QuantizedSimTime effective) {
    TimingErrorBudget budget = timingErrorBudgets.get(interaction);
    if (budget != null) {
      budget.observe(interaction, source, effective);
    }
  }

}