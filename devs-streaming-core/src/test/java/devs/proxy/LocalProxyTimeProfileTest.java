package devs.proxy;

import static devs.proxy.ExactTimeTransportTest.*;
import static org.junit.jupiter.api.Assertions.*;

import devs.iso.*;
import devs.iso.time.*;
import devs.time.EnhancedClockContext;
import devs.time.Rational;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

class LocalProxyTimeProfileTest {
  private static class LocalProxy extends AbstractLocalProxy<SimTime> {
    LocalProxy(ActorContext<DevsMessage> context, Publisher publisher, Receiver receiver,
        TimeEndpointPolicy policy) {
      super(context, "remote", "model", publisher, receiver, policy);
    }
    LocalProxy(ActorContext<DevsMessage> context, Publisher publisher, Receiver receiver) {
      super(context, "remote", "model", publisher, receiver);
    }
  }

  private BehaviorTestKit<DevsMessage> proxy(Publisher publisher, Receiver receiver,
      TimeEndpointPolicy policy) {
    return BehaviorTestKit.create(Behaviors.setup(context -> new LocalProxy(context, publisher, receiver, policy)));
  }

  @Test
  void exactInboundUsesMailboxAndLearnedParentWithoutWrappingOrRepublishing() throws Exception {
    for (TimeEndpointPolicy policy : exactPolicies()) {
      Publisher publisher = new Publisher();
      Receiver transport = new Receiver();
      TestInbox<DevsMessage> parent = TestInbox.create();
      var actor = proxy(publisher, transport, policy);
      var binding = new DevsJsonBinding(policy);
      transport.emit(binding.encode(next(finite(policy), infinity(policy))));
      actor.runOne();
      assertFalse(parent.hasMessages());
      assertTrue(publisher.payloads.isEmpty());
      actor.run(new SimulationInitMessage<>(init(finite(policy)), parent.getRef()));
      assertEquals(init(finite(policy)), binding.decode(publisher.payloads.getFirst()));
      for (DevsSimMessage message : messages(finite(policy), infinity(policy))) {
        transport.emit(binding.encode(message));
        assertFalse(parent.hasMessages());
        actor.runOne();
        assertEquals(message, parent.receiveMessage());
        assertEquals(1, publisher.payloads.size());
        assertEquals(0, publisher.closes);
        assertEquals(0, transport.shutdowns);
        assertTrue(actor.isAlive());
      }
      transport.emit(binding.encode(terminated("other")));
      actor.runOne();
      assertFalse(parent.hasMessages());
      actor.run(terminated("remote"));
      assertEquals(List.of(0L, 1L), publisher.sequences);
      assertEquals(1, publisher.closes);
      assertEquals(1, transport.shutdowns);
      assertFalse(actor.isAlive());
    }
  }

  @Test
  void rejectedInitializationDoesNotCaptureOrReplaceParentOrAdvanceSequence() throws Exception {
    for (TimeEndpointPolicy policy : exactPolicies()) {
      for (SimTime invalid : List.of(LongSimTime.create(0), overLimit(policy))) {
        Publisher publisher = new Publisher();
        Receiver transport = new Receiver();
        TestInbox<DevsMessage> rejectedParent = TestInbox.create();
        TestInbox<DevsMessage> acceptedParent = TestInbox.create();
        var actor = proxy(publisher, transport, policy);
        var binding = new DevsJsonBinding(policy);
        assertThrows(IllegalArgumentException.class,
            () -> actor.run(new SimulationInitMessage<>(init(invalid), rejectedParent.getRef())));
        transport.emit(binding.encode(next(finite(policy), infinity(policy))));
        actor.runOne();
        assertFalse(rejectedParent.hasMessages());
        assertTrue(publisher.payloads.isEmpty());
        assertEquals(0, publisher.closes);
        assertEquals(0, transport.shutdowns);
        actor.run(new SimulationInitMessage<>(init(finite(policy)), acceptedParent.getRef()));
        assertThrows(IllegalArgumentException.class,
            () -> actor.run(new SimulationInitMessage<>(init(invalid), rejectedParent.getRef())));
        transport.emit(binding.encode(next(finite(policy), infinity(policy))));
        actor.runOne();
        assertEquals(next(finite(policy), infinity(policy)), acceptedParent.receiveMessage());
        assertFalse(rejectedParent.hasMessages());
        assertEquals(List.of(0L), publisher.sequences);
      }
    }
  }

