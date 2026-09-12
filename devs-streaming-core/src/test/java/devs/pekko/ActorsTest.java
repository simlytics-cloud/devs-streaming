package devs.pekko;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import devs.pekko.Actors.FailurePolicy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.Done;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.junit.jupiter.api.Test;

final class ActorsTest {

    private interface Command {}

    private static final class GetGeneration implements Command {
        private final ActorRef<Integer> replyTo;

        private GetGeneration(ActorRef<Integer> replyTo) {
            this.replyTo = replyTo;
        }
    }

    private enum Fail implements Command {
        INSTANCE
    }

    private enum Start implements Command {
        INSTANCE
    }

    @Test
    void missingPolicyUsesNormalStopBehavior() {
        ActorTestKit kit = testKit("stop-actor");
        try {
            AtomicInteger starts = new AtomicInteger();
            ActorRef<Command> actor =
                    kit.spawn(
                            Actors.withFailurePolicy(
                                    statefulBehavior(starts), FailurePolicy.STOP_ACTOR));
            TestProbe<Terminated> terminationProbe = kit.createTestProbe();

            actor.tell(Fail.INSTANCE);

            terminationProbe.expectTerminated(actor, Duration.ofSeconds(3));
            assertEquals(1, starts.get());
        } finally {
            kit.shutdownTestKit();
        }
    }

    @Test
    void restartPolicyRestartsFailedActor() {
        ActorTestKit kit = testKit("restart-actor");
        try {
            AtomicInteger starts = new AtomicInteger();
            ActorRef<Command> actor =
                    kit.spawn(
                            Actors.withFailurePolicy(
                                    statefulBehavior(starts), FailurePolicy.RESTART_ACTOR));
            TestProbe<Integer> generations = kit.createTestProbe();

            actor.tell(new GetGeneration(generations.getRef()));
            assertEquals(1, generations.receiveMessage());

            actor.tell(Fail.INSTANCE);
            actor.tell(new GetGeneration(generations.getRef()));

            assertEquals(2, generations.receiveMessage());
            assertEquals(2, starts.get());
        } finally {
            kit.shutdownTestKit();
        }
    }

    @Test
    void terminateSystemPolicyRunsCoordinatedShutdown() throws Exception {
        Config config =
                ConfigFactory.parseString(
                                "devs.actor-failure-policy=terminate-system\n"
                                        + "pekko.coordinated-shutdown.run-by-actor-system-terminate=on\n"
                                        + "pekko.coordinated-shutdown.exit-jvm=off\n")
                        .withFallback(ConfigFactory.load());

        Behavior<Command> guardian =
                Behaviors.setup(
                        context -> {
                            ActorRef<Command> child =
                                    Actors.spawn(
                                            context,
                                            Behaviors.receive(Command.class)
                                                    .onMessage(
                                                            Fail.class,
                                                            ignored -> {
                                                                throw new IllegalStateException(
                                                                        "fatal test failure");
                                                            })
                                                    .build(),
                                            "fatal-child");
                            return Behaviors.receive(Command.class)
                                    .onMessage(
                                            Start.class,
                                            ignored -> {
                                                child.tell(Fail.INSTANCE);
                                                return Behaviors.same();
                                            })
                                    .build();
                        });

        ActorSystem<Command> system =
                ActorSystem.create(guardian, "terminate-policy-test", config);
        AtomicBoolean coordinatedShutdownRan = new AtomicBoolean();
        CoordinatedShutdown.get(system)
                .addTask(
                        CoordinatedShutdown.PhaseBeforeServiceUnbind(),
                        "record-test-shutdown",
                        () -> {
                            coordinatedShutdownRan.set(true);
                            return CompletableFuture.completedFuture(Done.getInstance());
                        });

        system.tell(Start.INSTANCE);
        system.getWhenTerminated().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(coordinatedShutdownRan.get());
    }

    @Test
    void parsesConfigurationAndDefaultsToStop() {
        assertEquals(
                FailurePolicy.STOP_ACTOR,
                FailurePolicy.fromConfig(ConfigFactory.empty()));
        assertEquals(
                FailurePolicy.STOP_ACTOR,
                FailurePolicy.fromConfig(configWithPolicy("stop-actor")));
        assertEquals(
                FailurePolicy.RESTART_ACTOR,
                FailurePolicy.fromConfig(configWithPolicy(" RESTART-ACTOR ")));
        assertEquals(
                FailurePolicy.TERMINATE_SYSTEM,
                FailurePolicy.fromConfig(configWithPolicy("terminate-system")));
        assertThrows(
                IllegalArgumentException.class,
                () -> FailurePolicy.fromConfig(configWithPolicy("resume-actor")));
    }

    @Test
    void spawnUtilityPreservesGenericMessageType() {
        ActorTestKit kit = testKit("stop-actor");
        try {
            TestProbe<ActorRef<Command>> spawnedRef = kit.createTestProbe();
            TestProbe<Integer> replyProbe = kit.createTestProbe();

            Behavior<Command> guardian =
                    Behaviors.setup(
                            context -> {
                                // This assignment is the compile-time generic type-safety check.
                                ActorRef<Command> child =
                                        Actors.spawn(
                                                context,
                                                statefulBehavior(new AtomicInteger()),
                                                "typed-child");
                                spawnedRef.getRef().tell(child);
                                return Behaviors.empty();
                            });

            kit.spawn(guardian);
            ActorRef<Command> child = spawnedRef.receiveMessage();
            child.tell(new GetGeneration(replyProbe.getRef()));

            assertEquals(1, replyProbe.receiveMessage());
        } finally {
            kit.shutdownTestKit();
        }
    }

    private static Behavior<Command> statefulBehavior(AtomicInteger starts) {
        return Behaviors.setup(
                context -> {
                    int generation = starts.incrementAndGet();
                    return Behaviors.receive(Command.class)
                            .onMessage(
                                    GetGeneration.class,
                                    command -> {
                                        command.replyTo.tell(generation);
                                        return Behaviors.same();
                                    })
                            .onMessage(
                                    Fail.class,
                                    ignored -> {
                                        throw new IllegalStateException("boom");
                                    })
                            .build();
                });
    }

    private static Config configWithPolicy(String policy) {
        return ConfigFactory.parseString(
                "devs.actor-failure-policy=\"" + policy + "\"");
    }

    private static ActorTestKit testKit(String policy) {
        Config config =
                ConfigFactory.parseString(
                                "devs.actor-failure-policy=\""
                                        + policy
                                        + "\"\n"
                                        + "pekko.coordinated-shutdown.exit-jvm=off\n")
                        .withFallback(ConfigFactory.load());
        return ActorTestKit.create(config);
    }
}
