package devs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devs.experimentalframe.Acceptor;
import devs.experimentalframe.Generator;
import devs.experimentalframe.LogBaseTwoDecimalCalculatorModel;
import devs.experimentalframe.PowerOfTwoGenerator;
import devs.iso.PortValue;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.LongSimTime;
import devs.msg.state.ScheduleState;
import devs.msg.state.TimeState;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.time.DecimalTimeCodec;
import devs.time.DecimalTimeMapping;
import devs.time.EnhancedClockContext;
import devs.time.TimeSemantics;
import devs.utils.Schedule;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.Test;

class EnhancedDevsModelTestTest {
  private static final String FRAME_ID = "enhanced-test-frame";
  private static final String NESTED_ID = "enhanced-nested-model";
  private static final String CALCULATOR_ID = LogBaseTwoDecimalCalculatorModel.MODEL_ID;
  private static final DecimalTimeMapping<DecimalSimTime> IDENTITY =
      new DecimalTimeMapping<>(new DecimalTimeCodec(),
          TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO));

  @Test
  void executesNestedEnhancedModelAtCanonicalStart() throws InterruptedException {
    List<Integer> output = new CopyOnWriteArrayList<>();
    FixtureHarness harness = new FixtureHarness(output);

    harness.executeExperimentalFrame(time("0"), time("3"), "nested-success", 5);

    assertEquals(List.of(0), output);
  }

  @Test
  void rejectsMismatchedClockAndUnrepresentableInitializationAndStopsActors() {
    AtomicInteger stopped = new AtomicInteger();
    FixtureHarness wrongClock = new FixtureHarness(new CopyOnWriteArrayList<>()) {
      @Override
      protected CoupledModelFactory<DecimalSimTime> buildCoupledModel(
          String runId, EnhancedClockContext clock) {
        return nestedFactory(new EnhancedClockContext(clock.clockId(),
            clock.compositionOrigin().add(BigDecimal.ONE)));
      }

      @Override
      protected CompletableFuture<Void> stopAdditionalActors(ActorTestKit testKit,
          Duration timeout) {
        stopped.incrementAndGet();
        return CompletableFuture.completedFuture(null);
      }
    };
    assertThrows(IllegalArgumentException.class,
        () -> wrongClock.executeExperimentalFrame(time("0"), time("3"), "wrong-clock", 2));
    assertEquals(1, stopped.get());

    FixtureHarness inexactStart = new FixtureHarness(new CopyOnWriteArrayList<>()) {
      @Override
      protected CoupledModelFactory<DecimalSimTime> buildCoupledModel(
          String runId, EnhancedClockContext clock) {
        ChildBinding<?> longChild = ChildBinding.defaultLongProvider(
            new DevsSimulatorProvider<>(new PassiveLongAcceptor("long-native-child")), 1.0);
        return CoupledModelFactory.enhanced(NESTED_ID, List.of(longChild),
            PDevsCouplings.builder(NESTED_ID).build(), new CoordinatorExecutionConfig(clock));
      }
    };
    assertThrows(ArithmeticException.class,
        () -> inexactStart.executeExperimentalFrame(time("0.5"), time("3"), "inexact", 2));

    FixtureHarness legacyCoordinator = new FixtureHarness(new CopyOnWriteArrayList<>()) {
      @SuppressWarnings("deprecation")
      @Override
      protected CoupledModelFactory<DecimalSimTime> buildCoupledModel(
          String runId, EnhancedClockContext clock) {
        SimulatorProvider<DecimalSimTime> provider = new DevsSimulatorProvider<>(
            new LogBaseTwoDecimalCalculatorModel(BigDecimal.ZERO));
        return new CoupledModelFactory<>(NESTED_ID, List.of(provider),
            PDevsCouplings.builder(NESTED_ID).build());
      }
    };
    assertThrows(IllegalArgumentException.class,
        () -> legacyCoordinator.executeExperimentalFrame(time("0"), time("3"), "legacy-child", 2));
  }

  @Test
  void propagatesAcceptorAndMissingOutputFailures() {
    AssertionError acceptorFailure = new AssertionError("acceptor rejected output");
    FixtureHarness failingAcceptor = new FixtureHarness(new CopyOnWriteArrayList<>(),
        acceptorFailure);
    AssertionError observed = assertThrows(AssertionError.class,
        () -> failingAcceptor.executeExperimentalFrame(time("0"), time("3"), "acceptor-failure", 5));
    assertSame(acceptorFailure, observed);

    FixtureHarness missingOutput = new FixtureHarness(new CopyOnWriteArrayList<>()) {
      @Override
      protected Generator<DecimalSimTime> buildGenerator() {
        return new EmptyGenerator();
      }

      @Override
      protected void verifyExpectedOutputs() {
        assertFalse(output().isEmpty(), "expected output was not observed");
      }
    };
    assertThrows(AssertionError.class,
        () -> missingOutput.executeExperimentalFrame(time("0"), time("3"), "missing-output", 5));
  }

  @Test
  void boundsStopHookAndRunsItAfterSetupFailure() {
    AtomicInteger stopCalls = new AtomicInteger();
    FixtureHarness hangingStop = new FixtureHarness(new CopyOnWriteArrayList<>()) {
      @Override
      protected CompletableFuture<Void> stopAdditionalActors(ActorTestKit testKit,
          Duration timeout) {
        stopCalls.incrementAndGet();
        return new CompletableFuture<>();
      }
    };
    long before = System.nanoTime();
    AssertionError stopTimeout = assertThrows(AssertionError.class,
        () -> hangingStop.executeExperimentalFrame(time("0"), time("3"), "stop-timeout", 1));
    assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - before) < 5);
    assertTrue(stopTimeout.getMessage().contains("Timed out stopping"));
    assertEquals(1, stopCalls.get());

    IllegalStateException setupFailure = new IllegalStateException("setup failed");
    FixtureHarness setupFailureHarness = new FixtureHarness(new CopyOnWriteArrayList<>()) {
      @Override
      protected Generator<DecimalSimTime> buildGenerator() {
        throw setupFailure;
      }

      @Override
      protected CompletableFuture<Void> stopAdditionalActors(ActorTestKit testKit,
          Duration timeout) {
        stopCalls.incrementAndGet();
        return CompletableFuture.failedFuture(new IllegalArgumentException("stop failed"));
      }
    };
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> setupFailureHarness.executeExperimentalFrame(time("0"), time("3"), "setup-failure", 2));
    assertSame(setupFailure, thrown);
    assertEquals(1, thrown.getSuppressed().length);
    assertEquals(2, stopCalls.get());
  }

  @Test
  void stopsAdditionalActorsAfterSimulationTimeout() {
    CountDownLatch enteredOutput = new CountDownLatch(1);
    CountDownLatch releaseOutput = new CountDownLatch(1);
    AtomicInteger stopCalls = new AtomicInteger();
    FixtureHarness blockedGenerator = new FixtureHarness(new CopyOnWriteArrayList<>()) {
      @Override
      protected Generator<DecimalSimTime> buildGenerator() {
        return new BlockingGenerator(enteredOutput, releaseOutput);
      }

      @Override
      protected CompletableFuture<Void> stopAdditionalActors(ActorTestKit testKit,
          Duration timeout) {
        stopCalls.incrementAndGet();
        releaseOutput.countDown();
        return CompletableFuture.completedFuture(null);
      }
    };

    assertThrows(AssertionError.class, () -> blockedGenerator.executeExperimentalFrame(
        time("0"), time("3"), "simulation-timeout", 1));

    assertEquals(0, enteredOutput.getCount());
    assertEquals(1, stopCalls.get());
  }

  @Test
  void supportsRepeatedRunsWithFreshModelInstances() throws InterruptedException {
    List<Integer> output = new CopyOnWriteArrayList<>();
    FixtureHarness harness = new FixtureHarness(output);

    harness.executeExperimentalFrame(time("0"), time("3"), "first-run", 5);
    harness.executeExperimentalFrame(time("0"), time("3"), "second-run", 5);

    assertEquals(List.of(0, 0), output);
  }

  @Test
  void preservesLegacyHarnessSubclassApiAndExecution() throws InterruptedException {
    new LegacyHarness().executeExperimentalFrame(LongSimTime.create(0L), LongSimTime.create(8L),
        "legacy-compatibility", 5);
  }

  @Test
  void rejectsInvalidFrameRangeBeforeCreatingActors() {
    FixtureHarness harness = new FixtureHarness(new CopyOnWriteArrayList<>());
    assertThrows(IllegalArgumentException.class,
        () -> harness.executeExperimentalFrame(time("2"), time("1"), "invalid-range", 1));
    assertThrows(IllegalArgumentException.class,
        () -> harness.executeExperimentalFrame(DecimalSimTime.infinity(), time("3"),
            "infinite-start", 1));
  }

  private static DecimalSimTime time(String value) {
    return DecimalSimTime.of(new BigDecimal(value));
  }

  private static CoupledModelFactory<DecimalSimTime> nestedFactory(EnhancedClockContext clock) {
    ChildBinding<DecimalSimTime> calculator = ChildBinding.nativeProvider(
        new DevsSimulatorProvider<>(new LogBaseTwoDecimalCalculatorModel(BigDecimal.ZERO)),
        DecimalSimTime.class, IDENTITY);
    PDevsCouplings couplings = PDevsCouplings.builder(NESTED_ID)
        .addConnection(NESTED_ID, "value", CALCULATOR_ID, "numberIn")
        .addConnection(CALCULATOR_ID, "numberOut", NESTED_ID, "result")
        .build();
    return CoupledModelFactory.enhanced(NESTED_ID, List.of(calculator), couplings,
        new CoordinatorExecutionConfig(clock));
  }

  private static class FixtureHarness extends EnhancedDevsModelTest {
    private final List<Integer> output;
    private final Throwable acceptorFailure;

    private FixtureHarness(List<Integer> output) {
      this(output, null);
    }

    private FixtureHarness(List<Integer> output, Throwable acceptorFailure) {
      super(FRAME_ID);
      this.output = output;
      this.acceptorFailure = acceptorFailure;
    }

    @Override
    protected Generator<DecimalSimTime> buildGenerator() {
      return new SingleValueGenerator();
    }

    @Override
    protected Acceptor<DecimalSimTime, ?> buildAcceptor(AtomicReference<Throwable> failureRef) {
      return new ResultAcceptor(output, failureRef, acceptorFailure);
    }

    @Override
    protected CoupledModelFactory<DecimalSimTime> buildCoupledModel(
        String runId, EnhancedClockContext clock) {
      return nestedFactory(clock);
    }

    @Override
    protected PDevsCouplings buildCouplings() {
      return PDevsCouplings.builder(FRAME_ID)
          .addConnection(SingleValueGenerator.MODEL_ID, "value", NESTED_ID, "value")
          .addConnection(NESTED_ID, "result", ResultAcceptor.MODEL_ID, "result")
          .build();
    }

    List<Integer> output() {
      return output;
    }
  }

  private static final class SingleValueGenerator extends Generator<DecimalSimTime> {
    private static final String MODEL_ID = "single-value-generator";
    private static final Port<Integer> VALUE = new Port<>("value", Integer.class);

    private SingleValueGenerator() {
      super(MODEL_ID, stateWithOutput());
    }

    private static ScheduleState<DecimalSimTime> stateWithOutput() {
      Schedule<DecimalSimTime> schedule = new Schedule<>();
      schedule.scheduleOutput(time("1"), VALUE, 1);
      return new ScheduleState<>(time("0"), schedule);
    }

    @Override
    public void handleScheduledEvents(List<Object> events) {}
  }

  private static final class EmptyGenerator extends Generator<DecimalSimTime> {
    private EmptyGenerator() {
      super(SingleValueGenerator.MODEL_ID,
          new ScheduleState<>(time("0"), new Schedule<>()));
    }

    @Override
    public void handleScheduledEvents(List<Object> events) {}
  }

  private static final class BlockingGenerator extends Generator<DecimalSimTime> {
    private final CountDownLatch enteredOutput;
    private final CountDownLatch releaseOutput;

    private BlockingGenerator(CountDownLatch enteredOutput, CountDownLatch releaseOutput) {
      super(SingleValueGenerator.MODEL_ID,
          SingleValueGenerator.stateWithOutput());
      this.enteredOutput = enteredOutput;
      this.releaseOutput = releaseOutput;
    }

    @Override
    public List<PortValue<?>> outputFunction() {
      enteredOutput.countDown();
      try {
        if (!releaseOutput.await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("Test cleanup did not release the generator");
        }
      } catch (InterruptedException interruption) {
        Thread.currentThread().interrupt();
      }
      return super.outputFunction();
    }

    @Override
    public void handleScheduledEvents(List<Object> events) {}
  }

  private static final class ResultAcceptor
      extends Acceptor<DecimalSimTime, TimeState<DecimalSimTime>> {
    private static final String MODEL_ID = "result-acceptor";
    private final List<Integer> output;
    private final AtomicReference<Throwable> failureRef;
    private final Throwable failureToRecord;

    private ResultAcceptor(List<Integer> output, AtomicReference<Throwable> failureRef,
        Throwable failureToRecord) {
      super(new TimeState<>(time("0")), MODEL_ID);
      this.output = output;
      this.failureRef = failureRef;
      this.failureToRecord = failureToRecord;
    }

    @Override
    public void internalStateTransitionFunction() {}

    @Override
    public void externalStateTransitionFunction(DecimalSimTime elapsedTime,
        List<PortValue<?>> inputs) {
      modelState.setCurrentTime(modelState.getCurrentTime().plus(elapsedTime));
      for (PortValue<?> input : inputs) {
        try {
          if (failureToRecord != null) {
            failureRef.compareAndSet(null, failureToRecord);
          } else if ("result".equals(input.getPortName())) {
            output.add((Integer) input.getValue());
          } else {
            throw new IllegalArgumentException("Unexpected input port " + input.getPortName());
          }
        } catch (Throwable failure) {
          failureRef.compareAndSet(null, failure);
        }
      }
    }

    @Override
    public DecimalSimTime timeAdvanceFunction() {
      return DecimalSimTime.infinity();
    }
  }

  private static final class PassiveLongAcceptor
      extends Acceptor<LongSimTime, TimeState<LongSimTime>> {
    private PassiveLongAcceptor(String modelId) {
      super(new TimeState<>(LongSimTime.create(0L)), modelId);
    }

    @Override
    public void internalStateTransitionFunction() {}

    @Override
    public void externalStateTransitionFunction(LongSimTime elapsedTime,
        List<PortValue<?>> inputs) {}

    @Override
    public LongSimTime timeAdvanceFunction() {
      return LongSimTime.create(Long.MAX_VALUE);
    }
  }

  private static final class LegacyHarness extends DevsModelTest<LongSimTime> {
    private LegacyHarness() {
      super("legacy-test-frame");
    }

    @Override
    protected Generator<LongSimTime> buildGenerator() {
      return new PowerOfTwoGenerator();
    }

    @Override
    protected Acceptor<LongSimTime, ?> buldAcceptor(AtomicReference<Throwable> failureRef) {
      return new PassiveLongAcceptor("legacy-frame-acceptor");
    }

    @Override
    protected SimulatorProvider<LongSimTime> buildDevsModelProvider(String simulationRunId) {
      return new DevsSimulatorProvider<>(new PassiveLongAcceptor("legacy-passive-child"));
    }

    @Override
    protected PDevsCouplings buildCouplings() {
      return PDevsCouplings.builder("legacy-test-frame")
          .addConnection(PowerOfTwoGenerator.MODEL_ID, "numbers", "legacy-frame-acceptor", "numbers")
          .addConnection(PowerOfTwoGenerator.MODEL_ID, "words", "legacy-frame-acceptor", "words")
          .build();
    }
  }
}
