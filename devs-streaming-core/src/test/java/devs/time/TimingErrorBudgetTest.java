package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.QuantizedSimTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimingErrorBudgetTest {
  @Test
  void observesAndEnforcesOnlyDeveloperSuppliedMetricAndAction() {
    List<TimingErrorBudget.Observation> observations = new ArrayList<>();
    List<TimingErrorBudget.Observation> violations = new ArrayList<>();
    TimingErrorBudget budget = new TimingErrorBudget(Rational.of(1, 10000),
        (source, effective) -> effective.value().subtract(source.value()),
        observations::add, violations::add);
    InteractionKey route = new InteractionKey("a", "out", "b", "in");
    budget.observe(route, QuantizedSimTime.of(Rational.of(3, 2000)),
        QuantizedSimTime.of(Rational.of(2, 1000)));
    assertEquals(1, observations.size());
    assertEquals(observations, violations);
    assertEquals(Rational.of(1, 2000), observations.getFirst().error());
    assertThrows(IllegalArgumentException.class, () -> new TimingErrorBudget(
        Rational.of(-1), (s, e) -> Rational.ZERO, observations::add, violations::add));
  }
}