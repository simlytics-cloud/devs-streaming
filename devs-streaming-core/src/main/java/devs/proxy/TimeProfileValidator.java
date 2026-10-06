package devs.proxy;

import devs.iso.*;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.ExactTimeJsonCodec;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import java.util.Set;

/** Validates transport permission without changing timestamp values or local clock context. */
public final class TimeProfileValidator {
  /** Simulation message vocabulary permitted by the exact-time envelope. */
  public static final Set<String> MESSAGE_TYPES = Set.of("SimulationInit", "SimulationTerminate",
      "ModelTerminated", "RequestOutput", "ExecuteTransition", "NextInternalTimeReport",
      "OutputReport", "TransitionComplete");

  private TimeProfileValidator() {}

  /**
   * Unwraps local initialization and checks message vocabulary and transport time fields.
   * Enhanced clock context is local-only and is rejected rather than silently discarded.
   *
   * @param message the message to validate
   * @param policy the endpoint's wire profile and timestamp permissions
   * @return the portable simulation message, with timestamp values unchanged
   * @throws IllegalArgumentException if the message, context, or timestamps are not portable
   */
  public static DevsSimMessage validate(DevsMessage message, TimeEndpointPolicy policy) {
    if (message instanceof SimulationInitMessage<?> init) {
      if (init.getEnhancedClock().isPresent()) {
        throw new IllegalArgumentException("profile-mismatch: enhanced coordinator context has no external transport profile");
      }
      message = init.getSimulationInit();
    }
    if (!(message instanceof DevsSimMessage wire)) {
      throw new IllegalArgumentException("profile-mismatch: only DEVS simulation messages are portable");
    }
    if (policy.profile() == TimeWireProfile.EXACT_TIME_V1
        && (!MESSAGE_TYPES.contains(wire.getMessageType().name())
            || !wire.getClass().getSimpleName().equals(wire.getMessageType().name()))) {
      throw new IllegalArgumentException("profile-mismatch: unsupported exact message vocabulary");
    }
    if (wire instanceof TimedMessage<?> timed) {
      validateTime(timed.getEventTime(), "eventTime", policy);
    }
    if (wire instanceof NextInternalTimeReport<?> report) {
      validateTime(report.getNextInternalTime(), "nextInternalTime", policy);
    } else if (wire instanceof OutputReport<?> report) {
      validateTime(report.getNextInternalTime(), "nextInternalTime", policy);
    } else if (wire instanceof TransitionComplete<?> report) {
      validateTime(report.getNextInternalTime(), "nextInternalTime", policy);
    }
    return wire;
  }

  /** Applies exact-value limits and endpoint permissions, reporting the offending wire field. */
  private static void validateTime(SimTime value, String field, TimeEndpointPolicy policy) {
    try {
      if (value instanceof DecimalSimTime || value instanceof QuantizedSimTime) {
        ExactTimeJsonCodec.validate(value);
      }
      policy.validate(value);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      String category = exception.getMessage() != null && exception.getMessage().startsWith("time-limit-exceeded")
          ? "time-limit-exceeded" : "unsupported-time-type";
      throw new IllegalArgumentException(category + ": invalid or unrepresentable " + field);
    }
  }
}