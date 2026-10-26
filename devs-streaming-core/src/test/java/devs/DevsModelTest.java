package devs;

import devs.experimentalframe.Acceptor;
import devs.experimentalframe.Generator;
import devs.iso.DevsMessage;
import devs.iso.SimulationInit;
import devs.iso.time.SimTime;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DevsModelTest<T extends SimTime> {

    private static final Logger log = LoggerFactory.getLogger(DevsModelTest.class);

    protected final String testFrameModelId;

    protected DevsModelTest(String testFrameModelId) {
        this.testFrameModelId = testFrameModelId;
    }

    protected abstract Generator<T> buildGenerator();

    /**
     * Build the acceptor and give it a place to record failures occurring on actor threads.
     */
    protected abstract Acceptor<T, ?> buldAcceptor(AtomicReference<Throwable> failureRef);

    protected abstract SimulatorProvider<T> buildDevsModelProvider(String simulationRunId);
    protected abstract PDevsCouplings buildCouplings();

    /**
     * Override this method to spawn additional actors in the experimental frame.
     */
    protected void spawnAdditionalActors(ActorTestKit testKit) {
        
    }

    /**
     * Override this method for cleanup of additional actors spawned in spawnAdditoinalActors()
     */
    protected CompletionStage<Void> stopAdditionalActors(ActorTestKit testKit, Duration timeout) {
        return CompletableFuture.completedFuture(null);
    }

    protected void executeExperimentalFrame(T startTime, T endTime, String simulationRunId,
                                            long timeoutSeconds)
            throws InterruptedException {

        ActorTestKit testKit = ActorTestKit.create();
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        try {
            spawnAdditionalActors(testKit);
            Generator<T> generator = buildGenerator();
            Acceptor<T, ?> acceptor = buldAcceptor(failureRef);

            List<SimulatorProvider<T>> simulatorProviders = new ArrayList<>();
            simulatorProviders.add(new DevsSimulatorProvider<>(generator));
            simulatorProviders.add(new DevsSimulatorProvider<>(acceptor));
            simulatorProviders.add(buildDevsModelProvider(simulationRunId));

            PDevsCouplings couplings = buildCouplings();

            CoupledModelFactory<T> coupledModelFactory =
                    new CoupledModelFactory<>(testFrameModelId, simulatorProviders, couplings);

            ActorRef<DevsMessage> testFrame =
                    testKit.spawn(coupledModelFactory.create(startTime), testFrameModelId);
            ActorRef<DevsMessage> rootCoordinator =
                    testKit.spawn(RootCoordinator.create(endTime, testFrame, testFrameModelId), "root");

            rootCoordinator.tell(SimulationInit.<T>builder()
                    .eventTime(startTime)
                    .simulationRunId(simulationRunId)
                    .messageId("SimulationInit")
                    .senderId("TestActor")
                    .receiverId("root")
                    .build());

            TestProbe<DevsMessage> testProbe = testKit.createTestProbe();
            Duration timeout = Duration.ofSeconds(timeoutSeconds);
            
            try {
                // Wait for the DEVS simulation itself to finish.
                testProbe.expectTerminated(rootCoordinator, timeout);

                // Give subclasses a chance to flush and stop resources they spawned.
                // This must happen before shutdownTestKit() terminates the actor system.
                stopAdditionalActors(testKit, timeout);
            } catch (AssertionError timeoutOrOther) {
                Throwable failure = failureRef.get();
                if (failure != null) {
                    if (failure instanceof AssertionError ae) throw ae;
                    throw new AssertionError("Failure occurred inside actor thread", failure);
                }
                throw timeoutOrOther;
            }

            Throwable failure = failureRef.get();
            if (failure != null) {
                if (failure instanceof AssertionError ae) throw ae;
                throw new AssertionError("Failure occurred inside actor thread", failure);
            }
        } catch (Throwable failure) {
            log.error("Experimental frame failed for simulation run " + simulationRunId, failure);
            throw failure;
        } finally {
            log.info("Starting testkit shutdown for simulation run " + simulationRunId);
            try {
                testKit.shutdownTestKit();
                log.info("Testkit shutdown completed for simulation run " + simulationRunId);
            } catch (Throwable shutdownFailure) {
                log.error("Testkit shutdown failed for simulation run " + simulationRunId, shutdownFailure);
                throw shutdownFailure;
            }
        }
    }
}