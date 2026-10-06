/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
 * DEVS Streaming Framework Java contributors.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package devs;



import devs.iso.DevsMessage;
import devs.iso.DevsSimMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.SimulationTerminatePayload;
import devs.iso.TransitionComplete;
import devs.iso.time.SimTime;
import devs.iso.time.QuantizedSimTime;
import devs.time.EnhancedClockContext;
import java.util.HashSet;
import java.util.Set;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ChildFailed;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;

/**
 * Drives a hierarchical DEVS simulation through one child and bounds its logical-time progression.
 * The construction entry point selects one of two separate protocols; enhanced semantics are not
 * added to a legacy simulation implicitly.
 *
 * <h2>Legacy common-time execution</h2>
 * The deprecated {@link #create(SimTime, ActorRef, String)} and public constructor assume that the
 * start, horizon, and all child reports already use a compatible common time base. Timestamps are
 * forwarded and compared directly: there is no unit/epoch conversion, lattice harmonization, or
 * delivery adaptation. Initialization forwards the start without an enhanced clock context. The
 * first {@link NextInternalTimeReport} immediately triggers a {@link RequestOutput}, without a
 * horizon check. Subsequently each {@link OutputReport} supplies the next time: a value at or before
 * the horizon triggers another output request; a later value triggers termination at the current
 * requested time. This protocol relies on the legacy {@link PDevsCoordinator} completing child
 * transitions before reporting output. The root does not send a separate transition command and
 * does not handle {@link TransitionComplete} in legacy mode.
 *
 * <h2>Enhanced canonical execution</h2>
 * {@link #enhanced(QuantizedSimTime, ActorRef, String, EnhancedClockContext)} uses exact rational
 * seconds represented by {@link QuantizedSimTime}, not a fixed common tick lattice. The start and
 * horizon must be finite, with start no later than the horizon. The root forwards the shared
 * {@link EnhancedClockContext} to its enhanced child coordinator; every coordinator in that
 * hierarchy must agree on both clock identity and composition origin. Native conversion and
 * per-route delivery adaptation belong to the child hierarchy, not to this root. The context's
 * origin is not an extra offset applied by the root.
 *
 * <p>For each finite candidate at or before the inclusive horizon, the root requests output, waits
 * for the output report at that same active time, sends an empty {@link ExecuteTransition}, and
 * waits for {@link TransitionComplete} before selecting the next candidate. The child's candidate
 * includes both native internal events mapped to canonical time and queued effective deliveries.
 * Thus a delayed input can keep the simulation active even when all native internal candidates are
 * infinite. The root never substitutes an output report's next time for the post-transition next
 * time. Equal-time candidates are permitted; backward progression is not.
 *
 * <p>Logical infinity means there is no finite candidate and is never requested as an event time.
 * An infinite candidate terminates at the last completed time (or start if no step completed).
 * A finite candidate beyond the horizon terminates at the horizon without executing that candidate;
 * a candidate exactly at the horizon is executed. Responses are checked for phase, duplicate
 * message identifiers, sender, receiver, run, canonical event time, and nondecreasing next time.
 *
 * <p>Neither protocol implements wall-clock pacing, deadlines, or lag recovery. Waiting for child
 * responses synchronizes logical execution, not real-time speed. Enhanced safeguards enforce the
 * protocol; they do not assess native adaptation suitability or establish composition validity.
 *
 * @param <T> common simulation time in legacy mode; {@link QuantizedSimTime} in enhanced mode
 */
public class RootCoordinator<T extends SimTime> extends AbstractBehavior<DevsMessage> {

  private T time;
  private String simulationRunId;
  private final T endTime;
  private final ActorRef<DevsMessage> child;
  private final String childModelId;
  private final EnhancedClockContext enhancedClock;
  private EnhancedPhase enhancedPhase = EnhancedPhase.NEW;
  private T activeTime;
  private final Set<String> receivedMessageIds = new HashSet<>();

  /** Enhanced protocol phases used to reject out-of-order child responses. */
  private enum EnhancedPhase {
    NEW, INITIALIZING, COLLECTING_OUTPUT, AWAITING_TRANSITION, TERMINATING
  }

  /**
   * Creates a legacy root using timestamps already expressed on a common time axis.
   * No native-clock conversion or enhanced phase validation is performed.
   *
   * @param <TT>    A type extending {@link SimTime} that represents the simulation's time base.
   * @param endTime the simulation's termination time
   * @param child   the reference to the child actor participating in the simulation
   * @param childModelId the identifier used to address the child
   * @return the root coordinator's behavior
   * @deprecated Retained for legacy common-time simulations; use {@link #enhanced} for an
   *             explicitly configured enhanced hierarchy.
   */
  @Deprecated
  public static <TT extends SimTime> Behavior<DevsMessage> create(TT endTime,
                                                                  ActorRef<DevsMessage> child,
      String childModelId) {
    return Behaviors.setup(context -> new RootCoordinator<>(context,
        endTime, child, childModelId));
  }

