package devs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devs.couplings.CouplingResolver;
import devs.couplings.CouplingTarget;
import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.OutputReportPayload;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.SimulationTerminatePayload;
import devs.iso.TransitionComplete;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.time.DecimalTimeCodec;
import devs.time.DoubleTimeCodec;
import devs.time.EnhancedClockContext;
import devs.time.LongTimeCodec;
import devs.time.Rational;
import devs.time.TimeGranularity;
import devs.time.TimeMapping;
import devs.time.TimeSemantics;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class PDevsCoordinatorTimeSemanticsTest {
  private static final ActorTestKit testKit = ActorTestKit.create();
  private static final String coordinatorName = "coupled";
  private static final String rootName = "root";
  private static final String simulationId = "time-semantics";
  private static final EnhancedClockContext clock =
      new EnhancedClockContext("shared-seconds", Rational.ZERO);
  private static final Duration quiet = Duration.ofMillis(100);
  private static final DoubleSimTime doubleInfinity = DoubleSimTime.create(Double.MAX_VALUE);
  private static final LongSimTime longInfinity = LongSimTime.create(Long.MAX_VALUE);
  private static final AtomicLong messageSequence = new AtomicLong();

  @AfterAll
  static void cleanup() {
    testKit.shutdownTestKit();
  }

  @Test
  void enhancedLifecycleAndBagsUseNaturalOrCustomOrderRegardlessOfArrivalOrder() {
    PDevsCoordinatorTest.assertOrderedLifecycle(true);
  }

  @Test
  void enhancedPartialImminenceInterleavesRecipientsWithInputFreeImminents() {
    PDevsCoordinatorTest.assertPartialImminence(true);
  }

  @Test
  void enhancedParentInputsUseChildOrderAndPreserveInputAndTargetOrder() {
    PDevsCoordinatorTest.assertParentInputOrder(true);
  }

  @Test
  void enhancedRejectsUnknownOutputAndParentInputRecipients() {
    PDevsCoordinatorTest.assertUnknownRecipients(true);
  }

  @Test
  void roundedDeliveryWaitsAcrossInterveningEventAndRunsWithOnlyInfinityCandidates() {
    try (ProbeFixture fixture = new ProbeFixture()) {
      fixture.initialize(DoubleSimTime.create(1.5), longInfinity);
      fixture.coordinator.tell(request(seconds(3, 2000)));
      assertEquals(DoubleSimTime.create(1.5), fixture.source
          .expectMessageClass(RequestOutput.class).getEventTime());
      fixture.coordinator.tell(output("source", DoubleSimTime.create(1.5),
          List.of(value("out", "queued"))));
      OutputReport<?> first = fixture.parent.expectMessageClass(OutputReport.class);
      assertEquals(seconds(3, 2000), first.getEventTime());
      assertEquals(List.of(value("external", "queued")), first.getPayload().getOutputs());
      fixture.source.expectNoMessage(quiet);
      fixture.target.expectNoMessage(quiet);

      fixture.coordinator.tell(execute(seconds(3, 2000), List.of()));
      ExecuteTransition<?> sourceTransition = fixture.source.expectMessageClass(ExecuteTransition.class);
      assertEquals(DoubleSimTime.create(1.5), sourceTransition.getEventTime());
      assertTrue(sourceTransition.getPayload().getInputs().isEmpty());
      fixture.target.expectNoMessage(quiet);
      fixture.parent.expectNoMessage(quiet);
      fixture.coordinator.tell(complete("source", DoubleSimTime.create(1.5),
          DoubleSimTime.create(1.75)));
      assertEquals(seconds(7, 4000), fixture.parent.expectMessageClass(TransitionComplete.class)
          .getNextInternalTime());

      fixture.coordinator.tell(request(seconds(7, 4000)));
      assertEquals(DoubleSimTime.create(1.75), fixture.source
          .expectMessageClass(RequestOutput.class).getEventTime());
      fixture.coordinator.tell(output("source", DoubleSimTime.create(1.75), List.of()));
      fixture.parent.expectMessageClass(OutputReport.class);
      fixture.target.expectNoMessage(quiet);
      fixture.coordinator.tell(execute(seconds(7, 4000), List.of()));
      assertEquals(DoubleSimTime.create(1.75), fixture.source
          .expectMessageClass(ExecuteTransition.class).getEventTime());
      fixture.coordinator.tell(complete("source", DoubleSimTime.create(1.75), doubleInfinity));
      assertEquals(seconds(1, 500), fixture.parent.expectMessageClass(TransitionComplete.class)
          .getNextInternalTime());

      fixture.coordinator.tell(request(seconds(1, 500)));
      OutputReport<?> deliveryOnly = fixture.parent.expectMessageClass(OutputReport.class);
      assertEquals(seconds(1, 500), deliveryOnly.getEventTime());
      assertTrue(deliveryOnly.getPayload().getOutputs().isEmpty());
      fixture.source.expectNoMessage(quiet);
      fixture.target.expectNoMessage(quiet);
      fixture.coordinator.tell(execute(seconds(1, 500), List.of()));
      ExecuteTransition<?> targetTransition = fixture.target.expectMessageClass(ExecuteTransition.class);
      assertEquals(LongSimTime.create(2), targetTransition.getEventTime());
      assertEquals(List.of(value("in", "queued")), targetTransition.getPayload().getInputs());
      fixture.source.expectNoMessage(quiet);
      fixture.parent.expectNoMessage(quiet);
      fixture.coordinator.tell(complete("target", LongSimTime.create(2), longInfinity));
      TransitionComplete<?> done = fixture.parent.expectMessageClass(TransitionComplete.class);
      assertEquals(seconds(1, 500), done.getEventTime());
      assertEquals(QuantizedSimTime.infinity(), done.getNextInternalTime());
      fixture.target.expectNoMessage(quiet);
    }
  }

  @Test
  void dueDeliveryCurrentOutputAndParentInputsMergeIntoOneConfluentDispatch() {
    try (ProbeFixture fixture = new ProbeFixture()) {
      fixture.initialize(DoubleSimTime.create(1.5), LongSimTime.create(2));
      fixture.sourceStep(1.5, List.of(value("out", "due")), DoubleSimTime.create(2));
      fixture.coordinator.tell(request(seconds(1, 500)));
      fixture.source.expectMessageClass(RequestOutput.class);
      assertEquals(LongSimTime.create(2), fixture.target.expectMessageClass(RequestOutput.class)
          .getEventTime());
      fixture.coordinator.tell(output("target", LongSimTime.create(2),
          List.of(value("out", "target-output"))));
      fixture.parent.expectNoMessage(quiet);
      fixture.coordinator.tell(output("source", DoubleSimTime.create(2),
          List.of(value("out", "current"))));
      OutputReport<?> report = fixture.parent.expectMessageClass(OutputReport.class);
      assertEquals(List.of(value("external", "current"), value("external", "target-output")),
          report.getPayload().getOutputs());
      fixture.source.expectNoMessage(quiet);
      fixture.target.expectNoMessage(quiet);

      fixture.coordinator.tell(execute(seconds(1, 500), List.of(value("input", "parent"))));
      fixture.source.expectMessageClass(ExecuteTransition.class);
      ExecuteTransition<?> targetTransition = fixture.target.expectMessageClass(ExecuteTransition.class);
      assertEquals(LongSimTime.create(2), targetTransition.getEventTime());
      assertEquals(List.of(value("in", "due"), value("in", "current"), value("in", "parent")),
          targetTransition.getPayload().getInputs());
      fixture.target.expectNoMessage(quiet);
      fixture.coordinator.tell(complete("source", DoubleSimTime.create(2), doubleInfinity));
      fixture.parent.expectNoMessage(quiet);
      fixture.source.expectNoMessage(quiet);
      fixture.coordinator.tell(complete("target", LongSimTime.create(2), longInfinity));
      assertEquals(QuantizedSimTime.infinity(), fixture.parent.expectMessageClass(TransitionComplete.class)
          .getNextInternalTime());
    }
  }

  @Test
  void outputBagsAndGlobalDispatchOrderDoNotDependOnChildResponseOrder() {
    List<List<PortValue<?>>> observed = new ArrayList<>();
    for (List<String> responseOrder : List.of(List.of("source", "other", "target"),
        List.of("target", "other", "source"))) {
      TestProbe<DevsMessage> children = testKit.createTestProbe();
      TestProbe<DevsMessage> parent = testKit.createTestProbe();
      List<String> childOrder = List.of("source", "other", "target");
      Map<String, ActorRef<DevsMessage>> actors = new LinkedHashMap<>();
      childOrder.forEach(id -> actors.put(id, children.getRef()));
      ActorRef<DevsMessage> coordinator = testKit.spawn(behavior(actors,
          StepTimingTracker.disabled()));
      try {
        coordinator.tell(init(parent.getRef(), clock));
        for (String id : childOrder) {
          SimulationInitMessage<?> message = children.expectMessageClass(SimulationInitMessage.class);
          assertEquals(id, message.getSimulationInit().getReceiverId());
          coordinator.tell(next(id, nativeTime(id, 0), nativeTime(id, 2)));
        }
        parent.expectMessageClass(NextInternalTimeReport.class);
        coordinator.tell(request(seconds(1, 500)));
        for (String id : childOrder) {
          assertEquals(id, children.expectMessageClass(RequestOutput.class).getReceiverId());
        }
        for (String id : responseOrder) {
          coordinator.tell(output(id, nativeTime(id, 2),
              List.of(value("out", id + "-first"), value("out", id + "-second"))));
        }
        List<PortValue<?>> bag = parent.expectMessageClass(OutputReport.class).getPayload().getOutputs();
        assertEquals(List.of(value("external", "source-first"), value("external", "source-second"),
            value("external", "other-first"), value("external", "other-second"),
            value("external", "target-first"), value("external", "target-second")), bag);
        observed.add(bag);
        children.expectNoMessage(quiet);
        coordinator.tell(execute(seconds(1, 500), List.of(value("input", "parent"))));
        for (String id : childOrder) {
          ExecuteTransition<?> transition = children.expectMessageClass(ExecuteTransition.class);
          assertEquals(id, transition.getReceiverId());
          assertEquals(nativeTime(id, 2), transition.getEventTime());
          assertEquals(id.equals("target") ? List.of(value("in", "source-first"),
              value("in", "source-second"), value("in", "other-first"),
              value("in", "other-second"), value("in", "parent")) : List.of(),
              transition.getPayload().getInputs());
        }
        children.expectNoMessage(quiet);
        for (String id : responseOrder) {
          coordinator.tell(complete(id, nativeTime(id, 2), nativeInfinity(id)));
        }
        assertEquals(QuantizedSimTime.infinity(), parent.expectMessageClass(TransitionComplete.class)
            .getNextInternalTime());
      } finally {
        testKit.stop(coordinator);
      }
    }
    assertEquals(observed.get(0), observed.get(1));
  }

  @Test
  void externalOnlyTransitionsAndRepeatedZeroAdvanceUseNativeTimes() {
    try (ProbeFixture fixture = new ProbeFixture()) {
      fixture.initialize(doubleInfinity, longInfinity);
      fixture.coordinator.tell(execute(seconds(1, 1000), List.of(value("input", "external-only"))));
      ExecuteTransition<?> external = fixture.target.expectMessageClass(ExecuteTransition.class);
      assertEquals(LongSimTime.create(1), external.getEventTime());
      assertEquals(List.of(value("in", "external-only")), external.getPayload().getInputs());
      fixture.source.expectNoMessage(quiet);
      fixture.parent.expectNoMessage(quiet);
      fixture.coordinator.tell(complete("target", LongSimTime.create(1), LongSimTime.create(1)));
      assertEquals(seconds(1, 1000), fixture.parent.expectMessageClass(TransitionComplete.class)
          .getNextInternalTime());

      for (int step = 0; step < 2; step++) {
        fixture.coordinator.tell(request(seconds(1, 1000)));
        assertEquals(LongSimTime.create(1), fixture.target.expectMessageClass(RequestOutput.class)
            .getEventTime());
        fixture.coordinator.tell(output("target", LongSimTime.create(1), List.of()));
        fixture.parent.expectMessageClass(OutputReport.class);
        fixture.target.expectNoMessage(quiet);
        fixture.coordinator.tell(execute(seconds(1, 1000), List.of()));
        assertTrue(fixture.target.expectMessageClass(ExecuteTransition.class).getPayload().getInputs().isEmpty());
        fixture.coordinator.tell(complete("target", LongSimTime.create(1),
            step == 0 ? LongSimTime.create(1) : longInfinity));
        assertEquals(step == 0 ? seconds(1, 1000) : QuantizedSimTime.infinity(),
            fixture.parent.expectMessageClass(TransitionComplete.class).getNextInternalTime());
      }
    }
  }

  @Test
  void terminationUsesLastNativeChildTimeRatherThanOffLatticeGlobalTime() {
    try (ProbeFixture fixture = new ProbeFixture()) {
      fixture.initialize(DoubleSimTime.create(1.75), longInfinity);
      fixture.sourceStep(1.75, List.of(), doubleInfinity);
      fixture.coordinator.tell(SimulationTerminate.<QuantizedSimTime>builder()
          .eventTime(seconds(7, 4000)).simulationRunId(simulationId).messageId(id())
          .senderId(rootName).receiverId(coordinatorName)
          .payload(SimulationTerminatePayload.builder().reason("done").build()).build());
      assertEquals(DoubleSimTime.create(1.75), fixture.source.expectMessageClass(SimulationTerminate.class)
          .getEventTime());
      assertEquals(LongSimTime.create(0), fixture.target.expectMessageClass(SimulationTerminate.class)
          .getEventTime());
      fixture.coordinator.tell(terminated("target"));
      fixture.parent.expectNoMessage(quiet);
      fixture.coordinator.tell(terminated("source"));
      assertEquals(coordinatorName, fixture.parent.expectMessageClass(ModelTerminated.class).getSenderId());
      fixture.parent.expectTerminated(fixture.coordinator);
    }
  }

  @Test
  void parentOutputRetainsCanonicalFractionalTimeWhileNativeDeliveryIsAdapted() {
    SyncFixture fixture = new SyncFixture();
    fixture.initialize(DoubleSimTime.create(1.5), longInfinity);
    fixture.kit.run(request(seconds(3, 2000)));
    assertEquals(DoubleSimTime.create(1.5), assertInstanceOf(RequestOutput.class,
        fixture.source.receiveMessage()).getEventTime());
    fixture.kit.run(output("source", DoubleSimTime.create(1.5), List.of(value("out", "value"))));
    OutputReport<?> report = assertInstanceOf(OutputReport.class, fixture.parent.receiveMessage());
    assertEquals(seconds(3, 2000), report.getEventTime());
    assertEquals(List.of(value("external", "value")), report.getPayload().getOutputs());
    fixture.kit.run(execute(seconds(3, 2000), List.of()));
    ExecuteTransition<?> transition = assertInstanceOf(ExecuteTransition.class, fixture.source.receiveMessage());
    assertEquals(DoubleSimTime.create(1.5), transition.getEventTime());
    assertFalse(fixture.target.hasMessages());
    fixture.kit.run(complete("source", DoubleSimTime.create(1.5), doubleInfinity));
    assertEquals(seconds(1, 500), assertInstanceOf(TransitionComplete.class, fixture.parent.receiveMessage())
        .getNextInternalTime());
    fixture.kit.run(request(seconds(1, 500)));
    fixture.parent.receiveMessage();
    fixture.kit.run(execute(seconds(1, 500), List.of()));
    ExecuteTransition<?> delivery = assertInstanceOf(ExecuteTransition.class, fixture.target.receiveMessage());
    assertEquals(LongSimTime.create(2), delivery.getEventTime());
    assertEquals(List.of(value("in", "value")), delivery.getPayload().getInputs());
    fixture.kit.run(complete("target", LongSimTime.create(2), longInfinity));
    assertEquals(QuantizedSimTime.infinity(), assertInstanceOf(TransitionComplete.class,
        fixture.parent.receiveMessage()).getNextInternalTime());
  }

  @Test
  void dynamicResolverAutomaticallyAdaptsOutputAndParentInputWithoutRouteRegistration() {
    CouplingResolver resolver = new CouplingResolver() {
      @Override
      public List<CouplingTarget> resolve(String sender, PortValue<?> portValue) {
        CouplingTarget target = CouplingTarget.of("target", value("resolved", sender + ":" + portValue.getValue()));
        return sender.equals("source")
            ? List.of(target, CouplingTarget.of(coordinatorName, value("external", portValue.getValue())))
            : List.of(target);
      }
    };
    SyncFixture fixture = new SyncFixture(PDevsCouplings.builder(coordinatorName)
        .addResolver("source", "out", resolver)
        .addResolver(coordinatorName, "input", resolver).build());
    fixture.initialize(DoubleSimTime.create(1.5), longInfinity);
    fixture.kit.run(request(seconds(3, 2000)));
    assertInstanceOf(RequestOutput.class, fixture.source.receiveMessage());
    fixture.kit.run(output("source", DoubleSimTime.create(1.5), List.of(value("out", "child"))));
    OutputReport<?> output = assertInstanceOf(OutputReport.class, fixture.parent.receiveMessage());
    assertEquals(seconds(3, 2000), output.getEventTime());
    assertEquals(List.of(value("external", "child")), output.getPayload().getOutputs());
    fixture.kit.run(execute(seconds(3, 2000), List.of(value("input", "parent"))));
    assertEquals(DoubleSimTime.create(1.5), assertInstanceOf(ExecuteTransition.class,
        fixture.source.receiveMessage()).getEventTime());
    fixture.kit.run(complete("source", DoubleSimTime.create(1.5), doubleInfinity));
    assertEquals(seconds(1, 500), assertInstanceOf(TransitionComplete.class, fixture.parent.receiveMessage())
        .getNextInternalTime());
    assertFalse(fixture.target.hasMessages());
    fixture.kit.run(request(seconds(1, 500)));
    assertTrue(assertInstanceOf(OutputReport.class, fixture.parent.receiveMessage()).getPayload().getOutputs().isEmpty());
    fixture.kit.run(execute(seconds(1, 500), List.of(value("input", "same-time"))));
    ExecuteTransition<?> delivery = assertInstanceOf(ExecuteTransition.class, fixture.target.receiveMessage());
    assertEquals(LongSimTime.create(2), delivery.getEventTime());
    assertEquals(List.of(value("resolved", "source:child"), value("resolved", "coupled:parent"),
        value("resolved", "coupled:same-time")), delivery.getPayload().getInputs());
    assertFalse(fixture.target.hasMessages());
    fixture.kit.run(complete("target", LongSimTime.create(2), longInfinity));
    assertEquals(QuantizedSimTime.infinity(), assertInstanceOf(TransitionComplete.class,
        fixture.parent.receiveMessage()).getNextInternalTime());
  }

  @Test
  void nonterminatingParentTimesCoalesceInArrivalOrderAtNextIntegralTick() {
    GridFixture<LongSimTime> fixture = new GridFixture<>(LongSimTime.class,
        new TimeMapping<>(new LongTimeCodec(), TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO)));
    fixture.initialize(LongSimTime.create(0), longInfinity);
    for (int numerator : List.of(1, 2)) {
      QuantizedSimTime at = seconds(numerator, 3000);
      fixture.kit.run(execute(at, List.of(value("input", "queued-" + numerator))));
      TransitionComplete<?> queued = assertInstanceOf(TransitionComplete.class, fixture.parent.receiveMessage());
      assertEquals(at, queued.getEventTime());
      assertEquals(seconds(1, 1000), queued.getNextInternalTime());
      assertFalse(fixture.target.hasMessages());
    }
    fixture.kit.run(request(seconds(1, 1000)));
    assertTrue(assertInstanceOf(OutputReport.class, fixture.parent.receiveMessage()).getPayload().getOutputs().isEmpty());
    fixture.kit.run(execute(seconds(1, 1000), List.of(value("input", "current"))));
    ExecuteTransition<?> delivery = assertInstanceOf(ExecuteTransition.class, fixture.target.receiveMessage());
    assertEquals(LongSimTime.create(1), delivery.getEventTime());
    assertEquals(List.of(value("in", "queued-1"), value("in", "queued-2"), value("in", "current")),
        delivery.getPayload().getInputs());
    assertFalse(fixture.target.hasMessages());
    fixture.kit.run(complete("target", LongSimTime.create(1), longInfinity));
    assertEquals(QuantizedSimTime.infinity(), assertInstanceOf(TransitionComplete.class,
        fixture.parent.receiveMessage()).getNextInternalTime());
  }

  @Test
  void nonterminatingParentTimeUsesDoubleCeilingWithoutSupplierGrid() {
    verifyUngridddedParentAdaptation(DoubleSimTime.class,
        new TimeMapping<>(new DoubleTimeCodec(), TimeSemantics.of(Rational.ONE, Rational.ZERO)),
        DoubleSimTime.create(0), DoubleSimTime.create(Math.nextUp(1.0 / 3.0)), doubleInfinity);
  }

  @Test
  void nonterminatingParentTimeUsesDecimalCeilingWithoutSupplierGrid() {
    verifyUngridddedParentAdaptation(DecimalSimTime.class,
        new TimeMapping<>(new DecimalTimeCodec(), TimeSemantics.of(Rational.ONE, Rational.ZERO)),
        DecimalSimTime.of(BigDecimal.ZERO), DecimalSimTime.of(new BigDecimal("0.333333333333333334")),
        DecimalSimTime.infinity());
  }

  private static <N extends SimTime> void verifyUngridddedParentAdaptation(Class<N> nativeType,
      TimeMapping<N> mapping, N zero, N expectedNative, N infinity) {
    GridFixture<N> fixture = new GridFixture<>(nativeType, mapping);
    fixture.initialize(zero, infinity);
    QuantizedSimTime sourceTime = seconds(1, 3);
    QuantizedSimTime deliveryTime = mapping.toCanonical(expectedNative);
    assertTrue(deliveryTime.compareTo(sourceTime) > 0);
    fixture.kit.run(execute(sourceTime, List.of(value("input", "nonterminating"))));
    TransitionComplete<?> queued = assertInstanceOf(TransitionComplete.class, fixture.parent.receiveMessage());
    assertEquals(sourceTime, queued.getEventTime());
    assertEquals(deliveryTime, queued.getNextInternalTime());
    assertFalse(fixture.target.hasMessages());
    fixture.kit.run(request(deliveryTime));
    OutputReport<?> output = assertInstanceOf(OutputReport.class, fixture.parent.receiveMessage());
    assertEquals(deliveryTime, output.getEventTime());
    assertTrue(output.getPayload().getOutputs().isEmpty());
    assertFalse(fixture.target.hasMessages());
    fixture.kit.run(execute(deliveryTime, List.of(value("input", "exact-current"))));
    ExecuteTransition<?> transition = assertInstanceOf(ExecuteTransition.class, fixture.target.receiveMessage());
    assertEquals(expectedNative, transition.getEventTime());
    assertEquals(List.of(value("in", "nonterminating"), value("in", "exact-current")),
        transition.getPayload().getInputs());
    assertFalse(fixture.target.hasMessages());
    assertFalse(fixture.parent.hasMessages());
    fixture.kit.run(complete("target", expectedNative, infinity));
    TransitionComplete<?> done = assertInstanceOf(TransitionComplete.class, fixture.parent.receiveMessage());
    assertEquals(deliveryTime, done.getEventTime());
    assertEquals(QuantizedSimTime.infinity(), done.getNextInternalTime());
  }

  @Test
  void doubleWholeNativeMillisecondGridRequiresExactInitializationAndAdaptsDelivery() {
    verifySupplierGrid(DoubleSimTime.class, new TimeMapping<>(new DoubleTimeCodec(),
            TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO,
                Optional.of(TimeGranularity.of(Rational.ONE)))),
        DoubleSimTime.create(0), DoubleSimTime.create(1.5), DoubleSimTime.create(2), doubleInfinity);
  }

  @Test
  void decimalSecondsMillisecondGridRequiresExactInitializationAndAdaptsDelivery() {
    verifySupplierGrid(DecimalSimTime.class, new TimeMapping<>(new DecimalTimeCodec(),
            TimeSemantics.of(Rational.ONE, Rational.ZERO,
                Optional.of(TimeGranularity.of(Rational.of(1, 1000))))),
        DecimalSimTime.of(BigDecimal.ZERO), DecimalSimTime.of(new BigDecimal("0.0015")),
        DecimalSimTime.of(new BigDecimal("0.002")), DecimalSimTime.infinity());
  }

  private static <N extends SimTime> void verifySupplierGrid(Class<N> nativeType,
      TimeMapping<N> mapping, N zero, N offGrid, N deliveryTime, N infinity) {
    TimeMapping<N> unrestricted = new TimeMapping<>(mapping.codec(),
        TimeSemantics.of(mapping.semantics().secondsPerUnit(), Rational.ZERO));
    assertEquals(seconds(3, 2000), unrestricted.toCanonical(offGrid));

    GridFixture<N> initialization = new GridFixture<>(nativeType, mapping);
    SimulationInitMessage<QuantizedSimTime> offGridInit = new SimulationInitMessage<>(
        SimulationInit.<QuantizedSimTime>builder().eventTime(seconds(1, 2000))
            .simulationRunId(simulationId).messageId(id()).senderId(rootName)
            .receiverId(coordinatorName).build(), initialization.parent.getRef(), clock);
    assertThrows(ArithmeticException.class, () -> initialization.kit.run(offGridInit));
    assertFalse(initialization.target.hasMessages());
    assertFalse(initialization.parent.hasMessages());

    GridFixture<N> candidate = new GridFixture<>(nativeType, mapping);
    assertThrows(ArithmeticException.class, () -> candidate.initialize(zero, offGrid));
    assertFalse(candidate.parent.hasMessages());

    GridFixture<N> adaptive = new GridFixture<>(nativeType, mapping);
    adaptive.initialize(zero, infinity);
    adaptive.kit.run(execute(seconds(3, 2000), List.of(value("input", "queued"))));
    TransitionComplete<?> queued = assertInstanceOf(TransitionComplete.class, adaptive.parent.receiveMessage());
    assertEquals(seconds(3, 2000), queued.getEventTime());
    assertEquals(seconds(1, 500), queued.getNextInternalTime());
    assertFalse(adaptive.target.hasMessages());

    adaptive.kit.run(request(seconds(1, 500)));
    OutputReport<?> report = assertInstanceOf(OutputReport.class, adaptive.parent.receiveMessage());
    assertEquals(seconds(1, 500), report.getEventTime());
    assertTrue(report.getPayload().getOutputs().isEmpty());
    assertFalse(adaptive.target.hasMessages());
    adaptive.kit.run(execute(seconds(1, 500), List.of()));
    ExecuteTransition<?> transition = assertInstanceOf(ExecuteTransition.class, adaptive.target.receiveMessage());
    assertEquals(deliveryTime, transition.getEventTime());
    assertEquals(List.of(value("in", "queued")), transition.getPayload().getInputs());
    assertFalse(adaptive.target.hasMessages());
    assertFalse(adaptive.parent.hasMessages());
    adaptive.kit.run(complete("target", deliveryTime, infinity));
    TransitionComplete<?> done = assertInstanceOf(TransitionComplete.class, adaptive.parent.receiveMessage());
    assertEquals(seconds(1, 500), done.getEventTime());
    assertEquals(QuantizedSimTime.infinity(), done.getNextInternalTime());
    assertFalse(adaptive.target.hasMessages());
  }

  @Test
  void unexpectedDuplicateStaleAndInconsistentOutputReportsAreRejected() {
    for (String fault : List.of("unexpected", "duplicate", "stale-run", "wrong-receiver",
        "wrong-event", "wrong-next", "wrong-native")) {
      SyncFixture fixture = new SyncFixture();
      fixture.initialize(DoubleSimTime.create(2), LongSimTime.create(2));
      fixture.kit.run(request(seconds(1, 500)));
      OutputReport<SimTime> valid = output("source", DoubleSimTime.create(2), List.of());
      if (fault.equals("duplicate")) {
        fixture.kit.run(valid);
      }
      OutputReport<SimTime> invalid = switch (fault) {
        case "unexpected" -> output("stranger", DoubleSimTime.create(2), List.of());
        case "duplicate" -> valid;
        case "wrong-event" -> output("source", DoubleSimTime.create(1.5), List.of());
        case "wrong-next" -> outputWithContext("source", DoubleSimTime.create(2), DoubleSimTime.create(3),
            simulationId, coordinatorName);
        case "wrong-native" -> output("source", LongSimTime.create(2), List.of());
        default -> outputWithContext("source", DoubleSimTime.create(2), DoubleSimTime.create(2),
            fault.equals("stale-run") ? "old-run" : simulationId,
            fault.equals("wrong-receiver") ? "another-coordinator" : coordinatorName);
      };
      if (fault.equals("wrong-native")) {
        assertThrows(ClassCastException.class, () -> fixture.kit.run(invalid), fault);
      } else {
        assertThrows(IllegalArgumentException.class, () -> fixture.kit.run(invalid), fault);
      }
      assertFalse(fixture.parent.hasMessages(), fault);
    }
  }

  @Test
  void initializationRejectsDuplicateWrongTimestampAndNativeRollbackReports() {
    for (String fault : List.of("duplicate", "wrong-event", "rollback", "wrong-native")) {
      SyncFixture fixture = new SyncFixture();
      fixture.kit.run(init(fixture.parent.getRef(), clock));
      NextInternalTimeReport<SimTime> first = next("source", DoubleSimTime.create(0), DoubleSimTime.create(2));
      if (fault.equals("duplicate")) {
        fixture.kit.run(first);
      }
      NextInternalTimeReport<SimTime> invalid = switch (fault) {
        case "duplicate" -> next("source", DoubleSimTime.create(0), DoubleSimTime.create(2));
        case "wrong-event" -> next("source", DoubleSimTime.create(1), DoubleSimTime.create(2));
        case "wrong-native" -> next("source", LongSimTime.create(0), LongSimTime.create(2));
        default -> next("source", DoubleSimTime.create(0), DoubleSimTime.create(-1));
      };
      if (fault.equals("wrong-native")) {
        assertThrows(ClassCastException.class, () -> fixture.kit.run(invalid), fault);
      } else {
        assertThrows(IllegalArgumentException.class, () -> fixture.kit.run(invalid), fault);
      }
      assertFalse(fixture.parent.hasMessages());
    }
  }

  @Test
  void completionRejectsUnexpectedDuplicateStaleTimestampAndNativeRollback() {
    for (String fault : List.of("unexpected", "duplicate", "wrong-event", "rollback", "wrong-native",
        "stale-run", "wrong-receiver")) {
      SyncFixture fixture = new SyncFixture();
      fixture.initialize(DoubleSimTime.create(2), LongSimTime.create(2));
      fixture.kit.run(request(seconds(1, 500)));
      fixture.kit.run(output("source", DoubleSimTime.create(2), List.of()));
      fixture.kit.run(output("target", LongSimTime.create(2), List.of()));
      fixture.parent.receiveMessage();
      if (!fault.equals("unexpected")) {
        fixture.kit.run(execute(seconds(1, 500), List.of()));
      }
      TransitionComplete<SimTime> valid = complete("source", DoubleSimTime.create(2), doubleInfinity);
      if (fault.equals("duplicate")) {
        fixture.kit.run(valid);
      }
      TransitionComplete<SimTime> invalid = switch (fault) {
        case "wrong-event" -> complete("source", DoubleSimTime.create(1.5), doubleInfinity);
        case "rollback" -> complete("source", DoubleSimTime.create(2), DoubleSimTime.create(1.75));
        case "wrong-native" -> complete("source", LongSimTime.create(2), longInfinity);
        case "stale-run", "wrong-receiver" -> TransitionComplete.<SimTime>builder()
            .eventTime(DoubleSimTime.create(2))
            .simulationRunId(fault.equals("stale-run") ? "old-run" : simulationId)
            .messageId(id()).senderId("source")
            .receiverId(fault.equals("wrong-receiver") ? "another-coordinator" : coordinatorName)
            .nextInternalTime(doubleInfinity).build();
        default -> valid;
      };
      assertThrows(IllegalArgumentException.class, () -> fixture.kit.run(invalid), fault);
      assertFalse(fixture.parent.hasMessages(), fault);
    }
  }

  @Test
  void completedStepReportCannotBeReplayedDuringTheNextZeroAdvanceStep() {
    SyncFixture fixture = new SyncFixture();
    fixture.initialize(DoubleSimTime.create(2), longInfinity);
    fixture.kit.run(request(seconds(1, 500)));
    OutputReport<SimTime> oldOutput = output("source", DoubleSimTime.create(2), List.of());
    fixture.kit.run(oldOutput);
    fixture.parent.receiveMessage();
    fixture.kit.run(execute(seconds(1, 500), List.of()));
    fixture.kit.run(complete("source", DoubleSimTime.create(2), DoubleSimTime.create(2)));
    fixture.parent.receiveMessage();
    fixture.kit.run(request(seconds(1, 500)));
    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> fixture.kit.run(oldOutput));
    assertTrue(failure.getMessage().contains("stale, or duplicate"));
    assertFalse(fixture.parent.hasMessages());
  }

  @Test
  void parentCannotTransitionBeforeOutputsOrAdvanceWhileCompletionIsDelayed() {
    SyncFixture collecting = new SyncFixture();
    collecting.initialize(DoubleSimTime.create(2), LongSimTime.create(2));
    collecting.kit.run(request(seconds(1, 500)));
    collecting.kit.run(output("source", DoubleSimTime.create(2), List.of()));
    assertThrows(IllegalStateException.class,
        () -> collecting.kit.run(execute(seconds(1, 500), List.of())));

    SyncFixture transitioning = new SyncFixture();
    transitioning.initialize(DoubleSimTime.create(2), LongSimTime.create(2));
    transitioning.kit.run(request(seconds(1, 500)));
    transitioning.kit.run(output("source", DoubleSimTime.create(2), List.of()));
    transitioning.kit.run(output("target", LongSimTime.create(2), List.of()));
    transitioning.parent.receiveMessage();
    transitioning.kit.run(execute(seconds(1, 500), List.of()));
    transitioning.kit.run(complete("source", DoubleSimTime.create(2), DoubleSimTime.create(3)));
    assertFalse(transitioning.parent.hasMessages());
    assertThrows(IllegalStateException.class, () -> transitioning.kit.run(request(seconds(3, 1000))));
  }

  @Test
  void parentNativeTimestampAndExternalRollbackAreRejected() {
    SyncFixture nativeParent = new SyncFixture();
    nativeParent.initialize(DoubleSimTime.create(2), longInfinity);
    assertThrows(IllegalArgumentException.class, () -> nativeParent.kit.run(
        RequestOutput.<LongSimTime>builder().eventTime(LongSimTime.create(2))
            .simulationRunId(simulationId).messageId(id()).senderId(rootName)
            .receiverId(coordinatorName).build()));

    SyncFixture rollback = new SyncFixture();
    rollback.initialize(doubleInfinity, longInfinity);
    rollback.kit.run(execute(seconds(1, 1000), List.of(value("input", "first"))));
    rollback.kit.run(complete("target", LongSimTime.create(1), longInfinity));
    rollback.parent.receiveMessage();
    assertThrows(IllegalStateException.class, () -> rollback.kit.run(
        execute(seconds(1, 2000), List.of(value("input", "past")))));
  }

  @Test
  void enhancedAndLegacyInitializationContextsCannotBeMixed() {
    for (EnhancedClockContext supplied : new EnhancedClockContext[] {null,
        new EnhancedClockContext("different", Rational.ZERO),
        new EnhancedClockContext(clock.clockId(), Rational.ONE)}) {
      SyncFixture fixture = new SyncFixture();
      assertThrows(IllegalArgumentException.class,
          () -> fixture.kit.run(init(fixture.parent.getRef(), supplied)));
    }
    TestInbox<DevsMessage> child = TestInbox.create();
    TestInbox<DevsMessage> parent = TestInbox.create();
    BehaviorTestKit<DevsMessage> legacy = BehaviorTestKit.create(PDevsCoordinator.create(coordinatorName,
        Map.of("source", child.getRef()), PDevsCouplings.builder(coordinatorName).build()));
    assertThrows(IllegalArgumentException.class, () -> legacy.run(init(parent.getRef(), clock)));
  }

  @Test
  void trackerRecordsEnhancedPhasesOnlyAfterTheirCorrespondingResponses() {
    AtomicLong nanos = new AtomicLong();
    StepTimingTracker tracker = StepTimingTracker.enabled(() -> nanos.addAndGet(10));
    SyncFixture fixture = new SyncFixture(tracker);
    fixture.initialize(DoubleSimTime.create(1.5), longInfinity);
    fixture.kit.run(request(seconds(3, 2000)));
    fixture.kit.run(output("source", DoubleSimTime.create(1.5), List.of(value("out", "queued"))));
    fixture.parent.receiveMessage();
    assertEquals(1, tracker.getAggregateStats(StepTimingTracker.AggregateCategory.OUTPUT_ROUND_TRIP).getCount());
    assertEquals(0, tracker.getAggregateStats(StepTimingTracker.AggregateCategory.TOTAL_STEP).getCount());
    fixture.kit.run(execute(seconds(3, 2000), List.of()));
    assertEquals(0, tracker.getAggregateStats(StepTimingTracker.AggregateCategory.TRANSITION_ROUND_TRIP).getCount());
    fixture.kit.run(complete("source", DoubleSimTime.create(1.5), doubleInfinity));
    assertEquals(seconds(1, 500), assertInstanceOf(TransitionComplete.class, fixture.parent.receiveMessage())
        .getNextInternalTime());
    fixture.kit.run(request(seconds(1, 500)));
    fixture.parent.receiveMessage();
    fixture.kit.run(execute(seconds(1, 500), List.of()));
    fixture.kit.run(complete("target", LongSimTime.create(2), longInfinity));
    fixture.parent.receiveMessage();
    for (StepTimingTracker.AggregateCategory category : StepTimingTracker.AggregateCategory.values()) {
      assertEquals(2, tracker.getAggregateStats(category).getCount(), category.name());
      assertTrue(tracker.getAggregateStats(category).getTotalNanos() > 0, category.name());
    }
    assertEquals(1, tracker.getModelStats(StepTimingTracker.ModelCategory.OUTPUT_ROUND_TRIP, "source").getCount());
    assertEquals(0, tracker.getModelStats(StepTimingTracker.ModelCategory.OUTPUT_ROUND_TRIP, "target").getCount());
    assertEquals(1, tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP, "source").getCount());
    assertEquals(1, tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP, "target").getCount());
  }

  private static final class ProbeFixture implements AutoCloseable {
    final TestProbe<DevsMessage> source = testKit.createTestProbe();
    final TestProbe<DevsMessage> target = testKit.createTestProbe();
    final TestProbe<DevsMessage> parent = testKit.createTestProbe();
    final ActorRef<DevsMessage> coordinator = testKit.spawn(behavior(
        Map.of("source", source.getRef(), "target", target.getRef()),
        StepTimingTracker.disabled()));

    void initialize(SimTime sourceNext, SimTime targetNext) {
      coordinator.tell(init(parent.getRef(), clock));
      SimulationInitMessage<?> sourceInit = source.expectMessageClass(SimulationInitMessage.class);
      SimulationInitMessage<?> targetInit = target.expectMessageClass(SimulationInitMessage.class);
      assertEquals(DoubleSimTime.create(0), sourceInit.getSimulationInit().getEventTime());
      assertEquals(LongSimTime.create(0), targetInit.getSimulationInit().getEventTime());
      assertTrue(sourceInit.getEnhancedClock().isEmpty());
      assertTrue(targetInit.getEnhancedClock().isEmpty());
      coordinator.tell(next("target", LongSimTime.create(0), targetNext));
      coordinator.tell(next("source", DoubleSimTime.create(0), sourceNext));
      NextInternalTimeReport<?> report = parent.expectMessageClass(NextInternalTimeReport.class);
      assertEquals(QuantizedSimTime.of(Rational.ZERO), report.getEventTime());
      assertEquals(coordinatorName, report.getSenderId());
    }

    void sourceStep(double millis, List<PortValue<?>> outputs, SimTime sourceNext) {
      QuantizedSimTime time = doubleMapping().toCanonical(DoubleSimTime.create(millis));
      coordinator.tell(request(time));
      source.expectMessageClass(RequestOutput.class);
      coordinator.tell(output("source", DoubleSimTime.create(millis), outputs));
      parent.expectMessageClass(OutputReport.class);
      coordinator.tell(execute(time, List.of()));
      source.expectMessageClass(ExecuteTransition.class);
      coordinator.tell(complete("source", DoubleSimTime.create(millis), sourceNext));
      parent.expectMessageClass(TransitionComplete.class);
    }

    @Override
    public void close() {
      testKit.stop(coordinator);
    }
  }

  private static final class SyncFixture {
    final TestInbox<DevsMessage> source = TestInbox.create();
    final TestInbox<DevsMessage> target = TestInbox.create();
    final TestInbox<DevsMessage> parent = TestInbox.create();
    final BehaviorTestKit<DevsMessage> kit;

    SyncFixture() {
      this(StepTimingTracker.disabled());
    }

    SyncFixture(StepTimingTracker tracker) {
      kit = BehaviorTestKit.create(behavior(Map.of("source", source.getRef(), "target", target.getRef()),
          tracker));
    }

    SyncFixture(PDevsCouplings routes) {
      kit = BehaviorTestKit.create(behavior(Map.of("source", source.getRef(), "target", target.getRef()),
          StepTimingTracker.disabled(), routes));
    }

    void initialize(SimTime sourceNext, SimTime targetNext) {
      kit.run(init(parent.getRef(), clock));
      assertEquals(DoubleSimTime.create(0), assertInstanceOf(SimulationInitMessage.class,
          source.receiveMessage()).getSimulationInit().getEventTime());
      assertEquals(LongSimTime.create(0), assertInstanceOf(SimulationInitMessage.class,
          target.receiveMessage()).getSimulationInit().getEventTime());
      kit.run(next("source", DoubleSimTime.create(0), sourceNext));
      kit.run(next("target", LongSimTime.create(0), targetNext));
      assertInstanceOf(NextInternalTimeReport.class, parent.receiveMessage());
    }
  }

  private static final class GridFixture<N extends SimTime> {
    final TestInbox<DevsMessage> target = TestInbox.create();
    final TestInbox<DevsMessage> parent = TestInbox.create();
    final BehaviorTestKit<DevsMessage> kit;

    GridFixture(Class<N> nativeType, TimeMapping<N> mapping) {
      Map<String, ActorRef<DevsMessage>> actors = Map.of("target", target.getRef());
      Map<String, ChildBinding<?>> bindings = Map.of("target",
          ChildBinding.nativeActor("target", target.getRef(), nativeType, mapping));
      CoordinatorExecutionConfig config = new CoordinatorExecutionConfig(clock);
      kit = BehaviorTestKit.create(Behaviors.setup(context -> new PDevsCoordinator<QuantizedSimTime>(
          coordinatorName, actors, PDevsCouplings.builder(coordinatorName)
              .addConnection(coordinatorName, "input", "target", "in").build(),
          context, StepTimingTracker.disabled(), config, bindings)));
    }

    void initialize(N zero, N candidate) {
      kit.run(init(parent.getRef(), clock));
      SimulationInitMessage<?> message = assertInstanceOf(SimulationInitMessage.class, target.receiveMessage());
      assertEquals(zero, message.getSimulationInit().getEventTime());
      assertTrue(message.getEnhancedClock().isEmpty());
      kit.run(next("target", zero, candidate));
      NextInternalTimeReport<?> report = assertInstanceOf(NextInternalTimeReport.class, parent.receiveMessage());
      assertEquals(QuantizedSimTime.of(Rational.ZERO), report.getEventTime());
      assertEquals(QuantizedSimTime.infinity(), report.getNextInternalTime());
    }
  }

  private static Behavior<DevsMessage> behavior(Map<String, ActorRef<DevsMessage>> actors,
      StepTimingTracker tracker) {
    return behavior(actors, tracker, couplings(actors.containsKey("other")
        ? List.of("source", "other", "target") : List.of("source", "target")));
  }

  private static Behavior<DevsMessage> behavior(Map<String, ActorRef<DevsMessage>> actors,
      StepTimingTracker tracker, PDevsCouplings routes) {
    List<String> childOrder = actors.containsKey("other") ? List.of("source", "other", "target")
        : List.of("source", "target");
    Map<String, ChildBinding<?>> bindings = new LinkedHashMap<>();
    for (String child : childOrder) {
      bindings.put(child, child.equals("target")
          ? ChildBinding.nativeActor(child, actors.get(child), LongSimTime.class,
              new TimeMapping<>(new LongTimeCodec(),
                  TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO)))
          : ChildBinding.nativeActor(child, actors.get(child), DoubleSimTime.class, doubleMapping()));
    }
    CoordinatorExecutionConfig config = new CoordinatorExecutionConfig(clock);
    return Behaviors.setup(context -> new PDevsCoordinator<QuantizedSimTime>(coordinatorName,
        actors, routes, context, tracker, config, bindings, childOrder));
  }

  private static TimeMapping<DoubleSimTime> doubleMapping() {
    return new TimeMapping<>(new DoubleTimeCodec(),
        TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO));
  }

  private static PDevsCouplings couplings(List<String> children) {
    var builder = PDevsCouplings.builder(coordinatorName);
    for (String child : children) {
      if (!child.equals("target")) {
        builder.addConnection(child, "out", "target", "in");
      }
      builder.addConnection(child, "out", coordinatorName, "external");
    }
    return builder.addConnection(coordinatorName, "input", "target", "in").build();
  }

  private static SimTime nativeTime(String child, long millis) {
    return child.equals("target") ? LongSimTime.create(millis) : DoubleSimTime.create(millis);
  }

  private static SimTime nativeInfinity(String child) {
    return child.equals("target") ? longInfinity : doubleInfinity;
  }

  private static QuantizedSimTime seconds(long numerator, long denominator) {
    return QuantizedSimTime.of(Rational.of(numerator, denominator));
  }

  private static PortValue<?> value(String port, Object value) {
    return PortValue.builder().value(value).portName(port).build();
  }

  private static String id() {
    return "time-semantics-" + messageSequence.incrementAndGet();
  }

  private static SimulationInitMessage<QuantizedSimTime> init(ActorRef<DevsMessage> parent,
      EnhancedClockContext suppliedClock) {
    return new SimulationInitMessage<>(SimulationInit.<QuantizedSimTime>builder()
        .eventTime(QuantizedSimTime.of(Rational.ZERO)).simulationRunId(simulationId).messageId(id())
        .senderId(rootName).receiverId(coordinatorName).build(), parent, suppliedClock);
  }

  private static NextInternalTimeReport<SimTime> next(String sender, SimTime event, SimTime next) {
    return NextInternalTimeReport.<SimTime>builder().eventTime(event).simulationRunId(simulationId)
        .messageId(id()).senderId(sender).receiverId(coordinatorName).nextInternalTime(next).build();
  }

  private static RequestOutput<QuantizedSimTime> request(QuantizedSimTime time) {
    return RequestOutput.<QuantizedSimTime>builder().eventTime(time).simulationRunId(simulationId)
        .messageId(id()).senderId(rootName).receiverId(coordinatorName).build();
  }

  private static OutputReport<SimTime> output(String sender, SimTime time, List<PortValue<?>> outputs) {
    return OutputReport.<SimTime>builder().eventTime(time)
        .payload(OutputReportPayload.builder().addAllOutputs(outputs).build())
        .simulationRunId(simulationId).messageId(id()).senderId(sender).receiverId(coordinatorName)
        .nextInternalTime(time).build();
  }

  private static OutputReport<SimTime> outputWithContext(String sender, SimTime event, SimTime next,
      String run, String receiver) {
    return OutputReport.<SimTime>builder().eventTime(event).payload(OutputReportPayload.builder().build())
        .simulationRunId(run).messageId(id()).senderId(sender).receiverId(receiver).nextInternalTime(next).build();
  }

  private static ExecuteTransition<QuantizedSimTime> execute(QuantizedSimTime time, List<PortValue<?>> inputs) {
    return ExecuteTransition.<QuantizedSimTime>builder().eventTime(time)
        .payload(ExecuteTransitionPayload.builder().addAllInputs(inputs).build())
        .simulationRunId(simulationId).messageId(id()).senderId(rootName).receiverId(coordinatorName).build();
  }

  private static TransitionComplete<SimTime> complete(String sender, SimTime time, SimTime next) {
    return TransitionComplete.<SimTime>builder().eventTime(time).simulationRunId(simulationId)
        .messageId(id()).senderId(sender).receiverId(coordinatorName).nextInternalTime(next).build();
  }

  private static ModelTerminated<SimTime> terminated(String sender) {
    return ModelTerminated.<SimTime>builder().simulationRunId(simulationId).messageId(id())
        .senderId(sender).receiverId(coordinatorName).build();
  }
}