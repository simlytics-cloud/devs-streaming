// File: src/main/java/devs/pekko/Actors.java
package devs.pekko;

import com.typesafe.config.Config;
import java.util.Locale;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.Signal;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.TypedActorContext;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Framework-wide entry point for spawning actors with the configured failure policy.
 *
 * <p>Application actors should use this class instead of calling {@link ActorContext#spawn}
 * directly. Guardian behaviors can be wrapped with {@link #withFailurePolicy(ActorSystem,
 * Behavior)} before they are passed to an ActorSystem when necessary.
 */
public final class Actors {

    public static final String FAILURE_POLICY_CONFIG_PATH = "devs.actor-failure-policy";

    private Actors() {
        // Utility class.
    }

    /** Supported framework-wide handling of exceptions escaping actor behavior. */
    public enum FailurePolicy {
        STOP_ACTOR("stop-actor"),
        RESTART_ACTOR("restart-actor"),
        TERMINATE_SYSTEM("terminate-system");

        private final String configValue;

        FailurePolicy(String configValue) {
            this.configValue = configValue;
        }

        public String configValue() {
            return configValue;
        }

        public static FailurePolicy fromConfig(Config config) {
            Objects.requireNonNull(config, "config");
            if (!config.hasPath(FAILURE_POLICY_CONFIG_PATH)) {
                return STOP_ACTOR;
            }
            return parse(config.getString(FAILURE_POLICY_CONFIG_PATH));
        }

        public static FailurePolicy parse(String value) {
            Objects.requireNonNull(value, "value");
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (FailurePolicy policy : values()) {
                if (policy.configValue.equals(normalized)) {
                    return policy;
                }
            }
            throw new IllegalArgumentException(
                    "Unsupported "
                            + FAILURE_POLICY_CONFIG_PATH
                            + " value '"
                            + value
                            + "'. Expected one of: stop-actor, restart-actor, terminate-system");
        }
    }

    /** Spawns a named child and automatically applies the ActorSystem's configured policy. */
    public static <T> ActorRef<T> spawn(
            ActorContext<?> context, Behavior<T> behavior, String name) {
        Objects.requireNonNull(context, "context");
        return context.spawn(withFailurePolicy(context.getSystem(), behavior), name);
    }

    /**
     * Spawns a named child with Pekko Props and automatically applies the configured policy.
     */
    public static <T> ActorRef<T> spawn(
            ActorContext<?> context, Behavior<T> behavior, String name, Props props) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(props, "props");
        return context.spawn(withFailurePolicy(context.getSystem(), behavior), name, props);
    }

    /** Spawns an anonymous child and automatically applies the configured policy. */
    public static <T> ActorRef<T> spawnAnonymous(
            ActorContext<?> context, Behavior<T> behavior) {
        Objects.requireNonNull(context, "context");
        return context.spawnAnonymous(withFailurePolicy(context.getSystem(), behavior));
    }

    /**
     * Spawns an anonymous child with Pekko Props and automatically applies the configured policy.
     */
    public static <T> ActorRef<T> spawnAnonymous(
            ActorContext<?> context, Behavior<T> behavior, Props props) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(props, "props");
        return context.spawnAnonymous(withFailurePolicy(context.getSystem(), behavior), props);
    }

    /** Applies the policy configured for the supplied ActorSystem. */
    public static <T> Behavior<T> withFailurePolicy(
            ActorSystem<?> system, Behavior<T> behavior) {
        Objects.requireNonNull(system, "system");
        return withFailurePolicy(
                behavior, FailurePolicy.fromConfig(system.settings().config()));
    }

    /** Applies the policy in the supplied configuration. */
    public static <T> Behavior<T> withFailurePolicy(Behavior<T> behavior, Config config) {
        return withFailurePolicy(behavior, FailurePolicy.fromConfig(config));
    }

    /** Applies an explicitly selected policy, primarily for framework integration and tests. */
    public static <T> Behavior<T> withFailurePolicy(
            Behavior<T> behavior, FailurePolicy policy) {
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(policy, "policy");

        switch (policy) {
            case STOP_ACTOR:
                // No wrapper preserves Pekko Typed's normal unhandled-exception behavior.
                return behavior;
            case RESTART_ACTOR:
                return Behaviors.supervise(behavior).onFailure(SupervisorStrategy.restart());
            case TERMINATE_SYSTEM:
                return Behaviors.intercept(TerminateSystemInterceptor::new, behavior);
            default:
                throw new AssertionError("Unhandled actor failure policy: " + policy);
        }
    }

    /**
     * Intercepts exceptions escaping actor startup, message processing, or signal processing.
     * Messages handled normally by an actor—including domain-error messages—never enter this
     * failure path.
     */
    private static final class TerminateSystemInterceptor<T>
            extends BehaviorInterceptor<T, T> {

        @SuppressWarnings("unchecked")
        private TerminateSystemInterceptor() {
            // Typed behaviors are checked by Behavior/ActorRef generics; Java erases T at runtime.
            super((Class<T>) (Class<?>) Object.class);
        }

        @Override
        public Behavior<T> aroundStart(
                TypedActorContext<T> context,
                BehaviorInterceptor.PreStartTarget<T> target) {
            try {
                return target.start(context);
            } catch (Exception exception) {
                terminate(context, exception, "actor startup");
                return rethrow(exception);
            }
        }

        @Override
        public Behavior<T> aroundReceive(
                TypedActorContext<T> context,
                T message,
                BehaviorInterceptor.ReceiveTarget<T> target) {
            try {
                return target.apply(context, message);
            } catch (Exception exception) {
                terminate(context, exception, "message processing");
                return rethrow(exception);
            }
        }

        @Override
        public Behavior<T> aroundSignal(
                TypedActorContext<T> context,
                Signal signal,
                BehaviorInterceptor.SignalTarget<T> target) {
            try {
                return target.apply(context, signal);
            } catch (Exception exception) {
                terminate(
                        context,
                        exception,
                        "signal processing (" + signal.getClass().getSimpleName() + ")");
                return rethrow(exception);
            }
        }

        private void terminate(
                TypedActorContext<T> context, Exception exception, String operation) {
            ActorContext<T> javaContext = context.asJava();

            // Issue the error log before starting shutdown so the original exception and actor
            // path are retained in the application logs.
            javaContext.getLog()
                    .error(
                            "Unhandled exception during {} in actor {}; terminating ActorSystem {}",
                            operation,
                            javaContext.getSelf().path(),
                            javaContext.getSystem().name(),
                            exception);

            // With run-by-actor-system-terminate enabled, this invokes Coordinated Shutdown.
            // exit-jvm and exit-code are configured in reference.conf/application.conf.
            javaContext.getSystem().terminate();
        }

        /** Rethrows the original exception despite this Pekko version exposing no checked throws. */
        @SuppressWarnings("unchecked")
        private static <R, E extends Throwable> R rethrow(Throwable throwable) throws E {
            throw (E) throwable;
        }
    }
}