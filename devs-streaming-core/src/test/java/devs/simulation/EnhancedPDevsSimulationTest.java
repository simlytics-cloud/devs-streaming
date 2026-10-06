package devs.simulation;

import static org.junit.jupiter.api.Assertions.*;

import devs.*;
import devs.iso.*;
import devs.iso.time.*;
import devs.time.*;
import java.util.List;
import java.util.UUID;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class EnhancedPDevsSimulationTest {
  private static final ActorTestKit kit = ActorTestKit.create();
  private final EnhancedClockContext clock = new EnhancedClockContext("shared", Rational.of(10));

  @AfterAll
  static void shutdown() { kit.shutdownTestKit(); }

  private QuantizedSimTime time(long numerator, long denominator) {
    return QuantizedSimTime.of(Rational.of(numerator, denominator));
  }

  private PortValue<String> value(String value, String port) {
    return PortValue.<String>builder().value(value).portName(port).build();
  }

  private <N extends SimTime> SimulatorProvider<N> provider(PDEVSModel<N, Boolean> model) {
    return new SimulatorProvider<>() {
      public String getModelIdentifier() { return model.getModelIdentifier(); }
      public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, N initial) {
        return context.spawn(PDevsSimulator.create(model, initial), model.getModelIdentifier());
      }
    };
  }

  private ChildBinding<LongSimTime> target(TestProbe<TransitionRecord> records, boolean passive) {
    return ChildBinding.nativeProvider(provider(new Target(records.ref(), passive)), LongSimTime.class,
        new TimeMapping<>(new LongTimeCodec(),
            TimeSemantics.of(Rational.of(1, 1000), Rational.of(99, 10))));
  }

  private void initialize(ActorRef<DevsMessage> actor, TestProbe<DevsMessage> parent, String id) {
    actor.tell(new SimulationInitMessage<>(SimulationInit.<QuantizedSimTime>builder()
        .eventTime(time(10, 1)).simulationRunId("run").messageId(UUID.randomUUID().toString())
        .senderId("parent").receiverId(id).build(), parent.ref(), clock));
  }

  private ExecuteTransition<QuantizedSimTime> execute(String id, QuantizedSimTime at,
      List<PortValue<?>> inputs) {
    return ExecuteTransition.<QuantizedSimTime>builder().eventTime(at)
        .payload(ExecuteTransitionPayload.builder().addAllInputs(inputs).build())
        .simulationRunId("run").messageId(UUID.randomUUID().toString())
        .senderId("parent").receiverId(id).build();
  }

  @Test
  void nestedConfluenceMergesSameTimeParentInputBeforeOneRealSimulatorTransition() {
    TestProbe<TransitionRecord> records = kit.createTestProbe();
    TestProbe<DevsMessage> parent = kit.createTestProbe();
    CoupledModelFactory<QuantizedSimTime> nested = CoupledModelFactory.enhanced("nested",
        List.of(target(records, false)), PDevsCouplings.builder("nested")
            .addConnection("nested", "in", "target", "in")
            .addConnection("target", "out", "nested", "out").build(),
        new CoordinatorExecutionConfig(clock));
    ChildBinding<DoubleSimTime> source = ChildBinding.nativeProvider(provider(new Source()),
        DoubleSimTime.class, new TimeMapping<>(new DoubleTimeCodec(),
            TimeSemantics.of(Rational.of(1, 1000), Rational.of(10))));
    CoupledModelFactory<QuantizedSimTime> outer = CoupledModelFactory.enhanced("outer",
        List.of(source, ChildBinding.coordinator(nested)), PDevsCouplings.builder("outer")
            .addConnection("source", "out", "nested", "in")
            .addConnection("outer", "in", "nested", "in")
            .addConnection("nested", "out", "outer", "out").build(),
        new CoordinatorExecutionConfig(clock));
    ActorRef<DevsMessage> actor = kit.spawn(outer.create(time(10, 1)));
    initialize(actor, parent, "outer");
    assertEquals(time(5001, 500), parent.expectMessageClass(NextInternalTimeReport.class).getNextInternalTime());
    actor.tell(RequestOutput.<QuantizedSimTime>builder().eventTime(time(5001, 500))
        .simulationRunId("run").messageId("output").senderId("parent").receiverId("outer").build());
    OutputReport<?> output = parent.expectMessageClass(OutputReport.class);
    assertEquals(List.of(value("tick", "out")), output.getPayload().getOutputs());
    records.expectNoMessage(java.time.Duration.ofMillis(50));
    actor.tell(execute("outer", time(5001, 500), List.of(value("parent", "in"))));
    TransitionRecord record = records.receiveMessage();
    assertEquals("confluent", record.kind());
    assertEquals(List.of("source", "parent"), record.values());
    TransitionComplete<?> complete = parent.expectMessageClass(TransitionComplete.class);
    assertEquals(time(5001, 500), complete.getEventTime());
    assertEquals(QuantizedSimTime.infinity(), complete.getNextInternalTime());
    actor.tell(SimulationTerminate.<QuantizedSimTime>builder().eventTime(time(10003, 1000))
        .simulationRunId("run").messageId("terminate").senderId("parent").receiverId("outer")
        .payload(SimulationTerminatePayload.builder().reason("completed").build()).build());
    parent.expectMessageClass(ModelTerminated.class);
    parent.expectTerminated(actor);
  }

  @Test
  void mappedNonzeroInitializationMakesExternalElapsedTimeRelativeToNativeStart() {
    TestProbe<TransitionRecord> records = kit.createTestProbe();
    TestProbe<DevsMessage> parent = kit.createTestProbe();
    CoupledModelFactory<QuantizedSimTime> factory = CoupledModelFactory.enhanced("coupled",
        List.of(target(records, true)), PDevsCouplings.builder("coupled")
            .addConnection("coupled", "in", "target", "in").build(),
        new CoordinatorExecutionConfig(clock));
    ActorRef<DevsMessage> actor = kit.spawn(factory.create(time(10, 1)));
    initialize(actor, parent, "coupled");
    assertEquals(QuantizedSimTime.infinity(), parent.expectMessageClass(NextInternalTimeReport.class).getNextInternalTime());
    actor.tell(execute("coupled", time(10001, 1000), List.of(value("input", "in"))));
    TransitionRecord record = records.receiveMessage();
    assertEquals("external", record.kind());
    assertEquals(1, record.elapsed());
    assertEquals(List.of("input"), record.values());
    parent.expectMessageClass(TransitionComplete.class);
    kit.stop(actor);
  }

  private record TransitionRecord(String kind, long elapsed, List<Object> values) {}

  @Test
  void nonterminatingParentInputIsQueuedThenConfluentAtRealSimulatorNativeTick() {
    TestProbe<TransitionRecord> records = kit.createTestProbe();
    TestProbe<DevsMessage> parent = kit.createTestProbe();
    CoupledModelFactory<QuantizedSimTime> factory = CoupledModelFactory.enhanced("coupled",
        List.of(target(records, false)), PDevsCouplings.builder("coupled")
            .addConnection("coupled", "in", "target", "in")
            .addConnection("target", "out", "coupled", "out").build(),
        new CoordinatorExecutionConfig(clock));
    ActorRef<DevsMessage> actor = kit.spawn(factory.create(time(10, 1)));
    try {
      initialize(actor, parent, "coupled");
      assertEquals(time(5001, 500), parent.expectMessageClass(NextInternalTimeReport.class).getNextInternalTime());
      QuantizedSimTime fractional = time(30004, 3000);
      actor.tell(execute("coupled", fractional, List.of(value("queued", "in"))));
      TransitionComplete<?> queued = parent.expectMessageClass(TransitionComplete.class);
      assertEquals(fractional, queued.getEventTime());
      assertEquals(time(5001, 500), queued.getNextInternalTime());
      records.expectNoMessage(java.time.Duration.ofMillis(50));
      actor.tell(RequestOutput.<QuantizedSimTime>builder().eventTime(time(5001, 500))
          .simulationRunId("run").messageId("output").senderId("parent").receiverId("coupled").build());
      OutputReport<?> output = parent.expectMessageClass(OutputReport.class);
      assertEquals(time(5001, 500), output.getEventTime());
      assertEquals(List.of(value("tick", "out")), output.getPayload().getOutputs());
      records.expectNoMessage(java.time.Duration.ofMillis(50));
      actor.tell(execute("coupled", time(5001, 500), List.of(value("current", "in"))));
      TransitionRecord record = records.receiveMessage();
      assertEquals("confluent", record.kind());
      assertEquals(List.of("queued", "current"), record.values());
      TransitionComplete<?> done = parent.expectMessageClass(TransitionComplete.class);
      assertEquals(time(5001, 500), done.getEventTime());
      assertEquals(QuantizedSimTime.infinity(), done.getNextInternalTime());
      records.expectNoMessage(java.time.Duration.ofMillis(50));
    } finally {
      kit.stop(actor);
    }
  }

  @Test
  void rootDiscardsQueuedDeliveryBeyondExactHorizon() {
    runRootDeliveryHorizon(time(40007, 4000), false);
  }

  @Test
  void rootExecutesDeliveryAtExactHorizonThenTerminatesAtInfinity() {
    runRootDeliveryHorizon(time(5001, 500), true);
  }

  private void runRootDeliveryHorizon(QuantizedSimTime horizon, boolean expectDelivery) {
    TestProbe<TransitionRecord> records = kit.createTestProbe();
    TestProbe<DevsMessage> lifecycle = kit.createTestProbe();
    ChildBinding<DoubleSimTime> source = ChildBinding.nativeProvider(provider(new Source(1.5)),
        DoubleSimTime.class, new TimeMapping<>(new DoubleTimeCodec(),
            TimeSemantics.of(Rational.of(1, 1000), Rational.of(10))));
    CoupledModelFactory<QuantizedSimTime> factory = CoupledModelFactory.enhanced("coupled",
        List.of(source, target(records, true)), PDevsCouplings.builder("coupled")
            .addConnection("source", "out", "target", "in").build(),
        new CoordinatorExecutionConfig(clock));
    ActorRef<DevsMessage> coordinator = kit.spawn(factory.create(time(10, 1)));
    ActorRef<DevsMessage> root = kit.spawn(RootCoordinator.enhanced(horizon, coordinator, "coupled", clock));
    root.tell(SimulationInit.<QuantizedSimTime>builder().eventTime(time(10, 1))
        .simulationRunId("run").messageId("init").senderId("test").receiverId("root").build());
    if (expectDelivery) {
      TransitionRecord record = records.receiveMessage();
      assertEquals("external", record.kind());
      assertEquals(2, record.elapsed());
      assertEquals(List.of("source"), record.values());
    }
    lifecycle.expectTerminated(root);
    lifecycle.expectTerminated(coordinator);
    records.expectNoMessage(java.time.Duration.ofMillis(50));
  }

  private class Target extends PDEVSModel<LongSimTime, Boolean> {
    private final ActorRef<TransitionRecord> records;
    Target(ActorRef<TransitionRecord> records, boolean passive) {
      super(passive, "target");
      this.records = records;
    }
    public void internalStateTransitionFunction() { modelState = true; }
    public void externalStateTransitionFunction(LongSimTime elapsed, List<PortValue<?>> inputs) {
      records.tell(new TransitionRecord("external", elapsed.getT(), inputs.stream().map(p -> (Object)p.getValue()).toList()));
      modelState = true;
    }
    public void confluentStateTransitionFunction(List<PortValue<?>> inputs) {
      records.tell(new TransitionRecord("confluent", 0, inputs.stream().map(p -> (Object)p.getValue()).toList()));
      modelState = true;
    }
    public LongSimTime timeAdvanceFunction() { return LongSimTime.create(modelState ? Long.MAX_VALUE : 2); }
    public List<PortValue<?>> outputFunction() { return List.of(value("tick", "out")); }
  }

  private class Source extends PDEVSModel<DoubleSimTime, Boolean> {
    private final double firstAdvance;
    Source() { this(2); }
    Source(double firstAdvance) {
      super(false, "source");
      this.firstAdvance = firstAdvance;
    }
    public void internalStateTransitionFunction() { modelState = true; }
    public void externalStateTransitionFunction(DoubleSimTime elapsed, List<PortValue<?>> inputs) { modelState = true; }
    public void confluentStateTransitionFunction(List<PortValue<?>> inputs) { modelState = true; }
    public DoubleSimTime timeAdvanceFunction() { return DoubleSimTime.create(modelState ? Double.MAX_VALUE : firstAdvance); }
    public List<PortValue<?>> outputFunction() { return List.of(value("source", "out")); }
  }
}
