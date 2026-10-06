package devs;

import static org.junit.jupiter.api.Assertions.*;

import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
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
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.time.DoubleTimeCodec;
import devs.time.EnhancedClockContext;
import devs.time.LongTimeCodec;
import devs.time.Rational;
import devs.time.TimeMapping;
import devs.time.TimeSemantics;
import devs.time.TimeGranularity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class CoupledModelFactoryTest {
  private static final ActorTestKit testKit = ActorTestKit.create();
  private static final EnhancedClockContext clock =
      new EnhancedClockContext("shared", Rational.of(10));

  @AfterAll
  static void shutdown() {
    testKit.shutdownTestKit();
  }

  private CoordinatorExecutionConfig config(String... children) {
    return new CoordinatorExecutionConfig(clock);
  }

  private TimeMapping<LongSimTime> longMapping() {
    return new TimeMapping<>(new LongTimeCodec(),
        TimeSemantics.of(Rational.of(1, 1000), Rational.of(9)), Rational.ONE);
  }

  private <N extends SimTime> SimulatorProvider<N> recordingProvider(String identifier,
      TestProbe<N> initialTimes, TestProbe<DevsMessage> messages) {
    return new SimulatorProvider<>() {
      @Override
      public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, N initialTime) {
        initialTimes.ref().tell(initialTime);
        return messages.ref();
      }

      @Override
      public String getModelIdentifier() {
        return identifier;
      }
    };
  }

  private SimulationInitMessage<QuantizedSimTime> init(QuantizedSimTime start,
      TestProbe<DevsMessage> parent) {
    return new SimulationInitMessage<>(SimulationInit.<QuantizedSimTime>builder()
        .eventTime(start).simulationRunId("factory-test").messageId("init")
        .senderId("root").receiverId("coupled").build(), parent.ref(), clock);
  }

  @Test
  void heterogeneousProvidersReceiveMappedCreationAndInitializationTimes() {
    TestProbe<LongSimTime> longInitial = testKit.createTestProbe();
    TestProbe<DoubleSimTime> doubleInitial = testKit.createTestProbe();
    TestProbe<DevsMessage> longMessages = testKit.createTestProbe();
    TestProbe<DevsMessage> doubleMessages = testKit.createTestProbe();
    TestProbe<DevsMessage> parent = testKit.createTestProbe();
    ChildBinding<LongSimTime> integral = ChildBinding.nativeProvider(
        recordingProvider("integral", longInitial, longMessages), LongSimTime.class, longMapping());
    ChildBinding<DoubleSimTime> binary = ChildBinding.nativeProvider(
        recordingProvider("binary", doubleInitial, doubleMessages), DoubleSimTime.class,
        new TimeMapping<>(new DoubleTimeCodec(),
            TimeSemantics.of(Rational.of(1, 2), Rational.of(11))));
    AtomicInteger trackers = new AtomicInteger();
    CoupledModelFactory<QuantizedSimTime> factory = CoupledModelFactory.enhanced("coupled",
        List.of(binary, integral), PDevsCouplings.builder("coupled").build(),
        config(), List.of("integral", "binary"), () -> {
          trackers.incrementAndGet();
          return StepTimingTracker.disabled();
        });
    QuantizedSimTime start = QuantizedSimTime.of(Rational.of(41, 4));
    ActorRef<DevsMessage> coordinator = testKit.spawn(factory.create(start));
    longInitial.expectMessage(LongSimTime.create(250));
    doubleInitial.expectMessage(DoubleSimTime.create(-1.5));
    coordinator.tell(init(start, parent));
    SimulationInitMessage<?> integralInit = longMessages.expectMessageClass(SimulationInitMessage.class);
    SimulationInitMessage<?> binaryInit = doubleMessages.expectMessageClass(SimulationInitMessage.class);
    assertEquals(LongSimTime.create(250), integralInit.getSimulationInit().getEventTime());
    assertEquals(DoubleSimTime.create(-1.5), binaryInit.getSimulationInit().getEventTime());
    assertTrue(integralInit.getEnhancedClock().isEmpty());
    assertTrue(binaryInit.getEnhancedClock().isEmpty());
    assertEquals("integral", integralInit.getSimulationInit().getReceiverId());
    assertEquals(coordinator, integralInit.getParent());
    assertEquals(1, trackers.get());
    testKit.stop(coordinator);
  }

  @Test
  void directCoordinatorReferenceReceivesCanonicalInitAndSharedContext() {
    TestProbe<DevsMessage> nested = testKit.createTestProbe();
    TestProbe<DevsMessage> parent = testKit.createTestProbe();
    CoupledModelFactory<QuantizedSimTime> factory = CoupledModelFactory.enhanced("coupled",
        List.of(ChildBinding.coordinatorActor("nested", nested.ref(), clock)),
        PDevsCouplings.builder("coupled").build(), config("nested"));
    QuantizedSimTime start = QuantizedSimTime.of(Rational.of(31, 3));
    ActorRef<DevsMessage> actor = testKit.spawn(factory.create(start));
    actor.tell(init(start, parent));
    SimulationInitMessage<?> message = nested.expectMessageClass(SimulationInitMessage.class);
    assertEquals(start, message.getSimulationInit().getEventTime());
    assertEquals(clock, message.getEnhancedClock().orElseThrow());
    testKit.stop(actor);
  }

  @Test
  void nestedFactoryConstructionValidatesClockIdentityAndOrigin() {
    CoupledModelFactory<QuantizedSimTime> nested = CoupledModelFactory.enhanced("nested", List.of(),
        PDevsCouplings.builder("nested").build(), config());
    CoupledModelFactory<QuantizedSimTime> factory = CoupledModelFactory.enhanced("coupled",
        List.of(ChildBinding.coordinator(nested)), PDevsCouplings.builder("coupled").build(),
        config("nested"));
    assertTrue(factory.isEnhanced());
    assertEquals(clock, factory.executionConfig().clock());
    for (EnhancedClockContext wrong : List.of(
        new EnhancedClockContext("different", Rational.of(10)),
        new EnhancedClockContext("shared", Rational.ZERO))) {
      assertThrows(IllegalArgumentException.class, () -> CoupledModelFactory.enhanced("coupled",
          List.of(ChildBinding.coordinator(nested)), PDevsCouplings.builder("coupled").build(),
          new CoordinatorExecutionConfig(wrong)));
      assertThrows(IllegalArgumentException.class, () -> CoupledModelFactory.enhanced("coupled",
          List.of(ChildBinding.coordinatorActor("nested", testKit.<DevsMessage>createTestProbe().ref(), wrong)),
          PDevsCouplings.builder("coupled").build(), config("nested")));
    }
  }

  @Test
  void factoryRejectsDuplicateMissingChildrenAndOffLatticeInitialization() {
    ChildBinding<LongSimTime> binding = ChildBinding.nativeActor("native",
        testKit.<DevsMessage>createTestProbe().ref(), LongSimTime.class, longMapping());
    PDevsCouplings couplings = PDevsCouplings.builder("coupled").build();
    assertThrows(IllegalArgumentException.class,
        () -> CoupledModelFactory.enhanced("coupled", List.of(binding, binding), couplings,
            config("native")));
    assertThrows(IllegalArgumentException.class,
        () -> CoupledModelFactory.enhanced("coupled", List.of(binding), couplings, config(),
            List.of("other")));
    CoupledModelFactory<QuantizedSimTime> factory = CoupledModelFactory.enhanced("coupled",
        List.of(binding), couplings, config("native"));
    assertThrows(ArithmeticException.class,
        () -> factory.create(QuantizedSimTime.of(Rational.of(20001, 2000))));
  }

  @Test
  void legacyProviderAndFactorySignaturesRemainAvailable() {
    TestProbe<LongSimTime> initialTimes = testKit.createTestProbe();
    TestProbe<DevsMessage> messages = testKit.createTestProbe();
    CoupledModelFactory<LongSimTime> factory = new CoupledModelFactory<>("legacy",
        List.of(recordingProvider("native", initialTimes, messages)),
        PDevsCouplings.builder("legacy").build(), StepTimingTracker::disabled);
    assertFalse(factory.isEnhanced());
    assertNull(factory.executionConfig());
    assertEquals("legacy", factory.getModelIdentifier());
    ActorRef<DevsMessage> actor = testKit.spawn(factory.create(LongSimTime.create(7)));
    assertEquals(LongSimTime.create(7), initialTimes.receiveMessage());
    testKit.stop(actor);
  }

  private SimulatorProvider<LongSimTime> orderedProvider(String identifier,
      List<String> creations, TestInbox<DevsMessage> messages) {
    return new SimulatorProvider<>() {
      @Override
      public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context,
          LongSimTime initialTime) {
        assertEquals(LongSimTime.create(0), initialTime);
        creations.add(identifier);
        return messages.getRef();
      }

      @Override
      public String getModelIdentifier() {
        return identifier;
      }
    };
  }

  @Test
  void legacyOverloadsSnapshotProvidersAndSpawnInResolvedOrder() {
    for (boolean custom : List.of(false, true)) {
      for (boolean suppliedTracker : List.of(false, true)) {
        TestInbox<DevsMessage> messages = TestInbox.create();
        List<String> creations = new ArrayList<>();
        List<SimulatorProvider<LongSimTime>> providers = new ArrayList<>();
        for (String id : List.of("a", "Z", "A")) {
          providers.add(orderedProvider(id, creations, messages));
        }
        List<String> order = new ArrayList<>(List.of("a", "A", "Z"));
        List<String> expected = custom ? List.copyOf(order) : List.of("A", "Z", "a");
        AtomicInteger trackers = new AtomicInteger();
        Supplier<StepTimingTracker> supplier = () -> {
          trackers.incrementAndGet();
          return StepTimingTracker.disabled();
        };
        PDevsCouplings couplings = PDevsCouplings.builder("coupled")
            .addConnection("coupled", "in", "a", "in")
            .addConnection("coupled", "in", "Z", "in")
            .addConnection("coupled", "in", "A", "in")
            .addConnection("a", "out", "Z", "in")
            .addConnection("a", "out", "A", "in")
            .addConnection("A", "out", "Z", "in")
            .addConnection("a", "out", "coupled", "out")
            .addConnection("A", "out", "coupled", "out").build();
        CoupledModelFactory<LongSimTime> factory = custom
            ? suppliedTracker
                ? new CoupledModelFactory<>("coupled", providers, couplings, order, supplier)
                : new CoupledModelFactory<>("coupled", providers, couplings, order)
            : suppliedTracker
                ? new CoupledModelFactory<>("coupled", providers, couplings, supplier)
                : new CoupledModelFactory<>("coupled", providers, couplings);
        providers.clear();
        order.clear();
        var behavior = factory.create(LongSimTime.create(0));
        order.add("mutated");
        assertTrue(creations.isEmpty());
        assertEquals(0, trackers.get());
        BehaviorTestKit<DevsMessage> kit = BehaviorTestKit.create(behavior);
        assertEquals(expected, creations);
        assertEquals(suppliedTracker ? 1 : 0, trackers.get());
        assertLegacyRuntimeOrder(kit, messages, expected);
        creations.clear();
        BehaviorTestKit.create(factory.create(LongSimTime.create(0)));
        assertEquals(expected, creations);
        assertEquals(suppliedTracker ? 2 : 0, trackers.get());
      }
    }
  }

  private void assertLegacyRuntimeOrder(BehaviorTestKit<DevsMessage> kit,
      TestInbox<DevsMessage> messages, List<String> expected) {
    TestInbox<DevsMessage> parent = TestInbox.create();
    LongSimTime start = LongSimTime.create(0);
    LongSimTime event = LongSimTime.create(1000);
    LongSimTime next = LongSimTime.create(2000);
    kit.run(new SimulationInitMessage<>(SimulationInit.<LongSimTime>builder()
        .eventTime(start).simulationRunId("factory-test").messageId("init")
        .senderId("root").receiverId("coupled").build(), parent.getRef()));
    for (String id : expected) {
      SimulationInitMessage<?> init = assertInstanceOf(SimulationInitMessage.class,
          messages.receiveMessage());
      assertEquals(id, init.getSimulationInit().getReceiverId());
      assertEquals(start, init.getSimulationInit().getEventTime());
      assertTrue(init.getEnhancedClock().isEmpty());
    }
    assertFalse(messages.hasMessages());
    for (String id : List.of("Z", "a", "A")) {
      kit.run(NextInternalTimeReport.<LongSimTime>builder().eventTime(start)
          .simulationRunId("factory-test").messageId("next-" + id)
          .senderId(id).receiverId("coupled")
          .nextInternalTime(id.equals("Z") ? next : event).build());
    }
    assertEquals(event, assertInstanceOf(NextInternalTimeReport.class,
        parent.receiveMessage()).getNextInternalTime());
    kit.run(RequestOutput.<LongSimTime>builder().eventTime(event)
        .simulationRunId("factory-test").messageId("request")
        .senderId("root").receiverId("coupled").build());
    List<String> imminent = expected.stream().filter(id -> !id.equals("Z")).toList();
    for (String id : imminent) {
      RequestOutput<?> request = assertInstanceOf(RequestOutput.class, messages.receiveMessage());
      assertEquals(id, request.getReceiverId());
      assertEquals(event, request.getEventTime());
    }
    assertFalse(messages.hasMessages());
    for (String id : List.of("a", "A")) {
      kit.run(OutputReport.<LongSimTime>builder().eventTime(event)
          .payload(OutputReportPayload.builder().addAllOutputs(List.of(
              PortValue.builder().value(id + "-1").portName("out").build(),
              PortValue.builder().value(id + "-2").portName("out").build())).build())
          .simulationRunId("factory-test").messageId("output-" + id)
          .senderId(id).receiverId("coupled").nextInternalTime(event).build());
      if (id.equals("a")) {
        assertFalse(messages.hasMessages());
      }
    }
    for (String id : expected) {
      ExecuteTransition<?> transition = assertInstanceOf(ExecuteTransition.class,
          messages.receiveMessage());
      assertEquals(id, transition.getReceiverId());
      assertEquals(event, transition.getEventTime());
      List<PortValue<?>> bag = new ArrayList<>();
      for (String source : imminent) {
        if (id.equals("Z") || id.equals("A") && source.equals("a")) {
          bag.add(PortValue.builder().value(source + "-1").portName("in").build());
          bag.add(PortValue.builder().value(source + "-2").portName("in").build());
        }
      }
      assertEquals(bag, transition.getPayload().getInputs());
    }
    assertFalse(messages.hasMessages());
    assertFalse(parent.hasMessages());
    for (String id : List.of("Z", "a", "A")) {
      kit.run(TransitionComplete.<LongSimTime>builder().eventTime(event)
          .simulationRunId("factory-test").messageId("complete-" + id)
          .senderId(id).receiverId("coupled").nextInternalTime(next).build());
    }
    OutputReport<?> output = assertInstanceOf(OutputReport.class, parent.receiveMessage());
    List<PortValue<?>> outputBag = new ArrayList<>();
    for (String id : imminent) {
      outputBag.add(PortValue.builder().value(id + "-1").portName("out").build());
      outputBag.add(PortValue.builder().value(id + "-2").portName("out").build());
    }
    assertEquals(outputBag, output.getPayload().getOutputs());
    assertEquals(event, output.getEventTime());
    assertEquals(next, output.getNextInternalTime());
    LongSimTime externalTime = LongSimTime.create(1500);
    List<PortValue<?>> inputs = List.of(
        PortValue.builder().value("first").portName("in").build(),
        PortValue.builder().value("second").portName("in").build());
    kit.run(ExecuteTransition.<LongSimTime>builder().eventTime(externalTime)
        .payload(ExecuteTransitionPayload.builder().addAllInputs(inputs).build())
        .simulationRunId("factory-test").messageId("external")
        .senderId("root").receiverId("coupled").build());
    for (String id : expected) {
      ExecuteTransition<?> transition = assertInstanceOf(ExecuteTransition.class,
          messages.receiveMessage());
      assertEquals(id, transition.getReceiverId());
      assertEquals(externalTime, transition.getEventTime());
      assertEquals(inputs, transition.getPayload().getInputs());
    }
    assertFalse(messages.hasMessages());
    for (String id : List.of("Z", "a", "A")) {
      kit.run(TransitionComplete.<LongSimTime>builder().eventTime(externalTime)
          .simulationRunId("factory-test").messageId("external-complete-" + id)
          .senderId(id).receiverId("coupled").nextInternalTime(next).build());
    }
    assertEquals(externalTime, assertInstanceOf(TransitionComplete.class,
        parent.receiveMessage()).getEventTime());
    var termination = SimulationTerminatePayload.builder().reason("done").build();
    kit.run(SimulationTerminate.<LongSimTime>builder().eventTime(next)
        .simulationRunId("factory-test").messageId("terminate")
        .senderId("root").receiverId("coupled").payload(termination).build());
    for (String id : expected) {
      SimulationTerminate<?> message = assertInstanceOf(SimulationTerminate.class,
          messages.receiveMessage());
      assertEquals(id, message.getReceiverId());
      assertEquals(next, message.getEventTime());
      assertEquals(termination, message.getPayload());
    }
    assertFalse(messages.hasMessages());
    assertFalse(parent.hasMessages());
  }

  @Test
  void enhancedOverloadsSnapshotBindingsAndForwardResolvedOrder() {
    for (boolean custom : List.of(false, true)) {
      for (boolean suppliedTracker : List.of(false, true)) {
        TestInbox<DevsMessage> messages = TestInbox.create();
        List<String> creations = new ArrayList<>();
        List<ChildBinding<?>> bindings = new ArrayList<>();
        for (String id : List.of("a", "Z", "A")) {
          bindings.add(ChildBinding.nativeProvider(orderedProvider(id, creations, messages),
              LongSimTime.class, longMapping()));
        }
        List<String> order = new ArrayList<>(List.of("a", "A", "Z"));
        List<String> expected = custom ? List.copyOf(order) : List.of("A", "Z", "a");
        List<StepTimingTracker> trackers = new ArrayList<>();
        Supplier<StepTimingTracker> supplier = () -> {
          StepTimingTracker tracker = StepTimingTracker.enabled(System::nanoTime);
          trackers.add(tracker);
          return tracker;
        };
        PDevsCouplings couplings = PDevsCouplings.builder("coupled")
            .addConnection("coupled", "in", "a", "in")
            .addConnection("coupled", "in", "Z", "in")
            .addConnection("coupled", "in", "A", "in").build();
        CoupledModelFactory<QuantizedSimTime> factory = custom
            ? suppliedTracker
                ? CoupledModelFactory.enhanced("coupled", bindings, couplings, config(), order, supplier)
                : CoupledModelFactory.enhanced("coupled", bindings, couplings, config(), order)
            : suppliedTracker
                ? CoupledModelFactory.enhanced("coupled", bindings, couplings, config(), supplier)
                : CoupledModelFactory.enhanced("coupled", bindings, couplings, config());
        bindings.clear();
        order.clear();
        QuantizedSimTime start = QuantizedSimTime.of(Rational.of(10));
        var behavior = factory.create(start);
        order.add("mutated");
        assertTrue(creations.isEmpty());
        assertTrue(trackers.isEmpty());
        for (boolean imminent : List.of(true, false)) {
          creations.clear();
          BehaviorTestKit<DevsMessage> kit = BehaviorTestKit.create(behavior);
          assertEquals(expected, creations);
          TestInbox<DevsMessage> parent = TestInbox.create();
          kit.run(new SimulationInitMessage<>(SimulationInit.<QuantizedSimTime>builder()
              .eventTime(start).simulationRunId("factory-test").messageId("init")
              .senderId("root").receiverId("coupled").build(), parent.getRef(), clock));
          for (String id : expected) {
            assertEquals(id, assertInstanceOf(SimulationInitMessage.class,
                messages.receiveMessage()).getSimulationInit().getReceiverId());
          }
          for (String id : List.of("Z", "a", "A")) {
            kit.run(NextInternalTimeReport.<LongSimTime>builder()
                .eventTime(LongSimTime.create(0))
                .simulationRunId("factory-test").messageId("next-" + id)
                .senderId(id).receiverId("coupled").nextInternalTime(imminent
                    ? LongSimTime.create(1000) : LongSimTime.create(Long.MAX_VALUE)).build());
          }
          assertInstanceOf(NextInternalTimeReport.class, parent.receiveMessage());
          QuantizedSimTime event = QuantizedSimTime.of(imminent
              ? Rational.of(11) : Rational.of(21, 2));
          if (imminent) {
            kit.run(RequestOutput.<QuantizedSimTime>builder().eventTime(event)
                .simulationRunId("factory-test").messageId("request")
                .senderId("root").receiverId("coupled").build());
            for (String id : expected) {
              assertEquals(id, assertInstanceOf(RequestOutput.class,
                  messages.receiveMessage()).getReceiverId());
            }
            for (String id : List.of("Z", "a", "A")) {
              kit.run(OutputReport.<LongSimTime>builder().eventTime(LongSimTime.create(1000))
                  .payload(OutputReportPayload.builder().build())
                  .simulationRunId("factory-test").messageId("output-" + id)
                  .senderId(id).receiverId("coupled")
                  .nextInternalTime(LongSimTime.create(1000)).build());
            }
            assertInstanceOf(OutputReport.class, parent.receiveMessage());
            if (suppliedTracker) {
              assertEquals(1, trackers.get(0).getAggregateStats(
                  StepTimingTracker.AggregateCategory.OUTPUT_ROUND_TRIP).getCount());
            }
          }
          kit.run(ExecuteTransition.<QuantizedSimTime>builder().eventTime(event)
              .payload(ExecuteTransitionPayload.builder().addAllInputs(imminent ? List.of()
                  : List.of(PortValue.builder().value("input").portName("in").build())).build())
              .simulationRunId("factory-test").messageId("execute")
              .senderId("root").receiverId("coupled").build());
          for (String id : expected) {
            ExecuteTransition<?> transition = assertInstanceOf(ExecuteTransition.class,
                messages.receiveMessage());
            assertEquals(id, transition.getReceiverId());
            assertEquals(LongSimTime.create(imminent ? 1000 : 500), transition.getEventTime());
            assertEquals(imminent ? 0 : 1, transition.getPayload().getInputs().size());
          }
          assertFalse(messages.hasMessages());
        }
        assertEquals(suppliedTracker ? 2 : 0, trackers.size());
      }
    }
  }

  @Test
  void bothModesRejectInvalidExplicitOrdersBeforeSpawning() {
    TestInbox<DevsMessage> messages = TestInbox.create();
    List<String> creations = new ArrayList<>();
    var upper = orderedProvider("A", creations, messages);
    var lower = orderedProvider("a", creations, messages);
    List<SimulatorProvider<LongSimTime>> providers = List.of(upper, lower);
    List<ChildBinding<?>> bindings = List.of(
        ChildBinding.nativeProvider(upper, LongSimTime.class, longMapping()),
        ChildBinding.nativeProvider(lower, LongSimTime.class, longMapping()));
    PDevsCouplings couplings = PDevsCouplings.builder("coupled").build();
    AtomicInteger trackers = new AtomicInteger();
    Supplier<StepTimingTracker> supplier = () -> {
      trackers.incrementAndGet();
      return StepTimingTracker.disabled();
    };
    for (List<String> invalid : List.of(List.<String>of(), List.of("A"),
        List.of("A", "A"), List.of("A", "a", "extra"), List.of("A", "unknown"),
        List.of("A", " "), List.of("A", " a"), Arrays.asList("A", null))) {
      assertThrows(IllegalArgumentException.class,
          () -> new CoupledModelFactory<>("coupled", providers, couplings, invalid));
      assertThrows(IllegalArgumentException.class,
          () -> new CoupledModelFactory<>("coupled", providers, couplings, invalid, supplier));
      assertThrows(IllegalArgumentException.class,
          () -> CoupledModelFactory.enhanced("coupled", bindings, couplings, config(), invalid));
      assertThrows(IllegalArgumentException.class,
          () -> CoupledModelFactory.enhanced("coupled", bindings, couplings, config(), invalid, supplier));
    }
    assertThrows(NullPointerException.class,
        () -> new CoupledModelFactory<>("coupled", providers, couplings, (List<String>) null));
    assertThrows(NullPointerException.class,
        () -> new CoupledModelFactory<>("coupled", providers, couplings, null, supplier));
    assertThrows(NullPointerException.class,
        () -> CoupledModelFactory.enhanced("coupled", bindings, couplings, config(), (List<String>) null));
    assertThrows(NullPointerException.class,
        () -> CoupledModelFactory.enhanced("coupled", bindings, couplings, config(), null, supplier));
    assertThrows(IllegalArgumentException.class,
        () -> new CoupledModelFactory<>("coupled", List.of(upper, upper), couplings));
    assertTrue(creations.isEmpty());
    assertEquals(0, trackers.get());
  }

  @Test
  void adaptiveRoutesNeverRoundProviderOrExistingActorInitialization() {
    var mapping = new TimeMapping<>(new DoubleTimeCodec(),
        TimeSemantics.of(Rational.of(1, 1000), Rational.ZERO,
            Optional.of(TimeGranularity.of(Rational.ONE))));
    TestProbe<DoubleSimTime> initialTimes = testKit.createTestProbe();
    TestProbe<DevsMessage> messages = testKit.createTestProbe();
    var provider = ChildBinding.nativeProvider(recordingProvider("native", initialTimes, messages),
        DoubleSimTime.class, mapping);
    var actor = ChildBinding.nativeActor("native", messages.ref(), DoubleSimTime.class, mapping);
    var configuration = new CoordinatorExecutionConfig(clock);
    for (ChildBinding<?> binding : List.of(provider, actor)) {
      var factory = CoupledModelFactory.enhanced("coupled", List.of(binding),
          PDevsCouplings.builder("coupled")
              .addConnection("coupled", "in", "native", "in").build(), configuration);
      for (Rational offGrid : List.of(Rational.of(1, 2000), Rational.of(1, 3000))) {
        assertThrows(ArithmeticException.class, () -> factory.create(QuantizedSimTime.of(offGrid)));
        assertThrows(ArithmeticException.class, () -> binding.spawn(null, QuantizedSimTime.of(offGrid)));
      }
      for (long nativeStart : new long[]{-1, 0}) {
        assertDoesNotThrow(() -> factory.create(QuantizedSimTime.of(Rational.of(nativeStart, 1000))));
      }
    }
    initialTimes.expectNoMessage();
    messages.expectNoMessage();
  }
}