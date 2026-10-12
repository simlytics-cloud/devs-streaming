package devs.proxy;

import devs.time.DecimalTestValues;
import static org.junit.jupiter.api.Assertions.*;

import devs.iso.*;
import devs.iso.time.*;
import devs.utils.DevsObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

class ExactTimeTransportTest {
  static class Publisher implements MessagePublisher {
    final List<Long> sequences = new ArrayList<>();
    final List<String> payloads = new ArrayList<>();
    int closes;
    public void publish(long sequence, String payload) {
      sequences.add(sequence);
      payloads.add(payload);
    }
    public void close() { closes++; }
  }

  static class Receiver implements MessageReceiver {
    Consumer<String> handler;
    int shutdowns;
    public void subscribe(Consumer<String> handler) { this.handler = handler; }
    public void shutdown() { shutdowns++; }
    void emit(String payload) { handler.accept(payload); }
  }

  private static class Proxy extends AbstractDevsStreamProxy<SimTime> {
    Proxy(ActorContext<DevsMessage> context, Publisher publisher, TimeEndpointPolicy policy) {
      super(context, "model", publisher, policy);
    }
  }

  private static class StreamReceiver extends AbstractDevsStreamReceiver {
    StreamReceiver(ActorContext<DevsMessage> context, ActorRef<DevsMessage> target,
        ActorRef<DevsMessage> sender, Receiver receiver, TimeEndpointPolicy policy) {
      super(context, target, sender, "model", receiver, policy);
    }
  }

  static SimulationInit<SimTime> init(SimTime time) {
    return SimulationInit.<SimTime>builder().eventTime(time).simulationRunId("run")
        .messageId("init").senderId("parent").receiverId("model").build();
  }

  static NextInternalTimeReport<SimTime> next(SimTime event, SimTime next) {
    return NextInternalTimeReport.<SimTime>builder().eventTime(event)
        .simulationRunId("run").messageId("next").senderId("remote").receiverId("model")
        .nextInternalTime(next).build();
  }

  static ModelTerminated<SimTime> terminated(String receiverId) {
    return ModelTerminated.<SimTime>builder().simulationRunId("run").messageId("done")
        .senderId("remote").receiverId(receiverId).build();
  }

  static List<DevsSimMessage> messages(SimTime time, SimTime next) {
    return List.of(init(time),
        RequestOutput.<SimTime>builder().eventTime(time).simulationRunId("run").messageId("request")
            .senderId("parent").receiverId("model").build(),
        ExecuteTransition.<SimTime>builder().eventTime(time).payload(ExecuteTransitionPayload.builder().build())
            .simulationRunId("run").messageId("execute").senderId("parent").receiverId("model").build(),
        next(time, next),
        OutputReport.<SimTime>builder().eventTime(time).payload(OutputReportPayload.builder().build())
            .simulationRunId("run").messageId("output").senderId("remote").receiverId("model")
            .nextInternalTime(next).build(),
        TransitionComplete.<SimTime>builder().eventTime(time).simulationRunId("run")
            .messageId("complete").senderId("remote").receiverId("model").nextInternalTime(next).build(),
        SimulationTerminate.<SimTime>builder().eventTime(time).simulationRunId("run").messageId("stop")
            .senderId("parent").receiverId("model").build(), terminated("model"));
  }

  static List<TimeEndpointPolicy> exactPolicies() {
    return List.of(TimeEndpointPolicy.decimalNative(), TimeEndpointPolicy.decimalNative());
  }

  static SimTime finite(TimeEndpointPolicy policy) {
    return policy.acceptedTypes().contains(DecimalSimTime.class)
        ? DecimalSimTime.of(new BigDecimal("0.1")) : DecimalSimTime.of(new BigDecimal("0.3"));
  }

  static SimTime infinity(TimeEndpointPolicy policy) {
    return policy.acceptedTypes().contains(DecimalSimTime.class)
        ? DecimalSimTime.infinity() : DecimalSimTime.infinity();
  }

