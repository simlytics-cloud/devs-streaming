package devs;

import devs.iso.DevsMessage;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.time.EnhancedClockContext;
import devs.time.LongTimeCodec;
import devs.time.Rational;
import devs.time.TimeMapping;
import devs.time.TimeSemantics;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoordinatorChildOrderTest {
  @Test
  void naturalOrderIsCaseSensitiveAndCustomOrderIsImmutable() {
    Set<String> ids = Set.of("a", "Z", "A");
    assertEquals(List.of("A", "Z", "a"), PDevsCoordinator.defaultChildOrder(ids));
    List<String> supplied = new ArrayList<>(List.of("a", "A", "Z"));
    List<String> resolved = PDevsCoordinator.customChildOrder(ids, supplied);
    supplied.clear();
    assertEquals(List.of("a", "A", "Z"), resolved);
    assertThrows(UnsupportedOperationException.class, () -> resolved.clear());
  }

  @Test
  void rejectsInvalidCustomListsWithoutNormalizingIdentifiers() {
    Set<String> ids = Set.of("A", "a");
    assertThrows(NullPointerException.class, () -> PDevsCoordinator.customChildOrder(ids, null));
    for (List<String> invalid : List.of(List.<String>of(), List.of("A"), List.of("A", "A"),
        List.of("A", "unknown"), List.of("A", " "), List.of("A", " a"),
        Arrays.asList("A", null))) {
      assertThrows(IllegalArgumentException.class,
          () -> PDevsCoordinator.customChildOrder(ids, invalid));
    }
  }

  @Test
  void staticConstructionSnapshotsBeforeDeferredSetup() {
    TestInbox<DevsMessage> child = TestInbox.create();
    var actors = new HashMap<String, ActorRef<DevsMessage>>();
    actors.put("child", child.getRef());
    List<String> order = new ArrayList<>(List.of("child"));
    var behavior = PDevsCoordinator.create("coupled", actors,
        PDevsCouplings.builder("coupled").build(), order);
    actors.clear();
    order.clear();
    BehaviorTestKit<DevsMessage> kit = BehaviorTestKit.create(behavior);
    kit.run(new SimulationInitMessage<>(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0)).simulationRunId("run")
        .messageId("init").senderId("parent").receiverId("coupled").build(),
        TestInbox.<DevsMessage>create().getRef()));
    assertEquals("child", assertInstanceOf(SimulationInitMessage.class,
        child.receiveMessage()).getSimulationInit().getReceiverId());
    assertThrows(NullPointerException.class, () -> PDevsCoordinator.create("coupled",
        java.util.Map.of("child", child.getRef()), PDevsCouplings.builder("coupled").build(),
        (List<String>) null));
  }

  @Test
  void enhancedStaticConstructionSnapshotsActorsBindingsAndOrder() {
    TestInbox<DevsMessage> shared = TestInbox.create();
    var actors = new HashMap<String, ActorRef<DevsMessage>>();
    var bindings = new HashMap<String, ChildBinding<?>>();
    var clock = new EnhancedClockContext("clock", Rational.ZERO);
    for (String id : List.of("A", "a")) {
      actors.put(id, shared.getRef());
      bindings.put(id, ChildBinding.nativeActor(id, shared.getRef(),
          LongSimTime.class, new TimeMapping<>(new LongTimeCodec(),
              TimeSemantics.of(Rational.ONE, Rational.ZERO))));
    }
    List<String> order = new ArrayList<>(List.of("a", "A"));
    var behavior = PDevsCoordinator.enhanced("coupled", actors,
        PDevsCouplings.builder("coupled").build(), new CoordinatorExecutionConfig(clock),
        bindings, order);
    actors.clear();
    bindings.clear();
    order.clear();
    BehaviorTestKit<DevsMessage> kit = BehaviorTestKit.create(behavior);
    kit.run(new SimulationInitMessage<>(SimulationInit.<QuantizedSimTime>builder()
        .eventTime(QuantizedSimTime.of(Rational.ZERO)).simulationRunId("run")
        .messageId("init").senderId("parent").receiverId("coupled").build(),
        TestInbox.<DevsMessage>create().getRef(), clock));
    for (String id : List.of("a", "A")) {
      assertEquals(id, assertInstanceOf(SimulationInitMessage.class,
          shared.receiveMessage()).getSimulationInit().getReceiverId());
    }
    assertFalse(shared.hasMessages());
  }
}