  @Test
  void enhancedInitializationIsRejectedForLegacyAndBothExactPolicies() throws Exception {
    for (TimeEndpointPolicy policy : List.of(TimeEndpointPolicy.legacy(), TimeEndpointPolicy.decimalNative(),
        TimeEndpointPolicy.rationalCompatible())) {
      SimTime time = policy.profile() == TimeWireProfile.LEGACY_NUMERIC_V1 ? LongSimTime.create(0) : finite(policy);
      Publisher publisher = new Publisher();
      Receiver transport = new Receiver();
      TestInbox<DevsMessage> parent = TestInbox.create();
      var actor = proxy(publisher, transport, policy);
      assertThrows(IllegalArgumentException.class, () -> actor.run(new SimulationInitMessage<>(init(time),
          parent.getRef(), new EnhancedClockContext("shared", Rational.ZERO))));
      transport.emit(new DevsJsonBinding(policy).encode(init(time)));
      actor.runOne();
      assertFalse(parent.hasMessages());
      assertTrue(publisher.payloads.isEmpty());
      assertEquals(0, publisher.closes);
      assertEquals(0, transport.shutdowns);
    }
  }

  @Test
  void rawInboundCannotBypassMarkerTypeOrLimitValidation() throws Exception {
    for (TimeEndpointPolicy policy : exactPolicies()) {
      Publisher publisher = new Publisher();
      Receiver transport = new Receiver();
      TestInbox<DevsMessage> parent = TestInbox.create();
      var actor = proxy(publisher, transport, policy);
      actor.run(new SimulationInitMessage<>(init(finite(policy)), parent.getRef()));
      String valid = new DevsJsonBinding(policy).encode(next(finite(policy), infinity(policy)));
      TimeEndpointPolicy other = policy.acceptedTypes().contains(DecimalSimTime.class)
          ? TimeEndpointPolicy.rationalCompatible() : TimeEndpointPolicy.decimalNative();
      String tooLarge = policy.acceptedTypes().contains(DecimalSimTime.class)
          ? valid.replace("\"0.1\"", "\"1e-10001\"")
          : valid.replace("\"numerator\":\"1\"", "\"numerator\":\"" + "9".repeat(4097) + "\"");
      assertNotEquals(valid, tooLarge);
      for (String invalid : List.of(valid.replace(DevsJsonBinding.EXACT_PROFILE, "unknown"),
          new DevsJsonBinding(TimeEndpointPolicy.legacy()).encode(terminated("model")),
          new DevsJsonBinding(other).encode(next(finite(other), infinity(other))), tooLarge,
          "{\"private-payload-sentinel\":")) {
        transport.emit(invalid);
        assertFalse(actor.selfInbox().hasMessages());
        assertFalse(parent.hasMessages());
        assertEquals(1, publisher.payloads.size());
        assertEquals(0, publisher.closes);
        assertEquals(0, transport.shutdowns);
      }
      assertFalse(actor.getAllLogEntries().toString().contains("private-payload-sentinel"));
      transport.emit(valid);
      actor.runOne();
      assertEquals(next(finite(policy), infinity(policy)), parent.receiveMessage());
    }
  }

  @Test
  void defaultConstructorKeepsLegacyAndInvalidOutboundTerminateHasNoSideEffects() throws Exception {
    Publisher publisher = new Publisher();
    Receiver transport = new Receiver();
    TestInbox<DevsMessage> parent = TestInbox.create();
    var actor = BehaviorTestKit.<DevsMessage>create(Behaviors.setup(context -> new LocalProxy(context, publisher, transport)));
    actor.run(new SimulationInitMessage<>(init(LongSimTime.create(0)), parent.getRef()));
    transport.emit(new DevsJsonBinding(TimeEndpointPolicy.legacy()).encode(next(LongSimTime.create(0), LongSimTime.create(1))));
    actor.runOne();
    assertEquals(next(LongSimTime.create(0), LongSimTime.create(1)), parent.receiveMessage());
    assertThrows(IllegalArgumentException.class, () -> actor.run(SimulationTerminate.<SimTime>builder()
        .eventTime(DecimalSimTime.infinity()).simulationRunId("run").messageId("bad")
        .senderId("parent").receiverId("remote").build()));
    assertEquals(List.of(0L), publisher.sequences);
    assertEquals(0, publisher.closes);
    assertEquals(0, transport.shutdowns);
    assertTrue(actor.isAlive());
    actor.run(terminated("remote"));
    assertEquals(List.of(0L, 1L), publisher.sequences);
    assertEquals(1, publisher.closes);
    assertEquals(1, transport.shutdowns);
    assertFalse(actor.isAlive());
  }
}