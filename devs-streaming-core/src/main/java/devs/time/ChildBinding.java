package devs.time;

import devs.CoupledModelFactory;
import devs.DevsSimulatorProvider;
import devs.SimulatorProvider;
import devs.iso.DevsMessage;
import devs.iso.time.*;

import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import scala.annotation.internal.Child;

/** A typed native boundary or an explicitly enhanced coordinator child. */
public final class ChildBinding<N extends SimTime> {
  private final String modelIdentifier;
  private final SimulatorProvider<N> provider;
  private final ActorRef<DevsMessage> actor;
  private final Class<N> nativeType;
  private final TimeMapping<N> mapping;
  private final EnhancedClockContext clock;

  private ChildBinding(String modelIdentifier, SimulatorProvider<N> provider,
      ActorRef<DevsMessage> actor, Class<N> nativeType, TimeMapping<N> mapping,
      EnhancedClockContext clock) {
    this.modelIdentifier = Objects.requireNonNull(modelIdentifier);
    if (modelIdentifier.isBlank()) {
      throw new IllegalArgumentException("Child model identifier is required");
    }
    this.provider = provider;
    this.actor = actor;
    this.nativeType = Objects.requireNonNull(nativeType);
    this.mapping = Objects.requireNonNull(mapping);
    this.clock = clock;
  }

  /**
   * Binds an atomic native-time provider using an explicit timestamp type and mapping.
   * @throws IllegalArgumentException if the provider is a coupled factory or its identifier is blank
   * @throws NullPointerException if the provider, its identifier, type, or mapping is null
   */
  public static <N extends SimTime> ChildBinding<N> nativeProvider(
      SimulatorProvider<N> provider, Class<N> nativeType, TimeMapping<N> mapping) {
    Objects.requireNonNull(provider);
    if (provider instanceof CoupledModelFactory<?>) {
      throw new IllegalArgumentException("Coupled factories require an enhanced coordinator binding");
    }
    return new ChildBinding<>(provider.getModelIdentifier(), provider, null, nativeType, mapping, null);
  }

  /**
   * Binds an existing simulator provider with default LongSimTime settings and origin offset of ZERO
   * @param provider A DEVS Simulator provider
   * @param secondsPerModelTimeUnit The seconds per time unit of the model wrapped by the provider
   * @return the ChildBinding<LongSimTime>
   */
  public static ChildBinding<LongSimTime> defaultLongProvider(SimulatorProvider<LongSimTime> provider, 
                                                              double secondsPerModelTimeUnit) {
    return nativeProvider(provider, LongSimTime.class, new TimeMapping<>(new LongTimeCodec(), 
            TimeSemantics.of(Rational.of(secondsPerModelTimeUnit), Rational.ZERO)));
  }

  /**
   * Binds an existing simulator provider with default DoubleSimTime settings and origin offset of ZERO
   * @param provider A DEVS Simulator provider
   * @param secondsPerModelTimeUnit The seconds per time unit of the model wrapped by the provider
   * @return the ChildBinding<DoubleSimTime>
   */
  public static ChildBinding<DoubleSimTime> defaultDoubleProvider(SimulatorProvider<DoubleSimTime> provider,
                                                              double secondsPerModelTimeUnit) {
    return nativeProvider(provider, DoubleSimTime.class, new TimeMapping<>(new DoubleTimeCodec(),
            TimeSemantics.of(Rational.of(secondsPerModelTimeUnit), Rational.ZERO)));
  }

  /**
   * Binds an existing simulator provider with default DecimalSimTime settings and origin offset of ZERO
   * @param provider A DEVS Simulator provider
   * @param secondsPerModelTimeUnit The seconds per time unit of the model wrapped by the provider
   * @return the ChildBinding<DecimalSimTime>
   */
  public static ChildBinding<DecimalSimTime> defaultDecimalProvider(SimulatorProvider<DecimalSimTime> provider,
                                                                  double secondsPerModelTimeUnit) {
    return nativeProvider(provider, DecimalSimTime.class, new TimeMapping<>(new DecimalTimeCodec(),
            TimeSemantics.of(Rational.of(secondsPerModelTimeUnit), Rational.ZERO)));
  }
  

