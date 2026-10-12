package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.time.DecimalSimTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoordinatorExecutionConfigTest {
  private final EnhancedClockContext clock = new EnhancedClockContext("test", BigDecimal.ZERO);
  private final InteractionKey route = new InteractionKey("a", "out", "b", "in");

  @Test
  void clockOnlyConstructionLeavesRoutesUnmonitored() {
    CoordinatorExecutionConfig config = new CoordinatorExecutionConfig(clock);
    assertSame(clock, config.clock());
    assertTrue(config.timingErrorBudgets().isEmpty());
    assertDoesNotThrow(() -> config.observe(route,
        DecimalSimTime.of(BigDecimal.ZERO), DecimalSimTime.of(BigDecimal.ONE)));
  }

  @Test
  void snapshotsBudgetsAndObservesOnlyConfiguredInteractions() {
    List<TimingErrorBudget.Observation> observed = new ArrayList<>();
    List<TimingErrorBudget.Observation> violations = new ArrayList<>();
    TimingErrorBudget budget = new TimingErrorBudget(BigDecimal.ZERO,
        (source, effective) -> effective.value().subtract(source.value()),
        observed::add, violations::add);
    Map<InteractionKey, TimingErrorBudget> budgets = new HashMap<>();
    budgets.put(route, budget);
    CoordinatorExecutionConfig config = new CoordinatorExecutionConfig(clock, budgets);
    budgets.clear();
    assertSame(clock, config.clock());
    assertEquals(Map.of(route, budget), config.timingErrorBudgets());
    assertThrows(UnsupportedOperationException.class, () -> config.timingErrorBudgets().clear());
    DecimalSimTime source = DecimalSimTime.of(BigDecimal.ZERO);
    DecimalSimTime effective = DecimalSimTime.of(BigDecimal.ONE);
    config.observe(new InteractionKey("a", "other", "b", "in"), source, effective);
    assertTrue(observed.isEmpty());
    config.observe(route, source, effective);
    assertEquals(List.of(new TimingErrorBudget.Observation(route, source, effective, BigDecimal.ONE)),
        observed);
    assertEquals(observed, violations);
  }

  @Test
  void rejectsNullClockBudgetsKeysAndValues() {
    assertThrows(NullPointerException.class, () -> new CoordinatorExecutionConfig(null));
    assertThrows(NullPointerException.class,
        () -> new CoordinatorExecutionConfig(null, Map.of()));
    assertThrows(NullPointerException.class,
        () -> new CoordinatorExecutionConfig(clock, null));
    TimingErrorBudget budget = new TimingErrorBudget(BigDecimal.ZERO,
        (source, effective) -> BigDecimal.ZERO, observation -> {}, observation -> {});
    Map<InteractionKey, TimingErrorBudget> budgets = new HashMap<>();
    budgets.put(null, budget);
    assertThrows(NullPointerException.class,
        () -> new CoordinatorExecutionConfig(clock, budgets));
    budgets.clear();
    budgets.put(route, null);
    assertThrows(NullPointerException.class,
        () -> new CoordinatorExecutionConfig(clock, budgets));
  }
}