  /**
   * Constructs a legacy root. The child, initialization time, and horizon must already share
   * compatible time semantics; this constructor does not harmonize them.
   *
   * @param context the actor context for this RootCoordinator
   * @param endTime the simulation's termination time
   * @param child   the reference to the child actor participating in the simulation
   * @param childModelId the identifier used to address the child
   * @deprecated Retained for legacy common-time simulations; use {@link #enhanced} for canonical
   *             coordination.
   */
  @Deprecated
  public RootCoordinator(ActorContext<DevsMessage> context, T endTime,
                          ActorRef<DevsMessage> child, String childModelId) {
    this(context, endTime, child, childModelId, null);
  }

  /**
   * Constructs the root for either protocol without changing the child's time representation.
   *
   * @param context root actor context
   * @param endTime simulation horizon in the selected protocol's time representation
   * @param child actor coordinating the subordinate hierarchy
   * @param childModelId identifier used to address the child
   * @param enhancedClock shared canonical clock context, or {@code null} for legacy mode
   */
  private RootCoordinator(ActorContext<DevsMessage> context, T endTime,
      ActorRef<DevsMessage> child, String childModelId, EnhancedClockContext enhancedClock) {
    super(context);
    this.endTime = endTime;
    this.child = child;
    this.childModelId = childModelId;
    this.enhancedClock = enhancedClock;
  }

  /**
   * Creates an enhanced root with an inclusive finite horizon in canonical rational seconds.
   * Send a {@link SimulationInit} with a finite canonical start at or before {@code endTime},
   * addressed to {@code root}. Initialization passes {@code clock} to the child unchanged; the
   * child must be an enhanced coordinator with the same context, not a native simulator or a
   * legacy coordinator. This method does not inspect the actor implementation.
   *
   * @param endTime finite canonical simulation horizon
   * @param child enhanced coordinator actor driving the subordinate hierarchy
   * @param childModelId the identifier used to address and validate the child
   * @param clock shared clock identity and composition origin; not an automatic conversion offset
   * @return the enhanced root behavior
   * @throws NullPointerException if the horizon or clock is null
   * @throws IllegalArgumentException if the horizon is infinite
   */
  public static Behavior<DevsMessage> enhanced(QuantizedSimTime endTime,
      ActorRef<DevsMessage> child, String childModelId, EnhancedClockContext clock) {
    java.util.Objects.requireNonNull(clock);
    if (java.util.Objects.requireNonNull(endTime).isInfinite()) {
      throw new IllegalArgumentException("Enhanced root requires a finite horizon");
    }
    return Behaviors.setup(context -> new RootCoordinator<>(context, endTime,
        child, childModelId, clock));
  }

  /**
   * Creates a receive behavior for handling various messages and signals in the hierarchical DEVS
   * simulation model. This method defines specific handlers for simulation lifecycle events and
   * failure scenarios.
   *
   * @return the receive behavior configured to process messages and signals relevant to the root
   * coordinator's role in the simulation.
   */
  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(SimulationInit.class, this::onSimulationInit);
    builder.onMessage(NextInternalTimeReport.class, this::onNextInternalTimeReport);
    builder.onMessage(OutputReport.class, this::onOutputReport);
    builder.onMessage(TransitionComplete.class, this::onTransitionComplete);
    builder.onMessage(ModelTerminated.class, this::onModelTerminated);
    builder.onSignal(ChildFailed.class, this::onChildFailed);
    builder.onSignal(Terminated.class, this::onTerminated);

