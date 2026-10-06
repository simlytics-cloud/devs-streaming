package devs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.OutputReportPayload;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.TransitionComplete;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.time.EnhancedClockContext;
import devs.time.Rational;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.junit.jupiter.api.Test;

class RootCoordinatorTimeSemanticsTest {
  private static final EnhancedClockContext CLOCK =
      new EnhancedClockContext("clock", Rational.of(7));

  @Test
  void sharesClockAndWaitsForDelayedOutputAndTransitionReplies() {
    Fixture f = new Fixture(time(10));
    f.init(time(1));
    SimulationInitMessage<?> init = assertInstanceOf(SimulationInitMessage.class,
        f.child.receiveMessage());
    assertSame(CLOCK, init.getEnhancedClock().orElseThrow());
    assertEquals(time(1), init.getSimulationInit().getEventTime());
    assertEquals("root", init.getSimulationInit().getSenderId());
    assertEquals("child", init.getSimulationInit().getReceiverId());
    assertEquals("run", init.getSimulationInit().getSimulationRunId());
    assertEquals(f.kit.getRef(), init.getParent());
    assertFalse(f.child.hasMessages());

    f.kit.run(f.next(time(1), time(2)));
    assertEquals(time(2), f.request().getEventTime());
    assertFalse(f.child.hasMessages());
    f.kit.run(f.output(time(2), time(9)));
    ExecuteTransition<?> execute = f.execute();
    assertEquals(time(2), execute.getEventTime());
    assertTrue(execute.getPayload().getInputs().isEmpty());
    assertEquals("run", execute.getSimulationRunId());
    assertEquals("root", execute.getSenderId());
    assertEquals("child", execute.getReceiverId());
    assertFalse(f.child.hasMessages());

    f.kit.run(f.complete(time(2), time(3)));
    assertEquals(time(3), f.request().getEventTime());
    assertFalse(f.child.hasMessages());
  }

  @Test
  void executesExactHorizonThenStopsWithoutRequestingBeyondIt() {
    Fixture f = initialized(time(10));
    f.kit.run(f.next(time(0), time(10)));
    assertEquals(time(10), f.request().getEventTime());
    f.kit.run(f.output(time(10), time(10)));
    assertEquals(time(10), f.execute().getEventTime());
    assertFalse(f.child.hasMessages());
    f.kit.run(f.complete(time(10), time(11)));
    assertEquals(time(10), f.terminate().getEventTime());
    assertFalse(f.child.hasMessages());
    f.kit.run(f.terminated("child", "run"));
    assertFalse(f.kit.isAlive());
  }

  @Test
  void checksExactFractionalHorizonOnFirstCandidate() {
    QuantizedSimTime horizon = QuantizedSimTime.of(Rational.of(1, 3));
    Fixture f = initialized(horizon);
    f.kit.run(f.next(time(0), QuantizedSimTime.of(Rational.of(334, 1000))));
    assertEquals(horizon, f.terminate().getEventTime());
    assertFalse(f.child.hasMessages());
  }

  @Test
  void initialInfinityTerminatesAtFiniteStartWithoutOutputOrTransition() {
    Fixture f = new Fixture(time(10));
    f.init(time(4));
    f.child.receiveMessage();
    f.kit.run(f.next(time(4), QuantizedSimTime.infinity()));
    assertEquals(time(4), f.terminate().getEventTime());
    assertFalse(f.child.hasMessages());
  }

  @Test
  void infinityAfterTransitionTerminatesAtLastCompletedFiniteTime() {
    Fixture f = executing(time(2));
    f.kit.run(f.complete(time(2), QuantizedSimTime.infinity()));
    assertEquals(time(2), f.terminate().getEventTime());
    assertFalse(f.child.hasMessages());
  }

  @Test
  void permitsRepeatedEqualTimesWithoutInventingAnIncrement() {
    Fixture f = initialized(time(10));
    f.kit.run(f.next(time(0), time(0)));
    for (int i = 0; i < 3; i++) {
      assertEquals(time(0), f.request().getEventTime());
      f.kit.run(f.output(time(0), time(0)));
      assertEquals(time(0), f.execute().getEventTime());
      f.kit.run(f.complete(time(0), time(0)));
    }
    assertEquals(time(0), f.request().getEventTime());
  }

