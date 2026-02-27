package devs;

import devs.experimentalframe.Acceptor;
import devs.experimentalframe.Generator;
import devs.iso.DevsMessage;
import devs.iso.SimulationInit;
import devs.iso.time.SimTime;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DevsModelTest<T extends SimTime> {

    protected abstract Generator<T> buildGenerator();

    /**
     * Build the acceptor and give it a place to record failures occurring on actor threads.
     */
    protected abstract Acceptor<T, ?> buldAcceptor(AtomicReference<Throwable> failureRef);

    protected abstract SimulatorProvider<T> buildDevsModelProvider();
    protected abstract PDevsCouplings buildCouplings();

    protected void executeExperimentalFrame(T startTime, T endTime, String simulationId,
                                            long timeoutSeconds)
            throws InterruptedException {

        ActorTestKit testKit = ActorTestKit.create();
        AtomicReference<Throwable> failureRef = new AtomicReference<>();

        try {
            Generator<T> generator = buildGenerator();
            Acceptor<T, ?> acceptor = buldAcceptor(failureRef);

            List<SimulatorProvider<T>> simulatorProviders = new ArrayList<>();
            simulatorProviders.add(generator.getDevsSimulatorProvider());
            simulatorProviders.add(acceptor.getDevsSimulatorProvider());
            simulatorProviders.add(buildDevsModelProvider());

            PDevsCouplings couplings = buildCouplings();

            CoupledModelFactory<T> coupledModelFactory =
                    new CoupledModelFactory<>("vehicleImplTest", simulatorProviders, couplings);

            ActorRef<DevsMessage> testFrame =
                    testKit.spawn(coupledModelFactory.create(startTime), "vehicleImplTest");
            ActorRef<DevsMessage> rootCoordinator =
                    testKit.spawn(RootCoordinator.create(endTime, testFrame, "vehicleImplTest"), "root");

            rootCoordinator.tell(SimulationInit.<T>builder()
                    .eventTime(startTime)
                    .simulationId(simulationId)
                    .messageId("SimulationInit")
                    .senderId("TestActor")
                    .receiverId("root")
                    .build());

            TestProbe<DevsMessage> testProbe = testKit.createTestProbe();
            try {
                testProbe.expectTerminated(rootCoordinator, Duration.ofSeconds(timeoutSeconds));
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
        } finally {
            testKit.shutdownTestKit();
        }
    }
}