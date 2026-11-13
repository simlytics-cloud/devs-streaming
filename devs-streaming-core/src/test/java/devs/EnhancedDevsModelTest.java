package devs;

import devs.experimentalframe.Acceptor;
import devs.experimentalframe.Generator;
import devs.iso.DevsMessage;
import devs.iso.SimulationInit;
import devs.iso.time.DecimalSimTime;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.time.DecimalTimeMapping;
import devs.time.EnhancedClockContext;
import devs.time.DecimalTimeCodec;
import devs.time.TimeSemantics;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Base class for tests that execute an enhanced coupled model inside a canonical-time frame.
 * Generator and acceptor timestamps are interpreted as decimal seconds; the supplied coupled
 * factory must use the same clock context as the per-run frame.
 */
public abstract class EnhancedDevsModelTest {
  private static final DecimalTimeMapping<DecimalSimTime> CANONICAL_MAPPING =
      new DecimalTimeMapping<>(new DecimalTimeCodec(),
          TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO));

  protected final String testFrameModelId;

  protected EnhancedDevsModelTest(String testFrameModelId) {
    this.testFrameModelId = Objects.requireNonNull(testFrameModelId);
    if (testFrameModelId.isBlank()) {
      throw new IllegalArgumentException("Test frame model identifier is required");
    }
  }

  protected abstract Generator<DecimalSimTime> buildGenerator();

  /** Build the acceptor and give it a place to record failures occurring on actor threads. */
  protected abstract Acceptor<DecimalSimTime, ?> buildAcceptor(
      AtomicReference<Throwable> failureRef);

  /** Build the enhanced coupled model that runs as a coordinator child of the frame. */
  protected abstract CoupledModelFactory<DecimalSimTime> buildCoupledModel(
      String simulationRunId, EnhancedClockContext clock);

  /** Build the couplings between the frame's generator, acceptor, and coupled model. */
  protected abstract PDevsCouplings buildCouplings();

  /** Override to spawn actors used by this test; the default does nothing. */
  protected void spawnAdditionalActors(ActorTestKit testKit) {}

  /** Override to stop actors created by {@link #spawnAdditionalActors(ActorTestKit)}. */
  protected CompletionStage<Void> stopAdditionalActors(ActorTestKit testKit, Duration timeout) {
    return java.util.concurrent.CompletableFuture.completedFuture(null);
  }

  /** Override to assert that all expected outputs were observed after the simulation completes. */
  protected void verifyExpectedOutputs() {}

  /**
   * Executes one enhanced experimental frame. The same canonical start and run clock are passed
   * through the nested coordinator, frame coordinator, and root coordinator.
   */
  protected void executeExperimentalFrame(DecimalSimTime start, DecimalSimTime end,
      String runId, long timeoutSeconds) throws InterruptedException {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(runId, "runId");
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be positive");
    }
    if (start.isInfinite() || end.isInfinite() || start.compareTo(end) > 0) {
      throw new IllegalArgumentException("The enhanced frame requires finite start <= end times");
    }

    Duration timeout = Duration.ofSeconds(timeoutSeconds);
    AtomicReference<Throwable> failureRef = new AtomicReference<>();
    ActorTestKit testKit = null;
    Throwable failure = null;
    boolean interrupted = false;

    try {
      EnhancedClockContext clock = new EnhancedClockContext(runId, start.value());
      testKit = ActorTestKit.create();
      spawnAdditionalActors(testKit);

      Generator<DecimalSimTime> generator = Objects.requireNonNull(buildGenerator(),
          "buildGenerator returned null");
      Acceptor<DecimalSimTime, ?> acceptor = Objects.requireNonNull(
          buildAcceptor(failureRef), "buildAcceptor returned null");
      CoupledModelFactory<DecimalSimTime> coupledModel = Objects.requireNonNull(
          buildCoupledModel(runId, clock), "buildCoupledModel returned null");
      validateCoordinator(coupledModel, clock);
      coupledModel.create(start);

      List<ChildBinding<?>> bindings = List.of(
          ChildBinding.nativeProvider(new DevsSimulatorProvider<>(generator),
              DecimalSimTime.class, CANONICAL_MAPPING),
          ChildBinding.nativeProvider(new DevsSimulatorProvider<>(acceptor),
              DecimalSimTime.class, CANONICAL_MAPPING),
          ChildBinding.coordinator(coupledModel));
      CoupledModelFactory<DecimalSimTime> frameFactory = CoupledModelFactory.enhanced(
          testFrameModelId, bindings, Objects.requireNonNull(buildCouplings(),
              "buildCouplings returned null"), new CoordinatorExecutionConfig(clock));
      var frameBehavior = frameFactory.create(start);
      ActorRef<DevsMessage> testFrame = testKit.spawn(frameBehavior, testFrameModelId);
      ActorRef<DevsMessage> root = testKit.spawn(
          RootCoordinator.enhanced(end, testFrame, testFrameModelId, clock), "root");
      root.tell(SimulationInit.<DecimalSimTime>builder()
          .eventTime(start)
          .simulationRunId(runId)
          .messageId("SimulationInit")
          .senderId("TestActor")
          .receiverId("root")
          .build());

      TestProbe<DevsMessage> probe = testKit.createTestProbe();
      try {
        probe.expectTerminated(root, timeout);
      } catch (AssertionError timeoutOrFailure) {
        Throwable actorFailure = failureRef.get();
        if (actorFailure != null) {
          throw actorFailure(actorFailure, timeoutOrFailure);
        }
        throw timeoutOrFailure;
      }
      throwActorFailure(failureRef.get());
      verifyExpectedOutputs();
    } catch (Throwable runFailure) {
      failure = runFailure;
    } finally {
      if (testKit != null) {
        try {
          CompletionStage<Void> stop = Objects.requireNonNull(
              stopAdditionalActors(testKit, timeout), "stopAdditionalActors returned null");
          stop.toCompletableFuture().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interruption) {
          interrupted = true;
          failure = appendFailure(failure, interruption);
        } catch (ExecutionException stopFailure) {
          failure = appendFailure(failure, stopFailure.getCause() == null
              ? stopFailure : stopFailure.getCause());
        } catch (TimeoutException stopTimeout) {
          failure = appendFailure(failure,
              new AssertionError("Timed out stopping additional actors", stopTimeout));
        } catch (Throwable stopFailure) {
          failure = appendFailure(failure, stopFailure);
        }
        try {
          testKit.shutdownTestKit();
        } catch (Throwable shutdownFailure) {
          failure = appendFailure(failure, shutdownFailure);
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    if (failure != null) {
      rethrow(failure);
    }
  }

  private static void validateCoordinator(CoupledModelFactory<DecimalSimTime> factory,
      EnhancedClockContext clock) {
    if (!factory.isEnhanced()) {
      throw new IllegalArgumentException("The coupled model factory must use enhanced coordination");
    }
    CoordinatorExecutionConfig config = factory.executionConfig();
    if (config == null || !clock.equals(config.clock())) {
      throw new IllegalArgumentException("The coupled model must use the frame's shared clock");
    }
  }

  private static void throwActorFailure(Throwable actorFailure) {
    if (actorFailure != null) {
      throw actorFailure(actorFailure, null);
    }
  }

  private static AssertionError actorFailure(Throwable actorFailure, Throwable testFailure) {
    AssertionError reported = actorFailure instanceof AssertionError assertionError
        ? assertionError
        : new AssertionError("Failure occurred inside actor thread", actorFailure);
    if (testFailure != null && testFailure != reported && testFailure != actorFailure) {
      reported.addSuppressed(testFailure);
    }
    return reported;
  }

  private static Throwable appendFailure(Throwable primary, Throwable cleanup) {
    if (primary == null) {
      return cleanup;
    }
    if (primary != cleanup) {
      primary.addSuppressed(cleanup);
    }
    return primary;
  }

  private static void rethrow(Throwable failure) throws InterruptedException {
    if (failure instanceof InterruptedException interruption) {
      throw interruption;
    }
    if (failure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (failure instanceof Error error) {
      throw error;
    }
    throw new AssertionError("Experimental frame failed", failure);
  }
}
