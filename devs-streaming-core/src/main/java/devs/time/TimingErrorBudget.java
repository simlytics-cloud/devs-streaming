package devs.time;

import devs.iso.time.QuantizedSimTime;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** An explicitly chosen metric and action; not a wall-clock execution deadline. */
public record TimingErrorBudget(Rational maximum,
    BiFunction<QuantizedSimTime, QuantizedSimTime, Rational> metric,
    Consumer<Observation> observer, Consumer<Observation> violationAction) {
  /**
   * Result of evaluating a delivery's explicitly configured error metric.
   * No validation is performed by this record itself.
   * @param interaction observed directed interaction
   * @param sourceTime original timestamp in canonical seconds
   * @param effectiveTime accepted delivery timestamp in canonical seconds
   * @param error metric result, in the metric's units rather than necessarily seconds
   */
  public record Observation(InteractionKey interaction, QuantizedSimTime sourceTime,
                            QuantizedSimTime effectiveTime, Rational error) {}

  /**
   * Creates an error budget with explicit callbacks and metric-defined units.
   * @param maximum nonnegative permitted error in the metric's units
   * @param metric computes error from source and effective canonical-second timestamps
   * @param observer callback invoked for every valid metric result
   * @param violationAction callback invoked after the observer when error exceeds maximum
   * @throws IllegalArgumentException if maximum is negative
   * @throws NullPointerException if any argument is null
   */
  public TimingErrorBudget {
    Objects.requireNonNull(maximum);
    if (maximum.compareTo(Rational.ZERO) < 0) {
      throw new IllegalArgumentException("Negative error budget");
    }
    Objects.requireNonNull(metric);
    Objects.requireNonNull(observer);
    Objects.requireNonNull(violationAction);
  }

  /**
   * Evaluates the metric, reports its nonnegative result, then invokes the violation
   * action only for a strict exceedance of maximum. Equality is within budget.
   * Callback exceptions propagate; an observer failure prevents the violation action.
   * @throws NullPointerException if the metric returns null
   * @throws IllegalArgumentException if the metric returns a negative error
   */
  public void observe(InteractionKey interaction, QuantizedSimTime source,
      QuantizedSimTime effective) {
    Rational error = Objects.requireNonNull(metric.apply(source, effective));
    if (error.compareTo(Rational.ZERO) < 0) {
      throw new IllegalArgumentException("Error metric must be nonnegative");
    }
    Observation observation = new Observation(interaction, source, effective, error);
    observer.accept(observation);
    if (error.compareTo(maximum) > 0) {
      violationAction.accept(observation);
    }
  }
}