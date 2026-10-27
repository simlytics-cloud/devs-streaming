package devs.time;

import devs.CoupledModelFactory;
import devs.DevsSimulatorProvider;
import devs.SimulatorProvider;
import devs.iso.DevsMessage;
import devs.iso.time.*;

import java.math.BigDecimal;
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
  private final DecimalTimeMapping<N> mapping;
  private final EnhancedClockContext clock;

  private ChildBinding(String modelIdentifier, SimulatorProvider<N> provider,
      ActorRef<DevsMessage> actor, Class<N> nativeType, DecimalTimeMapping<N> mapping,
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
      SimulatorProvider<N> provider, Class<N> nativeType, DecimalTimeMapping<N> mapping) {
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
    return nativeProvider(provider, LongSimTime.class, new DecimalTimeMapping<>(new LongTimeCodec(),
            TimeSemantics.of(BigDecimal.valueOf(secondsPerModelTimeUnit), BigDecimal.ZERO)));
  }

  /**
   * Binds an existing simulator provider with default DoubleSimTime settings and origin offset of ZERO
   * @param provider A DEVS Simulator provider
   * @param secondsPerModelTimeUnit The seconds per time unit of the model wrapped by the provider
   * @return the ChildBinding<DoubleSimTime>
   */
  public static ChildBinding<DoubleSimTime> defaultDoubleProvider(SimulatorProvider<DoubleSimTime> provider,
                                                              double secondsPerModelTimeUnit) {
    return defaultDoubleProvider(provider, BigDecimal.valueOf(secondsPerModelTimeUnit));
  }

  /**
   * Binds a continuous double-native provider with an explicitly declared decimal approximation
   * of its seconds-per-unit scale. No native granularity is implied.
   */
  public static ChildBinding<DoubleSimTime> defaultDoubleProvider(
      SimulatorProvider<DoubleSimTime> provider, BigDecimal secondsPerModelTimeUnit) {
    return nativeProvider(provider, DoubleSimTime.class, DecimalTimeMapping.approximateDouble(
        TimeSemantics.of(secondsPerModelTimeUnit, BigDecimal.ZERO), BigDecimal.ZERO));
  }

  /**
   * Binds an existing simulator provider with default DecimalSimTime settings and origin offset of ZERO
   * @param provider A DEVS Simulator provider
   * @param secondsPerModelTimeUnit The seconds per time unit of the model wrapped by the provider
   * @return the ChildBinding<DecimalSimTime>
   */
  public static ChildBinding<DecimalSimTime> defaultDecimalProvider(SimulatorProvider<DecimalSimTime> provider,
                                                                  double secondsPerModelTimeUnit) {
    return nativeProvider(provider, DecimalSimTime.class, new DecimalTimeMapping<>(new DecimalTimeCodec(),
            TimeSemantics.of(BigDecimal.valueOf(secondsPerModelTimeUnit), BigDecimal.ZERO)));
  }
  

  /**
   * Binds an existing native-time actor; the caller supplies its timestamp type and mapping.
   * @throws IllegalArgumentException if the model identifier is blank
   * @throws NullPointerException if any argument is null
   */


  public static <N extends SimTime> ChildBinding<N> nativeActor(String modelIdentifier,
      ActorRef<DevsMessage> actor, Class<N> nativeType, DecimalTimeMapping<N> mapping) {
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
            new DecimalTimeMapping<>(new LongTimeCodec(),
                TimeSemantics.of(BigDecimal.valueOf(secondsPerModelTimeUnit), BigDecimal.ZERO)));
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
    return defaultDoubleBinding(modelIdentifier, actor, BigDecimal.valueOf(secondsPerModelTimeUnit));
  }

  /**
   * Binds a continuous double-native actor with an explicitly declared decimal approximation of
   * its seconds-per-unit scale. No native granularity is implied.
   */
  public static ChildBinding<DoubleSimTime> defaultDoubleBinding(String modelIdentifier,
      ActorRef<DevsMessage> actor, BigDecimal secondsPerModelTimeUnit) {
    return ChildBinding.nativeActor(modelIdentifier, Objects.requireNonNull(actor), DoubleSimTime.class,
        DecimalTimeMapping.approximateDouble(
            TimeSemantics.of(secondsPerModelTimeUnit, BigDecimal.ZERO), BigDecimal.ZERO));
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
            new DecimalTimeMapping<>(new DecimalTimeCodec(),
                TimeSemantics.of(BigDecimal.valueOf(secondsPerModelTimeUnit), BigDecimal.ZERO)));
  }

  /**
   * Binds an enhanced coupled factory using identity mapping of canonical seconds.
   * Its clock must later match the enclosing hierarchy's clock.
   * @throws IllegalArgumentException if the factory is legacy or its identifier is blank
   */
  public static ChildBinding<DecimalSimTime> coordinator(
      CoupledModelFactory<DecimalSimTime> provider) {
    Objects.requireNonNull(provider);
    if (!provider.isEnhanced()) {
      throw new IllegalArgumentException("Legacy coordinator children are not supported");
    }
    return new ChildBinding<>(provider.getModelIdentifier(), provider, null, DecimalSimTime.class,
        canonicalMapping(), provider.executionConfig().clock());
  }

  /**
   * Binds an existing enhanced coordinator actor in canonical seconds with an explicit clock.
   * @throws IllegalArgumentException if the model identifier is blank
   * @throws NullPointerException if any argument is null
   */
  public static ChildBinding<DecimalSimTime> coordinatorActor(String modelIdentifier,
      ActorRef<DevsMessage> actor, EnhancedClockContext clock) {
    return new ChildBinding<>(modelIdentifier, null, Objects.requireNonNull(actor),
        DecimalSimTime.class, canonicalMapping(), Objects.requireNonNull(clock));
  }

  /** Returns the identity boundary for decimal canonical children. */
  private static DecimalTimeMapping<DecimalSimTime> canonicalMapping() {
    NativeTimeCodec<DecimalSimTime> codec = new DecimalTimeCodec();
    return new DecimalTimeMapping<>(codec, TimeSemantics.of(java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO));
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
      DecimalSimTime initialTime) {
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
  public DecimalSimTime toCanonical(SimTime value) {
    return mapping.toCanonical(nativeType.cast(value));
  }

  /**
   * Maps canonical seconds to the native timestamp, preserving infinity. Exact mappings require
   * equality; explicitly approximate double mappings require the configured bounded error.
   * @throws ArithmeticException if encoding, error-bound, or lattice validation fails
   */
  public N exact(DecimalSimTime value) {
    return mapping.exact(value);
  }

  /**
   * Converts a canonical-second delivery time at the native boundary. Native long mappings advance
   * finite deliveries to the next integer timestamp on the declared grid when needed; enhanced
   * coordinator children continue to require exact conversion. An explicitly approximate continuous-
   * double mapping may advance to the next finite double to keep the effective delivery from
   * preceding the source.
   */
  public N adapt(DecimalSimTime source) {
    if (source.isInfinite()) {
      throw new IllegalArgumentException("Cannot deliver at logical infinity");
    }
    N nativeTime = isCoordinator() ? mapping.exact(source) : mapping.nextRepresentable(source);
    DecimalSimTime effective = mapping.toCanonical(nativeTime);
    if (effective.isInfinite() || effective.compareTo(source) < 0) {
      throw new IllegalArgumentException("Delivery violates causality or finite range");
    }
    return nativeTime;
  }
}