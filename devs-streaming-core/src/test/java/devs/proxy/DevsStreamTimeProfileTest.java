package devs.proxy;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.*;
import devs.iso.time.*;
import devs.time.EnhancedClockContext;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

class DevsStreamTimeProfileTest {
  private static class Publisher implements MessagePublisher {
    final List<Long> sequences = new ArrayList<>();
    final List<String> payloads = new ArrayList<>();
    boolean closed;
    public void publish(long sequence, String payload) {
      sequences.add(sequence);
      payloads.add(payload);
    }
    public void close() { closed = true; }
  }

  private static class Proxy extends AbstractDevsStreamProxy<SimTime> {
    Proxy(ActorContext<DevsMessage> context, Publisher publisher) {
      super(context, "component", publisher);
    }
    Proxy(ActorContext<DevsMessage> context, Publisher publisher, TimeEndpointPolicy policy) {
      super(context, "component", publisher, policy);
    }
  }

  private BehaviorTestKit<DevsMessage> proxy(Publisher publisher) {
    return BehaviorTestKit.create(Behaviors.setup(context -> new Proxy(context, publisher)));
  }

  private SimulationInit<SimTime> init(SimTime time) {
    return SimulationInit.<SimTime>builder().eventTime(time).simulationRunId("run")
        .messageId("init").senderId("parent").receiverId("child").build();
  }

  private NextInternalTimeReport<SimTime> next(SimTime event, SimTime next) {
    return NextInternalTimeReport.<SimTime>builder().eventTime(event).simulationRunId("run")
        .messageId("next").senderId("child").receiverId("parent").nextInternalTime(next).build();
  }

  private OutputReport<SimTime> output(SimTime event, SimTime next) {
    return OutputReport.<SimTime>builder().eventTime(event).payload(OutputReportPayload.builder().build())
        .simulationRunId("run").messageId("output").senderId("child").receiverId("parent")
        .nextInternalTime(next).build();
  }

  private TransitionComplete<SimTime> complete(SimTime event, SimTime next) {
    return TransitionComplete.<SimTime>builder().eventTime(event).simulationRunId("run")
        .messageId("complete").senderId("child").receiverId("parent").nextInternalTime(next).build();
  }

  @Test
  void nativeProfilesKeepNumericJsonRoundTripsAndWrappedInitializationPayload() throws Exception {
    Publisher publisher = new Publisher();
    BehaviorTestKit<DevsMessage> proxy = proxy(publisher);
    var mapper = DevsObjectMapper.buildObjectMapper();
    SimulationInit<SimTime> init = init(LongSimTime.create(7));
    proxy.run(new SimulationInitMessage<>(init, TestInbox.<DevsMessage>create().getRef()));
    NextInternalTimeReport<SimTime> next = next(DoubleSimTime.create(0.1), DoubleSimTime.create(1.5));
    proxy.run(next);
    assertEquals(List.of(mapper.writeValueAsString(init), mapper.writeValueAsString(next)), publisher.payloads);
    assertEquals(List.of(0L, 1L), publisher.sequences);
    assertEquals(7, mapper.readTree(publisher.payloads.getFirst()).get("eventTime").longValue());
    assertEquals(0.1, mapper.readTree(publisher.payloads.get(1)).get("eventTime").doubleValue());
    assertEquals(1.5, mapper.readTree(publisher.payloads.get(1)).get("nextInternalTime").doubleValue());
    NextInternalTimeReport<?> decoded = mapper.readValue(publisher.payloads.get(1), NextInternalTimeReport.class);
    assertEquals(next.getEventTime(), decoded.getEventTime());
    assertEquals(next.getNextInternalTime(), decoded.getNextInternalTime());
    assertFalse(publisher.closed);
  }

  @Test
  void rejectsAllUnsupportedEventAndNextTimestampFieldsBeforePublishing() {
    SimTime nativeTime = LongSimTime.create(0);
    for (SimTime unsupported : List.of(QuantizedSimTime.of(Rational.of(1, 3)),
        QuantizedSimTime.infinity(), DecimalSimTime.of(new BigDecimal("0.1")), DecimalSimTime.infinity())) {
      List<DevsMessage> messages = List.of(
          init(unsupported), next(unsupported, nativeTime), next(nativeTime, unsupported),
          output(unsupported, nativeTime), output(nativeTime, unsupported),
          complete(unsupported, nativeTime), complete(nativeTime, unsupported),
          RequestOutput.<SimTime>builder().eventTime(unsupported).simulationRunId("run")
              .messageId("request").senderId("parent").receiverId("child").build(),
          ExecuteTransition.<SimTime>builder().eventTime(unsupported)
              .payload(ExecuteTransitionPayload.builder().build()).simulationRunId("run")
              .messageId("execute").senderId("parent").receiverId("child").build(),
          SimulationTerminate.<SimTime>builder().eventTime(unsupported).simulationRunId("run")
              .messageId("terminate").senderId("parent").receiverId("child")
              .payload(SimulationTerminatePayload.builder().reason("done").build()).build(),
          new SimulationInitMessage<>(init(unsupported), TestInbox.<DevsMessage>create().getRef()));
      for (DevsMessage message : messages) {
        Publisher publisher = new Publisher();
        assertThrows(IllegalArgumentException.class, () -> proxy(publisher).run(message));
        assertTrue(publisher.payloads.isEmpty());
        assertTrue(publisher.sequences.isEmpty());
        assertFalse(publisher.closed);
      }
    }
  }

  @Test
  void enhancedContextCannotBeSilentlyDroppedEvenWithNativeInitializationTime() {
    Publisher publisher = new Publisher();
    SimulationInitMessage<SimTime> message = new SimulationInitMessage<>(init(LongSimTime.create(0)),
        TestInbox.<DevsMessage>create().getRef(), new EnhancedClockContext("shared", Rational.ZERO));
    assertThrows(IllegalArgumentException.class, () -> proxy(publisher).run(message));
    assertTrue(publisher.payloads.isEmpty());
  }

  @Test
  void exactRejectsWrongEventNextTypesLimitsAndEnhancedContextBeforePublishing() {
    for (TimeEndpointPolicy policy : ExactTimeTransportTest.exactPolicies()) {
      SimTime accepted = ExactTimeTransportTest.finite(policy);
      SimTime wrong = LongSimTime.create(0);
      SimTime huge = ExactTimeTransportTest.overLimit(policy);
      for (DevsMessage message : List.of(init(wrong), next(wrong, accepted), next(accepted, wrong),
          output(accepted, wrong), complete(accepted, wrong), init(huge), next(accepted, huge),
          output(accepted, huge), complete(accepted, huge), new DevsMessage() {},
          new SimulationInitMessage<>(init(accepted), TestInbox.<DevsMessage>create().getRef(),
              new EnhancedClockContext("shared", Rational.ZERO)))) {
        Publisher publisher = new Publisher();
        var proxy = BehaviorTestKit.<DevsMessage>create(Behaviors.setup(context -> new Proxy(context, publisher, policy)));
        assertThrows(IllegalArgumentException.class, () -> proxy.run(message));
        assertTrue(publisher.payloads.isEmpty());
        assertTrue(publisher.sequences.isEmpty());
        assertFalse(publisher.closed);
        proxy.run(init(accepted));
        assertEquals(List.of(0L), publisher.sequences);
      }
    }
  }
}