  static SimTime overLimit(TimeEndpointPolicy policy) {
    return policy.acceptedTypes().contains(DecimalSimTime.class)
        ? DecimalSimTime.of(new BigDecimal("1e-10001"))
        : DecimalSimTime.of(DecimalTestValues.of(java.math.BigInteger.TEN.pow(TimeWireLimits.MAX_DIGITS),
            java.math.BigInteger.ONE));
  }

  @Test
  void exactDecimalExchangePreservesEveryMessageAndTimestamp() throws Exception {
    for (TimeEndpointPolicy policy : exactPolicies()) {
      for (DevsSimMessage message : messages(finite(policy), infinity(policy))) {
        Publisher publisher = new Publisher();
        Receiver receiver = new Receiver();
        TestInbox<DevsMessage> target = TestInbox.create();
        TestInbox<DevsMessage> sender = TestInbox.create();
        var outbound = BehaviorTestKit.<DevsMessage>create(Behaviors.setup(
            context -> new Proxy(context, publisher, policy)));
        var inbound = BehaviorTestKit.<DevsMessage>create(Behaviors.setup(
            context -> new StreamReceiver(context, target.getRef(), sender.getRef(), receiver, policy)));
        outbound.run(message instanceof SimulationInit<?> init
            ? new SimulationInitMessage<>(init, sender.getRef()) : message);
        String payload = publisher.payloads.getFirst();
        assertEquals(DevsJsonBinding.EXACT_PROFILE,
            DevsObjectMapper.buildObjectMapper().readTree(payload).get("wireProfile").textValue());
        assertEquals(message, new DevsJsonBinding(policy).decode(payload));
        receiver.emit(payload);
        assertFalse(target.hasMessages());
        inbound.runOne();
        DevsMessage actual = target.receiveMessage();
        if (actual instanceof SimulationInitMessage<?> init) {
          assertEquals(sender.getRef(), init.getParent());
          assertTrue(init.getEnhancedClock().isEmpty());
          actual = init.getSimulationInit();
        }
        assertEquals(message, actual);
        assertEquals(message.getClass(), actual.getClass());
        if (actual instanceof TimedMessage<?> timed) {
          assertEquals(finite(policy).getClass(), timed.getEventTime().getClass());
        }
        boolean terminal = message instanceof SimulationTerminate<?> || message instanceof ModelTerminated<?>;
        assertEquals(terminal ? 1 : 0, publisher.closes);
        assertEquals(terminal ? 1 : 0, receiver.shutdowns);
        assertEquals(!terminal, outbound.isAlive());
        assertEquals(!terminal, inbound.isAlive());
        assertEquals(List.of(0L), publisher.sequences);
      }
    }
  }

  @Test
  void numericAndTimeFreeMessagesStillRequireExactEnvelopeMarker() throws Exception {
    TimeEndpointPolicy policy = TimeEndpointPolicy.recorder(Set.of(LongSimTime.class));
    for (DevsSimMessage message : List.of(init(LongSimTime.create(7)), terminated("model"))) {
      Publisher publisher = new Publisher();
      Receiver receiver = new Receiver();
      TestInbox<DevsMessage> target = TestInbox.create();
      var inbound = BehaviorTestKit.<DevsMessage>create(Behaviors.setup(context -> new StreamReceiver(
          context, target.getRef(), target.getRef(), receiver, policy)));
      var outbound = BehaviorTestKit.<DevsMessage>create(Behaviors.setup(
          context -> new Proxy(context, publisher, policy)));
      outbound.run(message);
      String exact = publisher.payloads.getFirst();
      String legacy = new DevsJsonBinding(TimeEndpointPolicy.legacy()).encode(message);
      receiver.emit(legacy);
      assertFalse(inbound.selfInbox().hasMessages());
      receiver.emit(exact);
      inbound.runOne();
      assertTrue(target.hasMessages());
    }
  }
}