  @Test
  void rejectsRollbackInInitialOutputAndCompletionReportsWithoutAdvancing() {
    Fixture f = new Fixture(time(10));
    f.init(time(1));
    f.child.receiveMessage();
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.next(time(1), time(0))));
    assertFalse(f.child.hasMessages());
    f.kit.run(f.next(time(1), time(2)));
    f.request();
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.output(time(2), time(1))));
    assertFalse(f.child.hasMessages());
    f.kit.run(f.output(time(2), time(2)));
    f.execute();
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.complete(time(2), time(1))));
    assertFalse(f.child.hasMessages());
    f.kit.run(f.complete(time(2), time(3)));
    assertEquals(time(3), f.request().getEventTime());
  }

  @Test
  void rejectsWrongSenderRunReceiverAndEventTime() {
    Fixture f = initialized(time(10));
    NextInternalTimeReport<QuantizedSimTime> next = f.next(time(0), time(2));
    assertThrows(IllegalArgumentException.class, () -> f.kit.run(
        next.withSenderId("stranger")));
    assertThrows(IllegalArgumentException.class, () -> f.kit.run(
        next.withSimulationRunId("old")));
    assertThrows(IllegalArgumentException.class, () -> f.kit.run(
        next.withReceiverId("other")));
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.next(time(1), time(2))));
    assertFalse(f.child.hasMessages());
    f.kit.run(next);
    f.request();
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.output(time(1), time(2))));
    f.kit.run(f.output(time(2), time(2)));
    f.execute();
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.complete(time(1), time(3))));
    f.kit.run(f.complete(time(2), time(3)));
    assertEquals(time(3), f.request().getEventTime());
  }

  @Test
  void rejectsOutOfOrderAndDuplicateResponsesIncludingEqualTimeReplay() {
    Fixture f = initialized(time(10));
    assertThrows(IllegalStateException.class,
        () -> f.kit.run(f.output(time(0), time(0))));
    assertThrows(IllegalStateException.class,
        () -> f.kit.run(f.complete(time(0), time(0))));
    NextInternalTimeReport<QuantizedSimTime> next = f.next(time(0), time(0));
    f.kit.run(next);
    f.request();
    assertThrows(IllegalStateException.class, () -> f.kit.run(next));
    assertThrows(IllegalStateException.class,
        () -> f.kit.run(f.complete(time(0), time(0))));
    OutputReport<QuantizedSimTime> output = f.output(time(0), time(0));
    f.kit.run(output);
    f.execute();
    assertThrows(IllegalStateException.class, () -> f.kit.run(output));
    TransitionComplete<QuantizedSimTime> complete = f.complete(time(0), time(0));
    f.kit.run(complete);
    f.request();
    assertThrows(IllegalStateException.class, () -> f.kit.run(complete));
    assertThrows(IllegalStateException.class, () -> f.kit.run(output));
    assertFalse(f.child.hasMessages());
    f.kit.run(f.output(time(0), time(0)));
    f.execute();
    assertThrows(IllegalStateException.class, () -> f.kit.run(complete));
    assertFalse(f.child.hasMessages());
    f.kit.run(f.complete(time(0), time(1)));
    assertEquals(time(1), f.request().getEventTime());
  }

  @Test
  void validatesFiniteHorizonAndStartAndRejectsRepeatedInitialization() {
    TestInbox<DevsMessage> child = TestInbox.create();
    assertThrows(IllegalArgumentException.class,
        () -> RootCoordinator.enhanced(QuantizedSimTime.infinity(), child.getRef(), "child", CLOCK));
    assertThrows(NullPointerException.class,
        () -> RootCoordinator.enhanced(time(10), child.getRef(), "child", null));
    Fixture f = new Fixture(time(10));
    assertThrows(IllegalArgumentException.class, () -> f.init(QuantizedSimTime.infinity()));
    assertThrows(IllegalArgumentException.class, () -> f.init(time(11)));
    assertThrows(IllegalArgumentException.class, () -> f.init(LongSimTime.create(0)));
    assertFalse(f.child.hasMessages());
    f.init(time(10));
    f.child.receiveMessage();
    assertThrows(IllegalStateException.class, () -> f.init(time(10)));
    assertFalse(f.child.hasMessages());
    f.kit.run(f.next(time(10), QuantizedSimTime.infinity()));
    assertEquals(time(10), f.terminate().getEventTime());
  }

  @Test
  void validatesTerminationAcknowledgmentAndRejectsPrematureTermination() {
    Fixture f = initialized(time(10));
    assertThrows(IllegalStateException.class,
        () -> f.kit.run(f.terminated("child", "run")));
    f.kit.run(f.next(time(0), QuantizedSimTime.infinity()));
    f.terminate();
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.terminated("other", "run")));
    assertThrows(IllegalArgumentException.class,
        () -> f.kit.run(f.terminated("child", "old")));
    assertThrows(IllegalStateException.class,
        () -> f.kit.run(f.next(time(0), time(1))));
    assertTrue(f.kit.isAlive());
    f.kit.run(f.terminated("child", "run"));
    assertFalse(f.kit.isAlive());
  }

  @Test
  @SuppressWarnings("deprecation")
  void legacyStillAdvancesFromOutputWithoutExecuteTransition() {
    TestInbox<DevsMessage> child = TestInbox.create();
    BehaviorTestKit<DevsMessage> kit = BehaviorTestKit.create(
        RootCoordinator.create(LongSimTime.create(10), child.getRef(), "child"));
    kit.run(SimulationInit.<LongSimTime>builder().eventTime(LongSimTime.create(0))
        .simulationRunId("run").messageId("init").senderId("test").receiverId("root").build());
    SimulationInitMessage<?> init = assertInstanceOf(SimulationInitMessage.class,
        child.receiveMessage());
    assertTrue(init.getEnhancedClock().isEmpty());
    kit.run(NextInternalTimeReport.<LongSimTime>builder().eventTime(LongSimTime.create(0))
        .simulationRunId("run").messageId("next").senderId("test").receiverId("root")
        .nextInternalTime(LongSimTime.create(2)).build());
    assertEquals(LongSimTime.create(2),
        assertInstanceOf(RequestOutput.class, child.receiveMessage()).getEventTime());
    kit.run(OutputReport.<LongSimTime>builder().eventTime(LongSimTime.create(2))
        .payload(OutputReportPayload.builder().build()).simulationRunId("run").messageId("output1")
        .senderId("child").receiverId("root").nextInternalTime(LongSimTime.create(5)).build());
    assertEquals(LongSimTime.create(5),
        assertInstanceOf(RequestOutput.class, child.receiveMessage()).getEventTime());
    kit.run(OutputReport.<LongSimTime>builder().eventTime(LongSimTime.create(5))
        .payload(OutputReportPayload.builder().build()).simulationRunId("run").messageId("output2")
        .senderId("child").receiverId("root").nextInternalTime(LongSimTime.create(11)).build());
    assertEquals(LongSimTime.create(5),
        assertInstanceOf(SimulationTerminate.class, child.receiveMessage()).getEventTime());
    assertFalse(child.hasMessages());
  }

  private static QuantizedSimTime time(long value) {
    return QuantizedSimTime.of(Rational.of(value));
  }

  private static Fixture initialized(QuantizedSimTime end) {
    Fixture f = new Fixture(end);
    f.init(time(0));
    f.child.receiveMessage();
    return f;
  }

  private static Fixture executing(QuantizedSimTime event) {
    Fixture f = initialized(time(10));
    f.kit.run(f.next(time(0), event));
    f.request();
    f.kit.run(f.output(event, event));
    f.execute();
    return f;
  }

  private static final class Fixture {
    final TestInbox<DevsMessage> child = TestInbox.create();
    final BehaviorTestKit<DevsMessage> kit;
    private int messageId;

    Fixture(QuantizedSimTime end) {
      kit = BehaviorTestKit.create(RootCoordinator.enhanced(end, child.getRef(), "child", CLOCK));
    }

    void init(SimTime start) {
      kit.run(SimulationInit.<SimTime>builder().eventTime(start).simulationRunId("run")
          .messageId("init").senderId("test").receiverId("root").build());
    }

    NextInternalTimeReport<QuantizedSimTime> next(QuantizedSimTime event, QuantizedSimTime next) {
      return NextInternalTimeReport.<QuantizedSimTime>builder().eventTime(event)
          .simulationRunId("run").messageId("next" + messageId++)
          .senderId("child").receiverId("root").nextInternalTime(next).build();
    }

    OutputReport<QuantizedSimTime> output(QuantizedSimTime event, QuantizedSimTime next) {
      return OutputReport.<QuantizedSimTime>builder().eventTime(event)
          .payload(OutputReportPayload.builder().build())
          .simulationRunId("run").messageId("output" + messageId++)
          .senderId("child").receiverId("root").nextInternalTime(next).build();
    }

    TransitionComplete<QuantizedSimTime> complete(QuantizedSimTime event, QuantizedSimTime next) {
      return TransitionComplete.<QuantizedSimTime>builder().eventTime(event)
          .simulationRunId("run").messageId("complete" + messageId++)
          .senderId("child").receiverId("root").nextInternalTime(next).build();
    }

    ModelTerminated<QuantizedSimTime> terminated(String sender, String run) {
      return ModelTerminated.<QuantizedSimTime>builder().simulationRunId(run)
          .messageId("terminated" + messageId++).senderId(sender).receiverId("root").build();
    }

    RequestOutput<?> request() {
      return assertInstanceOf(RequestOutput.class, child.receiveMessage());
    }

    ExecuteTransition<?> execute() {
      return assertInstanceOf(ExecuteTransition.class, child.receiveMessage());
    }

    SimulationTerminate<?> terminate() {
      return assertInstanceOf(SimulationTerminate.class, child.receiveMessage());
    }
  }
}
