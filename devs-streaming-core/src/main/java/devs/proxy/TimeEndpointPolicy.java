package devs.proxy;

import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.SimTime;
import devs.time.DecimalTimeMapping;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Explicit endpoint permission, separate from JSON capability and coordinator execution mode. */
public final class TimeEndpointPolicy {
  /**
   * Declared transport role: {@code LEGACY} uses numeric encoding, {@code NATIVE} accepts the
   * endpoint's own time type, and
   * {@code RECORDER} accepts an explicitly declared set of types without mapping them.
   */
  public enum Role { LEGACY, NATIVE, RECORDER }

  private static final Set<Class<? extends SimTime>> SUPPORTED = Set.of(
      LongSimTime.class, DoubleSimTime.class, DecimalSimTime.class);
  private final TimeWireProfile profile;
  private final Role role;
  private final Set<Class<? extends SimTime>> acceptedTypes;
  private final Consumer<SimTime> nativeValidator;

  private TimeEndpointPolicy(TimeWireProfile profile, Role role,
      Set<Class<? extends SimTime>> acceptedTypes, Consumer<SimTime> nativeValidator) {
    this.profile = Objects.requireNonNull(profile);
    this.role = Objects.requireNonNull(role);
    this.acceptedTypes = Set.copyOf(acceptedTypes);
    if (acceptedTypes.isEmpty() || !SUPPORTED.containsAll(acceptedTypes)) {
      throw new IllegalArgumentException("unsupported-time-type: endpoint must declare supported concrete types");
    }
    this.nativeValidator = nativeValidator;
  }

  /**
   * Creates the legacy numeric policy for long and double timestamps.
   *
   * @return a policy that does not perform native mapping validation
   */
  public static TimeEndpointPolicy legacy() {
    return new TimeEndpointPolicy(TimeWireProfile.LEGACY_NUMERIC_V1, Role.LEGACY,
        Set.of(LongSimTime.class, DoubleSimTime.class), ignored -> {});
  }

  /**
   * Creates an exact-time policy accepting only decimal timestamps, without mapping validation.
   *
   * @return the decimal-native endpoint policy
   */
  public static TimeEndpointPolicy decimalNative() {
    return new TimeEndpointPolicy(TimeWireProfile.EXACT_TIME_V1, Role.NATIVE,
        Set.of(DecimalSimTime.class), ignored -> {});
  }

  /** Creates a mapping-aware decimal-native endpoint policy. */
  public static <N extends SimTime> TimeEndpointPolicy nativeEndpoint(Class<N> type, DecimalTimeMapping<N> mapping) {
    Objects.requireNonNull(mapping);
    return new TimeEndpointPolicy(TimeWireProfile.EXACT_TIME_V1, Role.NATIVE, Set.of(type),
        value -> mapping.toCanonical(type.cast(value)));
  }


  /**
   * Creates an exact-time recording policy without native mapping or adaptation.
   *
   * @param types a nonempty set of supported concrete timestamp classes, copied by the policy
   * @return the recording policy
   * @throws IllegalArgumentException if the set is empty or contains unsupported classes
   */
  public static TimeEndpointPolicy recorder(Set<Class<? extends SimTime>> types) {
    return new TimeEndpointPolicy(TimeWireProfile.EXACT_TIME_V1, Role.RECORDER, types, ignored -> {});
  }

  /** @return the wire representation required by this endpoint */
  public TimeWireProfile profile() { return profile; }
  /** @return the endpoint's declared transport role */
  public Role role() { return role; }
  /** @return the immutable set of accepted concrete timestamp classes */
  public Set<Class<? extends SimTime>> acceptedTypes() { return acceptedTypes; }

  /** Checks the exact timestamp class and, when configured, its native mapping constraints. */
  void validate(SimTime time) {
    if (time == null || !acceptedTypes.contains(time.getClass())) {
      throw new IllegalArgumentException("unsupported-time-type: endpoint does not accept timestamp type");
    }
    nativeValidator.accept(time);
  }
}