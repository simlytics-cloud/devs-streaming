package devs.time;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.DevsMessage;
import devs.iso.PortValue;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class CanonicalScheduleTest {
  private static final ActorTestKit kit = ActorTestKit.create();
  private final EnhancedClockContext clock = new EnhancedClockContext("test", Rational.ZERO);
  private final InteractionKey route = new InteractionKey("source", "out", "target", "in");

  @AfterAll
  static void shutdown() { kit.shutdownTestKit(); }

  private QuantizedSimTime time(long numerator, long denominator) {
    return QuantizedSimTime.of(Rational.of(numerator, denominator));
  }

  private CanonicalSchedule schedule() {
    return schedule(Map.of());
  }

  private CanonicalSchedule schedule(Map<InteractionKey, TimingErrorBudget> monitors) {
    return schedule(monitors, List.of("source", "target"));
  }

  private CanonicalSchedule schedule(Map<InteractionKey, TimingErrorBudget> monitors,
      List<String> resolvedChildOrder) {
    ChildBinding<LongSimTime> target = ChildBinding.nativeActor("target",
        kit.<DevsMessage>createTestProbe().ref(), LongSimTime.class,
        new TimeMapping<>(new LongTimeCodec(),
            TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO)));
    ChildBinding<DoubleSimTime> source = ChildBinding.nativeActor("source",
        kit.<DevsMessage>createTestProbe().ref(), DoubleSimTime.class,
        new TimeMapping<>(new DoubleTimeCodec(),
            TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO)));
    return new CanonicalSchedule(new CoordinatorExecutionConfig(clock, monitors),
        Map.of("source", source, "target", target), resolvedChildOrder);
  }

  private PortValue<String> value(String value) {
    return PortValue.<String>builder().value(value).portName("in").build();
  }

  @Test
  void suppliedOrderIsSnapshottedAndFiltersTiedAndPartialImminence() {
    List<String> order = new ArrayList<>(List.of("target", "source"));
    CanonicalSchedule schedule = schedule(Map.of(), order);
    order.clear();
    order.add("source");
    schedule.update("source", DoubleSimTime.create(2), time(0, 1));
    assertEquals(List.of("source"), schedule.imminent(time(1, 500)));
    schedule.update("target", LongSimTime.create(2), time(0, 1));
    assertEquals(List.of("target", "source"), schedule.imminent(time(1, 500)));
    assertThrows(UnsupportedOperationException.class,
        () -> schedule.imminent(time(1, 500)).add("other"));
    schedule.update("source", DoubleSimTime.create(3), time(1, 500));
    assertEquals(List.of("target"), schedule.imminent(time(1, 500)));
    assertEquals(List.of("source"), schedule.imminent(time(3, 1000)));
  }

  @Test
  void snapshotsBindingsAndRetainsIdentifierAndClockValidation() {
    CoordinatorExecutionConfig config = new CoordinatorExecutionConfig(clock);
    ChildBinding<QuantizedSimTime> binding = ChildBinding.coordinatorActor("nested",
        kit.<DevsMessage>createTestProbe().ref(), clock);
    Map<String, ChildBinding<?>> bindings = new HashMap<>();
    bindings.put("nested", binding);
    CanonicalSchedule schedule = new CanonicalSchedule(config, bindings, List.of("nested"));
    bindings.clear();
    schedule.update("nested", time(1, 1), time(0, 1));
    assertTrue(schedule.initialized());
    assertEquals(time(1, 1), schedule.next());
    assertThrows(IllegalArgumentException.class,
        () -> new CanonicalSchedule(config, Map.of("wrong", binding), List.of("wrong")));
    for (EnhancedClockContext other : List.of(
        new EnhancedClockContext("other", Rational.ZERO),
        new EnhancedClockContext("test", Rational.ONE))) {
      ChildBinding<QuantizedSimTime> mismatched = ChildBinding.coordinatorActor("nested",
          kit.<DevsMessage>createTestProbe().ref(), other);
      assertThrows(IllegalArgumentException.class,
          () -> new CanonicalSchedule(config, Map.of("nested", mismatched), List.of("nested")));
    }
  }

  @Test
  void rejectsNullConstructionInputsAndEntries() {
    CoordinatorExecutionConfig config = new CoordinatorExecutionConfig(clock);
    assertThrows(NullPointerException.class,
        () -> new CanonicalSchedule(null, Map.of(), List.of()));
    assertThrows(NullPointerException.class,
        () -> new CanonicalSchedule(config, null, List.of()));
    assertThrows(NullPointerException.class,
        () -> new CanonicalSchedule(config, Map.of(), null));
    List<String> order = new ArrayList<>();
    order.add(null);
    assertThrows(NullPointerException.class,
        () -> new CanonicalSchedule(config, Map.of(), order));
    Map<String, ChildBinding<?>> bindings = new HashMap<>();
    bindings.put("nested", null);
    assertThrows(NullPointerException.class,
        () -> new CanonicalSchedule(config, bindings, List.of("nested")));
    bindings.clear();
    bindings.put(null, ChildBinding.coordinatorActor("nested",
        kit.<DevsMessage>createTestProbe().ref(), clock));
    assertThrows(NullPointerException.class,
        () -> new CanonicalSchedule(config, bindings, List.of("nested")));
  }

  @Test
  void processesInterveningEventThenDeliveryEvenWithInfiniteCandidates() {
    CanonicalSchedule schedule = schedule();
    schedule.update("source", DoubleSimTime.create(1.5), time(0, 1));
    schedule.update("target", LongSimTime.create(Long.MAX_VALUE), time(0, 1));
    assertEquals(time(3, 2000), schedule.next());
    PendingDelivery delivery = schedule.enqueue(route, value("first"), schedule.next());
    assertEquals(time(3, 2000), delivery.sourceTime());
    assertEquals(time(1, 500), delivery.effectiveTime());
    assertEquals(LongSimTime.create(2), delivery.nativeTime());
    schedule.update("source", DoubleSimTime.create(1.75), time(3, 2000));
    assertEquals(time(7, 4000), schedule.next());
    assertTrue(schedule.drain(schedule.next()).isEmpty());
    schedule.update("source", DoubleSimTime.create(Double.MAX_VALUE), time(7, 4000));
    assertEquals(time(1, 500), schedule.next());
    assertTrue(schedule.imminent(schedule.next()).isEmpty());
    assertEquals(List.of(delivery), schedule.drain(schedule.next()));
    assertTrue(schedule.next().isInfinite());
  }

  @Test
  void retainsDuplicateRoutesAndStableTieOrderAndEqualTimeEvents() {
    CanonicalSchedule schedule = schedule();
    schedule.update("target", LongSimTime.create(2), time(0, 1));
    schedule.update("source", DoubleSimTime.create(2), time(0, 1));
    assertEquals(List.of("source", "target"), schedule.imminent(time(1, 500)));
    PendingDelivery first = schedule.enqueue(route, value("a"), time(3, 2000));
    PendingDelivery second = schedule.enqueue(route, value("b"), time(3, 2000));
    assertEquals(List.of(first, second), schedule.drain(time(1, 500)));
    schedule.update("source", DoubleSimTime.create(2), time(1, 500));
    assertEquals(time(1, 500), schedule.next());
  }

  @Test
  void rejectsMissingRulesRollbackOffLatticeAndMissedDeliveries() {
    // Routes need no rules; malformed targets and times remain guarded.
    CanonicalSchedule schedule = schedule();
    assertThrows(IllegalStateException.class, schedule::next);
    assertThrows(IllegalArgumentException.class,
        () -> schedule.update("unknown", LongSimTime.create(0), time(0, 1)));
    assertThrows(IllegalArgumentException.class,
        () -> schedule.update("target", LongSimTime.create(0), time(1, 1000)));
    assertThrows(IllegalArgumentException.class,
        () -> schedule.enqueue(route, value("x"), QuantizedSimTime.infinity()));
    assertThrows(IllegalArgumentException.class, () -> schedule.enqueue(
        new InteractionKey("source", "other", "unknown", "in"), value("x"), time(0, 1)));
    schedule.enqueue(route, value("x"), time(1, 1000));
    assertThrows(IllegalStateException.class, () -> schedule.drain(time(1, 500)));
    schedule.clear();
    assertFalse(schedule.initialized());
    assertTrue(schedule.drain(time(1, 500)).isEmpty());
  }

  @Test
  void budgetObservationAndViolationActionsAreExplicit() {
    List<TimingErrorBudget.Observation> observed = new ArrayList<>();
    List<TimingErrorBudget.Observation> violations = new ArrayList<>();
    TimingErrorBudget budget = new TimingErrorBudget(Rational.of(1, 10000),
        (source, effective) -> effective.value().subtract(source.value()), observed::add, violations::add);
    CanonicalSchedule schedule = schedule(Map.of(route, budget));
    schedule.enqueue(route, value("x"), time(3, 2000));
    assertEquals(1, observed.size());
    assertEquals(observed, violations);
    assertEquals(Rational.of(1, 2000), observed.getFirst().error());
  }

  @Test
  void unconfiguredRoutesAdaptAndQueuesSortByEffectiveTimeThenSequence() {
    CanonicalSchedule schedule = schedule();
    schedule.update("source", DoubleSimTime.create(Double.MAX_VALUE), time(0, 1));
    schedule.update("target", LongSimTime.create(Long.MAX_VALUE), time(0, 1));
    InteractionKey dynamic = new InteractionKey("source", "dynamic", "target", "in");
    PendingDelivery later = schedule.enqueue(dynamic, value("later"), time(7, 3000));
    PendingDelivery first = schedule.enqueue(route, value("first"), time(1, 3000));
    PendingDelivery second = schedule.enqueue(dynamic, value("second"), time(2, 3000));
    assertEquals(time(1, 3000), first.sourceTime());
    assertEquals(time(1, 1000), first.effectiveTime());
    assertEquals(time(1, 1000), schedule.next());
    assertEquals(List.of(first, second), schedule.drain(schedule.next()));
    assertEquals(time(3, 1000), schedule.next());
    assertEquals(List.of(later), schedule.drain(schedule.next()));
    assertTrue(schedule.next().isInfinite());
  }
}