  /**
   * Binds an existing native-time actor; the caller supplies its timestamp type and mapping.
   * @throws IllegalArgumentException if the model identifier is blank
   * @throws NullPointerException if any argument is null
   */
  public static <N extends SimTime> ChildBinding<N> nativeActor(String modelIdentifier,
      ActorRef<DevsMessage> actor, Class<N> nativeType, TimeMapping<N> mapping) {
    return new ChildBinding<>(modelIdentifier, null, Objects.requireNonNull(actor), nativeType,
        mapping, null);
  }

  /**
   * Binds an existing simulator with default LongSimTime settings and origin offset of ZERO
   * @param modelIdentifier the model identifier for the bound model
   * @param actor the PDEVSSimulator running the model
   * @param secondsPerModelTimeUnit the number of seconds in the time unit used by the model
   * @return The ChildBinding<LongSimeTime>
   */
  public static ChildBinding<LongSimTime> defaultLongBinding(String modelIdentifier, ActorRef<DevsMessage> actor, 
                                                             double secondsPerModelTimeUnit) {
    return  ChildBinding.nativeActor(modelIdentifier, Objects.requireNonNull(actor), LongSimTime.class,
            new TimeMapping<>(new LongTimeCodec(), TimeSemantics.of(Rational.of(secondsPerModelTimeUnit), Rational.ZERO)));
  }


  /**
   * Binds an existing simulator with default DoubleSimTime settings and origin offset of ZERO
   * @param modelIdentifier the model identifier for the bound model
   * @param actor the PDEVSSimulator running the model
   * @param secondsPerModelTimeUnit the number of seconds in the time unit used by the model
   * @return The ChildBinding<DoubleSimTime>
   */  
  public static ChildBinding<DoubleSimTime> defaultDoubleBinding(String modelIdentifier, ActorRef<DevsMessage> actor, 
                                                                 double secondsPerModelTimeUnit) {
    return ChildBinding.nativeActor(modelIdentifier, Objects.requireNonNull(actor), DoubleSimTime.class,
            new TimeMapping<>(new DoubleTimeCodec(), TimeSemantics.of(Rational.of(secondsPerModelTimeUnit), Rational.ZERO)));
  }

  /**
   * Binds an existing simulator with default DecimalSimTime settings and origin offset of ZERO
   * @param modelIdentifier the model identifier for the bound model
   * @param actor the PDEVSSimulator running the model
   * @param secondsPerModelTimeUnit the number of seconds in the time unit used by the model
   * @return The ChildBinding<DecimalSimTime>
   */
  public static ChildBinding<DecimalSimTime> defaultDecimalBinding(String modelIdentifier, ActorRef<DevsMessage> actor,
                                                                   double secondsPerModelTimeUnit) {
    return ChildBinding.nativeActor(modelIdentifier, Objects.requireNonNull(actor), DecimalSimTime.class,
            new TimeMapping<>(new DecimalTimeCodec(), TimeSemantics.of(Rational.of(secondsPerModelTimeUnit), Rational.ZERO)));
  }

  /**
   * Binds an enhanced coupled factory using identity mapping of canonical seconds.
   * Its clock must later match the enclosing hierarchy's clock.
   * @throws IllegalArgumentException if the factory is legacy or its identifier is blank
   */
  public static ChildBinding<QuantizedSimTime> coordinator(
      CoupledModelFactory<QuantizedSimTime> provider) {
    Objects.requireNonNull(provider);
    if (!provider.isEnhanced()) {
      throw new IllegalArgumentException("Legacy coordinator children are not supported");
    }
    return new ChildBinding<>(provider.getModelIdentifier(), provider, null, QuantizedSimTime.class,
        canonicalMapping(), provider.executionConfig().clock());
  }

  /**
   * Binds an existing enhanced coordinator actor in canonical seconds with an explicit clock.
   * @throws IllegalArgumentException if the model identifier is blank
   * @throws NullPointerException if any argument is null
   */
  public static ChildBinding<QuantizedSimTime> coordinatorActor(String modelIdentifier,
      ActorRef<DevsMessage> actor, EnhancedClockContext clock) {
    return new ChildBinding<>(modelIdentifier, null, Objects.requireNonNull(actor),
        QuantizedSimTime.class, canonicalMapping(), Objects.requireNonNull(clock));
  }

