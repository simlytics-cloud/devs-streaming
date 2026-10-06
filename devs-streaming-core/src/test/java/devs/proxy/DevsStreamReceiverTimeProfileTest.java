package devs.proxy;

import static devs.proxy.ExactTimeTransportTest.*;
import static org.junit.jupiter.api.Assertions.*;

import devs.iso.*;
import devs.iso.time.*;
import devs.time.EnhancedClockContext;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

class DevsStreamReceiverTimeProfileTest {
  private static class StreamReceiver extends AbstractDevsStreamReceiver {
    StreamReceiver(ActorContext<DevsMessage> context, ActorRef<DevsMessage> target,
        ActorRef<DevsMessage> sender, Receiver receiver, TimeEndpointPolicy policy) {
      super(context, target, sender, "model", receiver, policy);
    }
    StreamReceiver(ActorContext<DevsMessage> context, ActorRef<DevsMessage> target,
        ActorRef<DevsMessage> sender, Receiver receiver) {
      super(context, target, sender, "model", receiver);
    }
  }

  private BehaviorTestKit<DevsMessage> receiver(Receiver receiver, TestInbox<DevsMessage> target,
      TimeEndpointPolicy policy) {
    return BehaviorTestKit.create(Behaviors.setup(context ->
        new StreamReceiver(context, target.getRef(), target.getRef(), receiver, policy)));
  }

  @Test
  void defaultConstructorKeepsLegacyFilteringWrappingAndTermination() throws Exception {
    Receiver transport = new Receiver();
    TestInbox<DevsMessage> target = TestInbox.create();
    TestInbox<DevsMessage> sender = TestInbox.create();
    var actor = BehaviorTestKit.<DevsMessage>create(Behaviors.setup(context ->
        new StreamReceiver(context, target.getRef(), sender.getRef(), transport)));
    SimulationInit<SimTime> init = init(LongSimTime.create(7));
    transport.emit(new DevsJsonBinding(TimeEndpointPolicy.legacy()).encode(init));
    actor.runOne();
    var wrapped = assertInstanceOf(SimulationInitMessage.class, target.receiveMessage());
    assertEquals(init, wrapped.getSimulationInit());
    assertEquals(sender.getRef(), wrapped.getParent());
    actor.run(terminated("other"));
    assertFalse(target.hasMessages());
    assertEquals(0, transport.shutdowns);
    assertTrue(actor.isAlive());
    actor.run(terminated("model"));
    assertEquals(terminated("model"), target.receiveMessage());
    assertEquals(1, transport.shutdowns);
    assertFalse(actor.isAlive());
  }

  @Test
  void rejectsRawMarkersTypesLimitsAndMalformedRecordsWithoutPayloadLogs() throws Exception {
    for (TimeEndpointPolicy policy : exactPolicies()) {
      Receiver transport = new Receiver();
      TestInbox<DevsMessage> target = TestInbox.create();
      var actor = receiver(transport, target, policy);
      String valid = new DevsJsonBinding(policy).encode(next(finite(policy), infinity(policy)));
      var mapper = DevsObjectMapper.buildObjectMapper();
      var missing = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(valid);
      missing.remove("wireProfile");
      TimeEndpointPolicy other = policy.acceptedTypes().contains(DecimalSimTime.class)
          ? TimeEndpointPolicy.rationalCompatible() : TimeEndpointPolicy.decimalNative();
      String wrongType = new DevsJsonBinding(other).encode(next(finite(other), infinity(other)));
      String tooLarge = policy.acceptedTypes().contains(DecimalSimTime.class)
          ? valid.replace("\"0.1\"", "\"1e-10001\"")
          : valid.replace("\"numerator\":\"1\"", "\"numerator\":\"" + "9".repeat(4097) + "\"");
      assertNotEquals(valid, tooLarge);
      for (String invalid : List.of(missing.toString(), valid.replace(DevsJsonBinding.EXACT_PROFILE, "unknown"),
          "{\"wireProfile\":\"devs-exact-time-v1\"," + valid.substring(1), wrongType, tooLarge,
          "{\"private-payload-sentinel\":", new DevsJsonBinding(TimeEndpointPolicy.legacy()).encode(terminated("model")))) {
        transport.emit(invalid);
        assertFalse(actor.selfInbox().hasMessages());
        assertFalse(target.hasMessages());
        assertEquals(0, transport.shutdowns);
        assertTrue(actor.isAlive());
      }
      assertFalse(actor.getAllLogEntries().toString().contains("private-payload-sentinel"));
      transport.emit(valid);
      actor.runOne();
      assertEquals(next(finite(policy), infinity(policy)), target.receiveMessage());
    }
    Receiver transport = new Receiver();
    TestInbox<DevsMessage> target = TestInbox.create();
    var legacy = receiver(transport, target, TimeEndpointPolicy.legacy());
    transport.emit(new DevsJsonBinding(TimeEndpointPolicy.decimalNative()).encode(terminated("model")));
    assertFalse(legacy.selfInbox().hasMessages());
    assertEquals(0, transport.shutdowns);
  }

  @Test
  void directMessagesAreValidatedBeforeFilteringWrappingOrDispatch() {
    for (TimeEndpointPolicy policy : exactPolicies()) {
      Receiver transport = new Receiver();
      TestInbox<DevsMessage> target = TestInbox.create();
      var actor = receiver(transport, target, policy);
      SimTime accepted = finite(policy);
      SimTime rejected = policy.acceptedTypes().contains(DecimalSimTime.class)
          ? QuantizedSimTime.of(Rational.of(1, 3)) : DecimalSimTime.of(new java.math.BigDecimal("0.1"));
      for (DevsMessage invalid : List.of(init(rejected), next(rejected, accepted), next(accepted, rejected),
          next(accepted, overLimit(policy)), init(overLimit(policy)), new DevsMessage() {},
          new SimulationInitMessage<>(init(accepted), target.getRef(),
              new EnhancedClockContext("shared", Rational.ZERO)))) {
        actor.run(invalid);
        assertFalse(target.hasMessages());
        assertEquals(0, transport.shutdowns);
        assertTrue(actor.isAlive());
      }
      actor.run(new SimulationInitMessage<>(init(accepted), TestInbox.<DevsMessage>create().getRef()));
      var wrapped = assertInstanceOf(SimulationInitMessage.class, target.receiveMessage());
      assertEquals(init(accepted), wrapped.getSimulationInit());
      assertEquals(target.getRef(), wrapped.getParent());
      actor.run(next(accepted, infinity(policy)));
      assertEquals(next(accepted, infinity(policy)), target.receiveMessage());
    }
  }

  @Test
  void legacyDirectMessagesCannotBypassExactTimeOrEnhancedContextGuard() {
    Receiver transport = new Receiver();
    TestInbox<DevsMessage> target = TestInbox.create();
    var actor = receiver(transport, target, TimeEndpointPolicy.legacy());
    actor.run(init(DecimalSimTime.of(new java.math.BigDecimal("0.1"))));
    actor.run(new SimulationInitMessage<>(init(LongSimTime.create(0)), target.getRef(),
        new EnhancedClockContext("shared", Rational.ZERO)));
    assertFalse(target.hasMessages());
    assertEquals(0, transport.shutdowns);
    assertTrue(actor.isAlive());
  }
}