    return builder.build();
  }

  protected String generateMessageId() {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   * Stores the start and run identifier and forwards initialization to the child without changing
   * the timestamp. Legacy initialization has no enhanced context. Enhanced initialization is
   * accepted only once, must be addressed to {@code root}, and requires a finite canonical start
   * no later than the horizon; the shared context accompanies the forwarded local actor wrapper.
   *
   * @param simulationInit the initialization message containing the start time of the simulation and
   *                related data.
   * @return the updated behavior of the root coordinator after processing the initialization
   * message.
   */
  Behavior<DevsMessage> onSimulationInit(SimulationInit<T> simulationInit) {
    if (enhancedClock != null) {
      if (enhancedPhase != EnhancedPhase.NEW) {
        throw new IllegalStateException("Enhanced root is already initialized");
      }
      if (!(simulationInit.getEventTime() instanceof QuantizedSimTime start)
          || start.isInfinite() || start.compareTo(endTime) > 0) {
        throw new IllegalArgumentException("Enhanced root requires finite canonical start <= horizon");
      }
      if (!"root".equals(simulationInit.getReceiverId())) {
        throw new IllegalArgumentException("Initialization is not addressed to root");
      }
      enhancedPhase = EnhancedPhase.INITIALIZING;
    }
    this.time = simulationInit.getEventTime();
    this.simulationRunId = simulationInit.getSimulationRunId();
    SimulationInit<T> rootInit = SimulationInit.<T>builder()
        .eventTime(simulationInit.getEventTime())
        .simulationRunId(simulationInit.getSimulationRunId())
        .messageId(generateMessageId())
        .senderId("root")
        .receiverId(childModelId)
        .build();
    child.tell(new SimulationInitMessage<T>(rootInit, getContext().getSelf(), enhancedClock));
    return this;
  }

  /**
   * Accepts the child's initial next-time report. Enhanced mode verifies that the report describes
   * the canonical start and that its candidate does not precede that start, then applies the
   * horizon/infinity rules in {@link #selectCandidate}. Legacy mode directly requests the reported
   * next time, with no initial horizon or infinity guard.
   *
   * @param nextInternalTimeReport the message containing the next scheduled simulation time.
   * @return the current behavior after selecting the first step or initiating termination
   */
  Behavior<DevsMessage> onNextInternalTimeReport(NextInternalTimeReport<T> nextInternalTimeReport) {
    if (enhancedClock != null) {
      validateResponse(nextInternalTimeReport, EnhancedPhase.INITIALIZING);
      validateEventTime(nextInternalTimeReport.getEventTime(), time);
      validateNextTime(nextInternalTimeReport.getNextInternalTime(), time);
      receivedMessageIds.add(nextInternalTimeReport.getMessageId());
      selectCandidate(nextInternalTimeReport.getNextInternalTime());
      return this;
    }
    time = nextInternalTimeReport.getNextInternalTime();
    child.tell(RequestOutput.<T>builder()
        .eventTime(time)
        .simulationRunId(simulationRunId)
        .messageId(generateMessageId())
        .senderId("root")
        .receiverId(childModelId)
        .build());
    return this;
  }

  /**
   * Advances the mode-specific protocol after child output. Legacy reports arrive after the child's
   * transitions and supply the next candidate directly. Enhanced reports must match the active
   * canonical time; their next time is validated but is not used to advance the simulation yet.
   * Instead an empty transition command at the same time lets the child merge due deliveries and
   * execute its internal/confluent transitions before reporting a new candidate.
   *
   * @param outputReport the child's output and next-time report
   * @return the current behavior after requesting another legacy step, termination, or an enhanced
   *         transition
   */
  Behavior<DevsMessage> onOutputReport(OutputReport<T> outputReport) {
    if (enhancedClock != null) {
      validateResponse(outputReport, EnhancedPhase.COLLECTING_OUTPUT);
      validateEventTime(outputReport.getEventTime(), activeTime);
      validateNextTime(outputReport.getNextInternalTime(), activeTime);
      receivedMessageIds.add(outputReport.getMessageId());
      enhancedPhase = EnhancedPhase.AWAITING_TRANSITION;
      child.tell(((ExecuteTransition.Builder<T>) ExecuteTransition.<T>builder())
          .eventTime(activeTime)
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId())
          .senderId("root")
          .receiverId(childModelId)
          .payload(ExecuteTransitionPayload.builder().build())
          .build());
      return this;
    }
    if (outputReport.getNextInternalTime().compareTo(endTime) <= 0) {
      time = outputReport.getNextInternalTime();
      child.tell(RequestOutput.<T>builder()
          .eventTime(time)
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId())
          .senderId("root")
          .receiverId(childModelId)
          .build());
    } else {
      child.tell(SimulationTerminate.<T>builder()
          .eventTime(time)
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId())
          .senderId("root")
          .receiverId(childModelId)
          .payload(SimulationTerminatePayload.builder().reason("Simulation completed").build())
          .build());
    }
    return this;
  }

  /**
   * Commits the enhanced active time only after the child has completed its transitions, then
   * selects the reported post-transition candidate. Equal-time next events are allowed, but
   * rollback, mismatched event times, and out-of-phase reports are rejected. Legacy mode leaves
   * this message unhandled because its output report already completes the step.
   *
   * @param transitionComplete completion at the active canonical time with the recomputed next time
   * @return the current enhanced behavior, or an unhandled behavior in legacy mode
   */
  Behavior<DevsMessage> onTransitionComplete(TransitionComplete<T> transitionComplete) {
    if (enhancedClock == null) {
      return Behaviors.unhandled();
    }
    validateResponse(transitionComplete, EnhancedPhase.AWAITING_TRANSITION);
    validateEventTime(transitionComplete.getEventTime(), activeTime);
    validateNextTime(transitionComplete.getNextInternalTime(), activeTime);
    receivedMessageIds.add(transitionComplete.getMessageId());
    time = activeTime;
    selectCandidate(transitionComplete.getNextInternalTime());
    return this;
  }

  /** Checks enhanced phase, message identity, and addressing before accepting a child response. */
  private void validateResponse(DevsSimMessage response, EnhancedPhase expectedPhase) {
    if (enhancedPhase != expectedPhase || receivedMessageIds.contains(response.getMessageId())) {
      throw new IllegalStateException("Unexpected or duplicate enhanced root response");
    }
    if (!childModelId.equals(response.getSenderId())
        || !simulationRunId.equals(response.getSimulationRunId())
        || !"root".equals(response.getReceiverId())) {
      throw new IllegalArgumentException("Enhanced root response sender, run or receiver mismatch");
    }
  }

  /** Requires a finite canonical event time equal to the start or active time being acknowledged. */
  private void validateEventTime(SimTime eventTime, T expectedTime) {
    if (!(eventTime instanceof QuantizedSimTime canonical) || canonical.isInfinite()
        || canonical.compareTo(expectedTime) != 0) {
      throw new IllegalArgumentException("Enhanced root response does not match the active time");
    }
  }

  /** Allows canonical infinity or an equal/later finite candidate, never a native time or rollback. */
  private void validateNextTime(SimTime nextTime, T currentTime) {
    if (!(nextTime instanceof QuantizedSimTime canonical)
        || canonical.compareTo(currentTime) < 0) {
      throw new IllegalArgumentException("Enhanced root requires canonical next time without rollback");
    }
  }

  /**
   * Requests an already-validated enhanced candidate if it is finite and within the inclusive
   * horizon. Infinity terminates at the last committed time; a later finite candidate terminates
   * at the horizon. Neither case executes an out-of-range event or manufactures a horizon step.
   *
   * @param candidate canonical next time, including pending effective deliveries in the child
   */
  private void selectCandidate(T candidate) {
    if (((QuantizedSimTime) candidate).isInfinite() || candidate.compareTo(endTime) > 0) {
      enhancedPhase = EnhancedPhase.TERMINATING;
      // Infinity is a scheduling sentinel, not a timestamp to send as a termination event.
      child.tell(SimulationTerminate.<T>builder()
          .eventTime(((QuantizedSimTime) candidate).isInfinite() ? time : endTime)
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId())
          .senderId("root")
          .receiverId(childModelId)
          .payload(SimulationTerminatePayload.builder().reason("Simulation completed").build())
          .build());
      return;
    }
    activeTime = candidate;
    enhancedPhase = EnhancedPhase.COLLECTING_OUTPUT;
    child.tell(RequestOutput.<T>builder()
        .eventTime(activeTime)
        .simulationRunId(simulationRunId)
        .messageId(generateMessageId())
        .senderId("root")
        .receiverId(childModelId)
        .build());
  }

  /**
   * Handles the {@code ModelTerminated} message in the simulation. This method processes the completion
   * event of a DEVS model's execution, typically signaling that the model has finished its
   * computation or has reached its terminal state.
   *
   * @param modelTerminated the message indicating the completion of the model's execution, encapsulating
   *                  necessary details about the completed model.
   * @return the behavior indicating the termination of the root coordinator's actor after
   * processing the {@code ModelTerminated} message.
   */
  Behavior<DevsMessage> onModelTerminated(ModelTerminated<T> modelTerminated) {
    if (enhancedClock != null) {
      validateResponse(modelTerminated, EnhancedPhase.TERMINATING);
    }
    return Behaviors.stopped();
  }

  /**
   * Handles the failure of a child actor within the DEVS simulation system. This method logs the
   * failure message and retains the current behavior.
   *
   * @param childFailed the signal containing information about the failed child actor and the cause
   *                    of the failure.
   * @return the current behavior of the RootCoordinator after handling the failure.
   */
  Behavior<DevsMessage> onChildFailed(ChildFailed childFailed) {
    getContext().getLog()
        .error("Child actor failed with cause " + childFailed.cause().getMessage());
    return this;
  }

  Behavior<DevsMessage> onTerminated(Terminated terminated) {
    getContext().getLog().debug("Root coordinator received terminated {}", terminated);
    return Behaviors.same();
  }
}