  /** Returns the identity boundary for enhanced children already using exact canonical seconds. */
  private static TimeMapping<QuantizedSimTime> canonicalMapping() {
    NativeTimeCodec<QuantizedSimTime> codec = new NativeTimeCodec<>() {
      /** Returns the canonical timestamp unchanged, including infinity. */
      @Override
      public QuantizedSimTime decode(QuantizedSimTime value) {
        return value;
      }

      /** Returns the canonical timestamp unchanged, including infinity. */
      @Override
      public QuantizedSimTime encode(QuantizedSimTime value) {
        return value;
      }

      /** Returns lower unchanged; this private identity boundary has no declared lattice. */
      @Override
      public QuantizedSimTime nextRepresentable(QuantizedSimTime lower, Rational origin,
          Rational quantum) {
        return lower;
      }
    };
    return new TimeMapping<>(codec, TimeSemantics.of(Rational.ONE, Rational.ZERO));
  }

  /** Returns the nonblank child model identifier. */
  public String modelIdentifier() {
    return modelIdentifier;
  }

  /** Returns whether this binding declares an enhanced coordinator clock. */
  public boolean isCoordinator() {
    return clock != null;
  }

  /** Returns the enhanced coordinator's clock, or null for a native child. */
  public EnhancedClockContext clock() {
    return clock;
  }

  /**
   * Checks both clock identity and reference origin for enhanced coordinator children.
   * Native children require no shared-clock equality check.
   * @throws IllegalArgumentException if an enhanced child's clock differs from expected
   */
  public void validateClock(EnhancedClockContext expected) {
    if (isCoordinator() && !clock.equals(expected)) {
      throw new IllegalArgumentException("Coordinator child clock does not match the hierarchy");
    }
  }

  /**
   * Validates exact encoding of the canonical-second initial time, then returns the
   * existing actor or asks the provider to create one with the native initial timestamp.
   * Initial-time validation also occurs when an actor already exists.
   * @throws ArithmeticException if the initial timestamp cannot be mapped exactly
   */
  public ActorRef<DevsMessage> spawn(ActorContext<DevsMessage> context,
      QuantizedSimTime initialTime) {
    N nativeInitialTime = exact(initialTime);
    return actor != null ? actor : provider.provideSimulator(context, nativeInitialTime);
  }

  /**
   * Returns a binding with the supplied nonnull actor and all other metadata unchanged.
   * This does not spawn or initialize the actor.
   */
  public ChildBinding<N> withActor(ActorRef<DevsMessage> actor) {
    return new ChildBinding<>(modelIdentifier, provider, Objects.requireNonNull(actor), nativeType,
        mapping, clock);
  }

  /**
   * Converts an exact native timestamp to canonical seconds, preserving infinity.
   * @throws ClassCastException if value does not have the declared native timestamp type
   * @throws ArithmeticException if lattice or round-trip validation fails
   */
  public QuantizedSimTime toCanonical(SimTime value) {
    return mapping.toCanonical(nativeType.cast(value));
  }

  /**
   * Maps canonical seconds to the native timestamp exactly, preserving infinity.
   * @throws ArithmeticException if exact encoding or lattice validation fails
   */
  public N exact(QuantizedSimTime value) {
    return mapping.exact(value);
  }

  /**
   * Automatically adapts a canonical-second source time upward at the native boundary.
   * Returns a finite native timestamp whose canonical value is not earlier than source;
   * error-budget observation is the schedule's responsibility.
   */
  public N adapt(QuantizedSimTime source) {
    if (source.isInfinite()) {
      throw new IllegalArgumentException("Cannot deliver at logical infinity");
    }
    N nativeTime = isCoordinator() ? mapping.exact(source) : mapping.nextRepresentable(source);
    QuantizedSimTime effective = mapping.toCanonical(nativeTime);
    if (effective.isInfinite() || effective.compareTo(source) < 0) {
      throw new IllegalArgumentException("Delivery violates causality or finite range");
    }
    return nativeTime;
  }
}