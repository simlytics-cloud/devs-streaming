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


import com.fasterxml.jackson.core.JsonProcessingException;
import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.OutputReportPayload;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.TransitionComplete;
import devs.iso.time.SimTime;
import devs.iso.time.DecimalSimTime;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.time.CanonicalSchedule;
import devs.time.InteractionKey;
import devs.time.PendingDelivery;
import devs.utils.DevsObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ChildFailed;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.slf4j.Logger;
import org.slf4j.event.Level;


/**
 * Coordinates child simulators and coupled coordinators using Parallel DEVS logical-time rules.
 * Legacy common-time execution and enhanced native-clock harmonization are distinct paths selected
 * at construction; supplying native metadata elsewhere does not enable enhanced execution.
 * Both modes snapshot one child order at construction: omitted order sorts identifiers by Java's
 * natural, case-sensitive {@link String} order. Custom lists preserve the exact sequence and must
 * contain every child exactly once, with no null, blank, duplicate, missing, or unknown entries.
 * Initialization, output requests, source bag assembly, transitions, and termination use that order.
 * Each child's bag order and resolver target order are preserved. This orders sends, not execution
 * or completion across actors; it supports but does not guarantee whole-run reproducibility.
 *
 * <h2>Legacy common-time execution</h2>
 * Deprecated factories and constructors use {@code executionConfig == null}. Parent and children
 * must already share compatible time units, epochs, representations, and infinity conventions.
 * The coordinator forwards timestamps unchanged and compares them directly; it performs no native
 * mapping, quantization, or delayed-delivery adaptation. Initialization sends the same start to
 * every child and waits for all next-time reports. The minimum in {@code nextTimeMap} becomes
 * {@code timeNext}; all children with that time are imminent.
 *
 * <p>A parent output request must equal {@code timeNext}. The coordinator collects all imminent
 * outputs, routes their bags at that same common time, and immediately dispatches transitions to
 * the union of recipients and imminent children in one ordered pass, once per child.
 * This intentionally replaces incidental map iteration and recipient-first legacy dispatch.
 * It waits for those completions, updates
 * {@code timeLast} and the minimum next time, then reports output to the parent. A separate parent
 * transition accepts times in the inclusive interval {@code [timeLast, timeNext]} and routes parent
 * inputs without conversion. An empty parent transition merely acknowledges completion. This is
 * the legacy protocol used by {@link RootCoordinator}, not the enhanced output-before-transition
 * handshake. Enhanced initialization context is explicitly rejected on this path.
 *
 * <h2>Enhanced canonical execution</h2>
 * {@link #enhanced} requires approved {@link CoordinatorExecutionConfig} and one typed
 * {@link ChildBinding} per child. Parent-facing times use {@link DecimalSimTime}: exact decimal
 * seconds plus logical infinity, with no common fixed tick size. Native children retain their own
 * clocks. For finite native local time, each binding applies the developer-supplied affine mapping:
 *
 * <pre>{@code
 * canonical = alignment + nativeOrigin + secondsPerUnit * local
 * local = (canonical - alignment - nativeOrigin) / secondsPerUnit
 * }</pre>
 *
 * <p>Units and native origins are supplier facts; alignment, codecs, and any
 * timing-error budgets are composition-developer choices. Encoding representability and native
 * local-zero timestamp lattices still constrain conversion even when canonical arithmetic is
 * exact. Infinity is decoded/encoded as a sentinel separately from finite affine arithmetic.
 * Coordinator children use canonical identity bindings, not a second native mapping. Every enhanced
 * coordinator must share the configured clock identity and composition origin; that context does
 * not automatically add an origin offset to a binding.
 *
 * <p>Initialization requires a finite canonical start and matching enhanced context. Each child's
 * start is encoded exactly, without delivery adaptation. Native children receive local timestamps
 * without enhanced context; coordinator children receive canonical timestamps with that context.
 * Once all initial reports arrive, {@link CanonicalSchedule} orders mapped child candidates and
 * pending effective deliveries together. A native infinity candidate does not suppress a pending
 * finite delivery. Equal-time candidates are legal, but candidates and dispatches cannot roll back.
 *
 * <p>At the next canonical time the coordinator collects outputs only from children whose internal
 * candidates are imminent. A delivery-only step can therefore report an empty output bag without
 * requesting any child output. Routing uses construction-time child order rather than response arrival
 * order, and reports external output to the parent before transitions. Parent-output routes remain
 * unchanged on the shared canonical clock. The parent then commands a transition at the
 * same active time. Alternatively, while ready it may provide nonempty external input before the
 * next scheduled time, but not before the last completed time.
 *
 * <p>Internal outputs and parent inputs automatically adapt upward at native targets using their
 * codecs and any native-zero granularity. Already accepted timestamps are unchanged. The
 * earliest permitted delivery may select a
 * later effective time, never a time before the source event. Pending deliveries preserve source
 * time, effective time, native target timestamp, and enqueue sequence. Only deliveries due at the
 * active time are drained; later ones remain scheduled while intervening events execute. For
 * example, a source at 1.5 ms adapted to the next 1 ms-lattice point at 2 ms does not skip an event
 * at 1.75 ms.
 *
 * <p>Due deliveries, current routed outputs, and parent inputs form one input bag per target.
 * One transition per affected child is dispatched in resolved {@code childOrder}, at an exactly
 * mapped native timestamp. An imminent child with inputs receives a single combined request so its
 * simulator/model can execute its confluent transition. Completion reports must match both the
 * dispatched native timestamp and the active canonical time. Each completion updates that child's
 * candidate; only after all affected children finish does the coordinator commit
 * {@code canonicalLast} and report the next minimum, including still-pending deliveries.
 *
 * <p>Enhanced termination is accepted only between steps, at a canonical time not before the last
 * completed step. Pending deliveries are discarded and each child is terminated at its own last
 * initialized/completed timestamp, not by rounding or advancing it to the parent's horizon.
 * The root decides the inclusive horizon; this coordinator does not execute pending events beyond
 * it during shutdown.
 *
 * <p>Configuration and runtime guards check clock agreement, protocol synchronization,
 * representability, and causality; they do not prove composition validity or all possible dynamic
 * routes. Developers must validate adaptation suitability before coupling. Optional error budgets observe source/effective time
 * displacement only when explicitly configured. Logical synchronization waits for child responses;
 * neither these barriers nor {@link StepTimingTracker} implement wall-clock pacing, deadlines, or
 * lag recovery. Enhanced context is local actor plumbing, not a distributed clock handshake.
 *
 * @param <T> common simulation time in legacy mode; {@link DecimalSimTime} in enhanced mode
 */
public class PDevsCoordinator<T extends SimTime>
    extends AbstractBehavior<DevsMessage> {

  protected record NextInternalTimeCheck() implements DevsMessage {}
  protected record OutputReportCheck(SimTime checkTime) implements DevsMessage {}
  protected record AwaitTransitionCheck() implements DevsMessage {}
  protected record ModelTerminatedCheck() implements DevsMessage {}

  final String modelIdentifier;
  String simulationRunId;
  ActorRef<DevsMessage> parent;
  String parentId;
  final Map<String, ActorRef<DevsMessage>> modelSimulators;
  private final PDevsCouplings couplings;
  private final StepTimingTracker stepTimingTracker;
  private final CoordinatorExecutionConfig executionConfig;
  private final Map<String, ChildBinding<?>> childBindings;
  private final List<String> childOrder;
  private final CanonicalSchedule canonicalSchedule;
  private final java.util.Set<String> initialReports = new java.util.HashSet<>();
  /** Protocol phases separating initialization, output collection, transitions, and termination. */
  private enum EnhancedPhase { INITIALIZING, READY, COLLECTING, OUTPUT_READY, TRANSITIONING, TERMINATING }
  private EnhancedPhase enhancedPhase = EnhancedPhase.INITIALIZING;
  private DecimalSimTime canonicalActive;
  private List<RoutedDelivery> routedOutput = List.of();
  private final Map<String, SimTime> dispatchedTimes = new HashMap<>();
  /**
   * Identifies a message for duplicate detection within one simulation run.
   *
   * @param sender identifier of the sending model
   * @param type protocol message type
   * @param id sender-assigned message identifier
   */
  private record ResponseKey(String sender, devs.iso.SimMessageType type, String id) {}
  private final java.util.Set<ResponseKey> seenResponseIds = new java.util.HashSet<>();
  private DecimalSimTime canonicalLast;
  private final Map<String, SimTime> nativeLast = new HashMap<>();
  private T timeLast;
  private T timeNext;
  private final Map<String, T> nextTimeMap = new HashMap<>();
  private List<String> imminentModels = new ArrayList<>();
  private Map<String, Optional<List<PortValue<?>>>> outputMap = new HashMap<>();

  Map<String, List<PortValue<?>>> receivers = new HashMap<>();
  List<PortValue<?>> modelOutput;
  private List<String> awaitingTransition = new ArrayList<>();
  private boolean generatingOutput;

  /**
   * Creates a legacy coordinator whose parent and children already share a common time base.
   * Timestamps are forwarded unchanged; no enhanced bindings or adaptation are installed.
   *
   * @param modelIdentifier coupled model identifier
   * @param modelsSimulators child identifiers and simulator actors
   * @param couplings input/output routes
   * @return the legacy coordinator behavior
   * @deprecated Use {@link #enhanced} for explicitly configured native-clock harmonization.
   */
  @Deprecated
  public static Behavior<DevsMessage> create(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings) {
    return create(modelIdentifier, modelsSimulators, couplings, StepTimingTracker.disabled());
  }

  /**
   * Creates a legacy common-time coordinator with wall-clock step measurements. The tracker
   * observes execution latency; it does not convert logical time or pace the simulation.
   *
   * @param modelIdentifier coupled model identifier
   * @param modelsSimulators child identifiers and simulator actors
   * @param couplings input/output routes
   * @param stepTimingTracker optional-by-configuration step measurement implementation
   * @return the legacy coordinator behavior
   * @deprecated Use {@link #enhanced} for explicitly configured native-clock harmonization.
   */
  @Deprecated
  public static Behavior<DevsMessage> create(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      StepTimingTracker stepTimingTracker) {
    return resolved(modelIdentifier, Map.copyOf(modelsSimulators), couplings, null, Map.of(),
        defaultChildOrder(modelsSimulators.keySet()), stepTimingTracker);
  }

  @Deprecated
  public static Behavior<DevsMessage> create(String id,
      Map<String, ActorRef<DevsMessage>> actors, PDevsCouplings couplings, List<String> childOrder) {
    return create(id, actors, couplings, childOrder, StepTimingTracker.disabled());
  }

  @Deprecated
  public static Behavior<DevsMessage> create(String id,
      Map<String, ActorRef<DevsMessage>> actors, PDevsCouplings couplings,
      List<String> childOrder, StepTimingTracker tracker) {
    return resolved(id, Map.copyOf(actors), couplings, null, Map.of(),
        customChildOrder(actors.keySet(), childOrder), tracker);
  }

  static List<String> defaultChildOrder(java.util.Set<String> children) {
    return customChildOrder(children, children.stream().sorted().toList());
  }

  static List<String> customChildOrder(java.util.Set<String> children, List<String> order) {
    java.util.Objects.requireNonNull(order, "childOrder");
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (String id : order) {
      if (id == null || id.isBlank() || !seen.add(id)) {
        throw new IllegalArgumentException("Invalid or duplicate child identifier");
      }
    }
    if (!seen.equals(children)) {
      throw new IllegalArgumentException("Child order must cover every child exactly once");
    }
    return List.copyOf(order);
  }

  static Behavior<DevsMessage> resolved(String id, Map<String, ActorRef<DevsMessage>> actors,
      PDevsCouplings couplings, CoordinatorExecutionConfig config,
      Map<String, ChildBinding<?>> bindings, List<String> order, StepTimingTracker tracker) {
    Map<String, ActorRef<DevsMessage>> snapshot = Map.copyOf(actors);
    Map<String, ChildBinding<?>> bindingSnapshot = Map.copyOf(bindings);
    if (snapshot.isEmpty()) {
      throw new IllegalArgumentException("Cannot create a PDevsCoordinator with no component models");
    }
    return Behaviors.setup(context -> new PDevsCoordinator<>(id, snapshot, couplings, context,
        tracker, config, bindingSnapshot, order, true));
  }


  /**
   * Constructs a legacy common-time coordinator with step measurement disabled. Parent and child
   * timestamps must already be compatible; no native mapping or delivery adaptation is performed.
   *
   * @param modelIdentifier  the unique identifier of the coordinator model.
   * @param modelsSimulators a map containing the identifiers of component models and their
   *                         associated simulator actor references.
   * @param couplings        the coupling information defining input-output relationships between
   *                         the component models.
   * @param context          the actor context for the current Pekko actor.
   * @throws IllegalArgumentException if the modelsSimulators map is empty.
   * @deprecated Use {@link #enhanced} for canonical coordination with explicit child bindings.
   */
  @Deprecated
  public PDevsCoordinator(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      ActorContext<DevsMessage> context) {
    this(modelIdentifier, modelsSimulators, couplings, context, StepTimingTracker.disabled());
  }

  /**
   * Constructs a legacy common-time coordinator with a tracker that measures execution latency,
   * independently of logical-time synchronization.
   *
   * @param modelIdentifier coupled model identifier
   * @param modelsSimulators child identifiers and simulator actors
   * @param couplings input/output routes
   * @param context coordinator actor context
   * @param stepTimingTracker step measurement implementation, not a pacing policy
   * @deprecated Use {@link #enhanced} for canonical coordination with explicit child bindings.
   */
  @Deprecated
  public PDevsCoordinator(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      ActorContext<DevsMessage> context, StepTimingTracker stepTimingTracker) {
    this(modelIdentifier, modelsSimulators, couplings, context, stepTimingTracker, null, Map.of());
  }

  @Deprecated
  public PDevsCoordinator(String id, Map<String, ActorRef<DevsMessage>> actors,
      PDevsCouplings couplings, ActorContext<DevsMessage> context, List<String> childOrder) {
    this(id, actors, couplings, context, childOrder, StepTimingTracker.disabled());
  }

  @Deprecated
  public PDevsCoordinator(String id, Map<String, ActorRef<DevsMessage>> actors,
      PDevsCouplings couplings, ActorContext<DevsMessage> context,
      List<String> childOrder, StepTimingTracker tracker) {
    this(id, actors, couplings, context, tracker, null, Map.of(), childOrder);
  }

  /**
   * Constructs the selected execution path. A null configuration selects legacy common time and
   * requires empty bindings. A nonnull configuration selects enhanced canonical time, validates
   * exact child coverage/order and declared coordinator clocks, and creates the canonical schedule.
   * The supplied configuration is an execution policy, not a proof of modeling suitability or
   * future route representability; route rules are checked when used.
   *
   * @param modelIdentifier coupled model identifier
   * @param modelsSimulators child identifiers and simulator actors
   * @param couplings input/output routes
   * @param context coordinator actor context
   * @param stepTimingTracker wall-clock step measurements, independent of logical time
   * @param executionConfig enhanced execution rules, or null for legacy execution
   * @param childBindings typed native/identity bindings covering all enhanced children, empty for
   *                      legacy execution
   * @throws IllegalArgumentException if children, bindings, or declared clocks are inconsistent
   */
  public PDevsCoordinator(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      ActorContext<DevsMessage> context, StepTimingTracker stepTimingTracker,
      CoordinatorExecutionConfig executionConfig, Map<String, ChildBinding<?>> childBindings) {
    this(modelIdentifier, modelsSimulators, couplings, context, stepTimingTracker, executionConfig,
        childBindings, defaultChildOrder(modelsSimulators.keySet()), true);
  }

  public PDevsCoordinator(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      ActorContext<DevsMessage> context, StepTimingTracker stepTimingTracker,
      CoordinatorExecutionConfig executionConfig, Map<String, ChildBinding<?>> childBindings,
      List<String> childOrder) {
    this(modelIdentifier, modelsSimulators, couplings, context, stepTimingTracker, executionConfig,
        childBindings, customChildOrder(modelsSimulators.keySet(), childOrder), true);
  }

  private PDevsCoordinator(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      ActorContext<DevsMessage> context, StepTimingTracker stepTimingTracker,
      CoordinatorExecutionConfig executionConfig, Map<String, ChildBinding<?>> childBindings,
      List<String> resolvedChildOrder, boolean resolved) {
    // Check for valid data
    super(context);
    if (modelsSimulators.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot create a PDevsCoordinator with no component models");
    }
    this.modelIdentifier = modelIdentifier;
    this.modelSimulators = Map.copyOf(modelsSimulators);
    this.childOrder = resolvedChildOrder;
    this.couplings = couplings;
    this.stepTimingTracker = stepTimingTracker;
    this.executionConfig = executionConfig;
    this.childBindings = Map.copyOf(childBindings);
    if (executionConfig != null) {
      if (!childBindings.keySet().equals(modelsSimulators.keySet())) {
        throw new IllegalArgumentException("Missing typed child bindings");
      }
      childBindings.values().forEach(binding -> binding.validateClock(executionConfig.clock()));
    } else if (!childBindings.isEmpty()) {
      throw new IllegalArgumentException("Legacy coordination cannot have enhanced bindings");
    }
    this.canonicalSchedule = executionConfig == null ? null
        : new CanonicalSchedule(executionConfig, this.childBindings, this.childOrder);
  }

  /**
   * Creates an enhanced coordinator using approved mappings and codec-owned upward adaptation.
   * Its parent must initialize it with a {@link SimulationInitMessage} carrying a finite canonical
   * start and the configured shared clock. Bind native simulators with native mappings and enhanced
   * coordinator children with canonical identity bindings; actor references themselves do not
   * expose or verify actor kind. Initial native timestamps must be exactly representable.
   *
   * @param modelIdentifier coupled model identifier
   * @param modelsSimulators child identifiers and simulator actors
   * @param couplings input/output routes, including dynamically resolved interactions
   * @param config shared clock and optional timing-error budgets
   * @param bindings one typed binding for each child
   * @return the enhanced canonical coordinator behavior, with step timing disabled
   * @throws NullPointerException if config is null
   */
  public static Behavior<DevsMessage> enhanced(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      CoordinatorExecutionConfig config, Map<String, ChildBinding<?>> bindings) {
    return enhanced(modelIdentifier, modelsSimulators, couplings, config, bindings,
        StepTimingTracker.disabled());
  }

  public static Behavior<DevsMessage> enhanced(String id,
      Map<String, ActorRef<DevsMessage>> actors, PDevsCouplings couplings,
      CoordinatorExecutionConfig config, Map<String, ChildBinding<?>> bindings,
      StepTimingTracker tracker) {
    java.util.Objects.requireNonNull(config);
    return resolved(id, actors, couplings, config, bindings, defaultChildOrder(actors.keySet()), tracker);
  }

  public static Behavior<DevsMessage> enhanced(String id,
      Map<String, ActorRef<DevsMessage>> actors, PDevsCouplings couplings,
      CoordinatorExecutionConfig config, Map<String, ChildBinding<?>> bindings,
      List<String> childOrder) {
    return enhanced(id, actors, couplings, config, bindings, childOrder, StepTimingTracker.disabled());
  }

  public static Behavior<DevsMessage> enhanced(String id,
      Map<String, ActorRef<DevsMessage>> actors, PDevsCouplings couplings,
      CoordinatorExecutionConfig config, Map<String, ChildBinding<?>> bindings,
      List<String> childOrder, StepTimingTracker tracker) {
    java.util.Objects.requireNonNull(config);
    return resolved(id, actors, couplings, config, bindings,
        customChildOrder(actors.keySet(), childOrder), tracker);
  }

  public String getModelIdentifier() {
    return modelIdentifier;
  }

  protected boolean shouldLog(String message) {
    return true;
  }

  /**
   * Logs a message at the specified severity level, if logging conditions are met. This method
   * determines whether the message should be logged using {@code shouldLog(String message)} and
   * delegates the logging operation to the appropriate logger based on the severity level.
   *
   * @param level   the severity level of the log message (e.g., TRACE, DEBUG, INFO, WARN, ERROR).
   * @param message the log message to be recorded.
   */
  protected void log(Level level, String message) {
    if (shouldLog(message)) {
      Logger logger = getContext().getLog();
      switch (level) {
        case TRACE -> logger.trace(message);
        case DEBUG -> logger.debug(message);
        case INFO -> logger.info(message);
        case WARN -> logger.warn(message);
        case ERROR -> logger.error(message);
      }
    }
  }

  /**
   * Creates a {@code Receive} instance for the {@code PDevsCoordinator} actor, defining the message
   * handling logic. The method builds a builder with message handlers for various
   * {@code DevsMessage} subtypes and actor signals.
   *
   * <p>The handlers process messages related to initializing the simulation, managing outputs,
   * executing transitions, handling time updates, and other simulation-related operations.
   *
   * @return a {@code Receive} object that specifies the actor's message handling behavior.
   */
  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();

    builder.onMessage(SimulationInitMessage.class, this::onSimulationInit);
    builder.onMessage(NextInternalTimeCheck.class, this::onNextInternalTimeCheck);
    builder.onMessage(RequestOutput.class, this::onRequestOutput);
    builder.onMessage(OutputReportCheck.class, this::onOutputReportCheck);
    builder.onMessage(ExecuteTransition.class, this::onExecuteTransition);
    builder.onMessage(NextInternalTimeReport.class, this::onNextInternalTimeReport);
    builder.onMessage(OutputReport.class, this::onOutputReport);
    builder.onMessage(TransitionComplete.class, this::onTransitionComplete);
    builder.onMessage(AwaitTransitionCheck.class, this::onAwaitTransitionCheck);
    builder.onMessage(SimulationTerminate.class, this::onSimulationTerminate);
    builder.onMessage(ModelTerminated.class, this::onModelTerminated);
    builder.onSignal(Terminated.class, this::onTerminated);
    builder.onMessage(ModelTerminatedCheck.class, this::onModelTerminatedCheck);
    builder.onSignal(ChildFailed.class, this::onChildFailed);

    return builder.build();
  }

  /**
   * Initializes the parent return path and subordinate clocks. Legacy mode stores the common start
   * as {@code timeLast} and forwards it unchanged to every child; enhanced context is rejected.
   * Enhanced mode delegates to {@link #initializeEnhanced} for exact native initialization and
   * shared-clock validation. Neither path begins event processing until initial next times arrive.
   *
   * @param tSimulationInitMessage initialization timestamp, run data, parent actor, and optional
   *                               enhanced clock context
   * @return The updated behavior of the actor after processing the initialization message.
   */
  private Behavior<DevsMessage> onSimulationInit(SimulationInitMessage<T> tSimulationInitMessage) {
    if (executionConfig != null) {
      return initializeEnhanced(tSimulationInitMessage);
    }
    if (tSimulationInitMessage.getEnhancedClock().isPresent()) {
      throw new IllegalArgumentException("Legacy coordinator in enhanced hierarchy");
    }
    this.parent = tSimulationInitMessage.getParent();
    this.parentId = tSimulationInitMessage.getSimulationInit().getSenderId();
    this.simulationRunId = tSimulationInitMessage.getSimulationInit().getSimulationRunId();
    timeLast = tSimulationInitMessage.getSimulationInit().getEventTime();
    // System.out.println("Last time for " + modelIdentifier + " is " + timeLast);

    childOrder.forEach(
        modelId -> {
          SimulationInit<T> coordinatorInit = SimulationInit.<T>builder()
              .eventTime(tSimulationInitMessage.getSimulationInit().getEventTime())
              .simulationRunId(tSimulationInitMessage.getSimulationInit().getSimulationRunId())
              .messageId(generateMessageId(""))
              .senderId(modelIdentifier)
              .receiverId(modelId)
              .build();
          modelSimulators.get(modelId).tell(new SimulationInitMessage<>(coordinatorInit, getContext().getSelf()));
        });
    getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), new NextInternalTimeCheck());
    return this;
  }

  /**
   * Initializes enhanced clocks exactly at one finite canonical start. The shared context must
   * match configuration and initialization may occur only once. Each native binding inverse-maps
   * the start and checks encoding/lattice representability; delivery rounding is not allowed for
   * initialization. Coordinator bindings are canonical identity mappings and alone receive the
   * enhanced context. The recorded per-child start is also its last local time until it transitions.
   *
   * @param message canonical initialization with the shared context and local parent return path
   * @return the current behavior awaiting all initial child reports
   * @throws IllegalArgumentException if initialization, clock agreement, or native conversion fails
   */
  private Behavior<DevsMessage> initializeEnhanced(SimulationInitMessage<?> message) {
    if (!message.getEnhancedClock().filter(executionConfig.clock()::equals).isPresent()
        || !(message.getSimulationInit().getEventTime() instanceof DecimalSimTime start)
        || start.isInfinite() || canonicalLast != null) {
      throw new IllegalArgumentException("Enhanced initialization requires the shared clock and finite time");
    }
    parent = message.getParent();
    parentId = message.getSimulationInit().getSenderId();
    simulationRunId = message.getSimulationInit().getSimulationRunId();
    canonicalLast = start;
    for (String id : childOrder) {
      ChildBinding<?> binding = childBindings.get(id);
      // Initialization must preserve the instant exactly; route adaptation applies only to inputs.
      SimTime local = binding.exact(start);
      nativeLast.put(id, local);
      SimulationInit<SimTime> init = SimulationInit.<SimTime>builder()
          .eventTime(local).simulationRunId(simulationRunId)
          .messageId(generateMessageId("SimulationInit"))
          .senderId(modelIdentifier).receiverId(id).build();
      modelSimulators.get(id).tell(new SimulationInitMessage<>(init, getContext().getSelf(),
          binding.isCoordinator() ? executionConfig.clock() : null));
    }
    return this;
  }

  /**
   * Returns the legacy minimum of the children's already-compatible next timestamps. This helper
   * does not decode native clocks or include enhanced pending deliveries; enhanced selection uses
   * {@link CanonicalSchedule#next()} instead. Call only after child next times have been populated.
   *
   * @return The next simulation time of type {@code T}, representing the minimum value present in
   * {@code nextTimeMap}.
   */
  T getNextTime() {
    return nextTimeMap.values().stream().min(T::compareTo).get();
  }

  protected String generateMessageId(String messageType) {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   * Selects legacy imminent children in resolved order by equality with the minimum common next time.
   * No conversion is applied here; enhanced imminence is determined from
   * mapped internal candidates by {@link CanonicalSchedule#imminent(DecimalSimTime)}.
   *
   * @return The next simulation time of type {@code T}, representing the earliest scheduled time
   * from {@code nextTimeMap}.
   */
  T buildImminentModels() {
    T minTime = getNextTime();
    imminentModels = childOrder.stream()
        .filter(id -> nextTimeMap.get(id).compareTo(minTime) == 0).toList();
    return minTime;
  }

  /**
   * Collects child initialization candidates and reports the first next time after all children
   * respond. Legacy mode stores native reports directly as common-time values. Enhanced mode
   * accepts one report per child only during initialization, decodes its event time to verify the
   * canonical start, and maps its next time to canonical seconds (or infinity). A candidate before
   * the start is rejected; equal-time candidates are allowed. The parent receives a canonical
   * report, not one child's native timestamp.
   *
   * @param nextInternalTimeReport child event time and next internal candidate on that child's clock
   * @return the current behavior after recording the candidate and optionally notifying the parent
   */
  Behavior<DevsMessage> onNextInternalTimeReport(NextInternalTimeReport<T> nextInternalTimeReport) {
    if (executionConfig != null) {
      String child = nextInternalTimeReport.getSenderId();
      checkEnhancedResponse(nextInternalTimeReport, child);
      if (canonicalLast == null || initialReports.contains(child)
          || enhancedPhase != EnhancedPhase.INITIALIZING
          || !canonicalSchedule.decode(child, nextInternalTimeReport.getEventTime()).equals(canonicalLast)) {
        throw new IllegalArgumentException("Unexpected or inconsistent initialization report");
      }
      canonicalSchedule.update(child, nextInternalTimeReport.getNextInternalTime(), canonicalLast);
      initialReports.add(child);
      if (canonicalSchedule.initialized()) {
        enhancedPhase = EnhancedPhase.READY;
        parent.tell(NextInternalTimeReport.<DecimalSimTime>builder()
            .eventTime(canonicalLast)
            .simulationRunId(simulationRunId).messageId(generateMessageId("NextTime"))
            .senderId(modelIdentifier).receiverId(parentId)
            .nextInternalTime(canonicalSchedule.next()).build());
      }
      return this;
    }
    getContext().getLog().debug("Received NextInternalTimeReport from {}", nextInternalTimeReport.getSenderId());
    nextTimeMap.put(nextInternalTimeReport.getSenderId(), nextInternalTimeReport.getNextInternalTime());

    // If we have all next time messages from models, the next time if the min
    if (nextTimeMap.size() == modelSimulators.size()) {
      timeNext = getNextTime();
      // System.out.println("In onNextTimeMessage, Next time for " + modelIdentifier + " is " +
      // timeNext);
      parent.tell(NextInternalTimeReport.<T>builder()
          .eventTime(nextInternalTimeReport.getEventTime())
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId("NextTime"))
          .senderId(modelIdentifier)
          .receiverId(parentId)
          .nextInternalTime(timeNext)
          .build());
    }
    return this;
  }

  Behavior<DevsMessage> onNextInternalTimeCheck(NextInternalTimeCheck nextInternalTimeCheck) {
    if (nextTimeMap.size() < modelSimulators.size()) {
      for (String modelId: modelSimulators.keySet()) {
        if (!nextTimeMap.containsKey(modelId)) {
          getContext().getLog().warn("{} waiting for next time from {}", modelIdentifier, modelId);
           return this;
        }
      }
      getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), new NextInternalTimeCheck());
    }

    return this;
  }

  /**
   * Begins output collection at the scheduled next time. Legacy mode requires equality with
   * {@code timeNext} and forwards the unchanged timestamp to imminent children. Enhanced mode
   * delegates to {@link #requestEnhancedOutput}, checking the canonical schedule minimum (including
   * pending deliveries) and exactly mapping the instant for children with imminent internal events.
   *
   * @param requestOutput parent request at the next common or canonical time, according to mode
   * @return the current behavior awaiting outputs, or reporting a delivery-only enhanced step
   * @throws RuntimeException if the request is out of synchronization
   */
  Behavior<DevsMessage> onRequestOutput(RequestOutput<T> requestOutput) {
    if (executionConfig != null) {
      return requestEnhancedOutput(requestOutput);
    }
    if (requestOutput.getEventTime().compareTo(timeNext) != 0) {
      try {
        String sendOutputMessage = DevsObjectMapper.buildObjectMapper().writeValueAsString(requestOutput);
        throw new RuntimeException("Bad synchronization.  Received SendOutputMessage "
            + sendOutputMessage + "where time "
            + requestOutput.getEventTime() + " did not equal " + timeNext);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Bad synchronization.  Received SendOutputMessage where time "
            + requestOutput.getEventTime() + " did not equal " + timeNext);
      }
    }
    generatingOutput = true;
    stepTimingTracker.stepStarted();
    buildImminentModels();
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG, "Immenent models are " + Arrays.toString(imminentModels.toArray()));
    }
    outputMap = new HashMap<>();
    imminentModels.forEach(m -> {
      stepTimingTracker.outputRequestSent(m);
      modelSimulators.get(m).tell(RequestOutput.<T>builder()
          .eventTime(requestOutput.getEventTime())
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId("SendOutput"))
          .senderId(modelIdentifier)
          .receiverId(m)
          .build()

          );
      outputMap.put(m, Optional.empty());
    });
    getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), new OutputReportCheck(requestOutput.getEventTime()));
    return this;
  }

  /**
   * Determines whether all outputs in the output map are complete. The method checks the values in
   * the `outputMap` to verify if any output is empty, indicating an incomplete state. If no empty
   * outputs are found, the method returns {@code true}, confirming that all outputs are present.
   *
   * @return {@code true} if all outputs in the `outputMap` are non-empty, {@code false} otherwise.
   */
  private boolean haveAllOutputs() {
    return outputMap.values().stream().filter(o -> o.isEmpty()).findFirst().isEmpty();
  }

  /**
   * Collects child outputs under the mode-specific protocol. Legacy mode waits for every imminent
   * output, routes bags at {@code timeNext}, and dispatches child transitions immediately; the
   * parent output report waits for transition completion. Enhanced mode validates and collects
   * outputs at the unchanged active instant through {@link #receiveEnhancedOutput}, then reports
   * output before any transitions so that parent inputs can be merged into the same step.
   *
   * @param outputReport the {@link OutputReport} containing the outputReport from a model,
   *                including the sender, the next simulation time, and the associated
   *                port-value list.
   * @return the current behavior of the actor, allowing for further processing.
   */
  Behavior<DevsMessage> onOutputReport(OutputReport<T> outputReport) {
    if (executionConfig != null) {
      return receiveEnhancedOutput(outputReport);
    }
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG,
          "Got model outputReport at " + timeNext + " from " + outputReport.getSenderId() + ": "
              + Arrays.toString(outputReport.getPayload().getOutputs().stream()
              .map(pv -> pv.getPortName()).toArray()));
    }
    // outputReport.getModelOutput().getPortValueList().forEach(portValue -> getContext().getLog().debug(
    // " " + portValue.getPortIdentifier() + ": " + portValue.getValue()));
    stepTimingTracker.outputReportReceived(outputReport.getSenderId());
    outputMap.put(outputReport.getSenderId(), Optional.of(outputReport.getPayload().getOutputs()));
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG, "Have outputReport from " + Arrays.toString(outputMap.keySet().toArray()));
    }

    if (haveAllOutputs()) {
      // Send outputReport to parent based on mappings and translations
      // Send inputs to children based on mappings and translations
      log(Level.DEBUG, "We have all outputReport.");
      stepTimingTracker.allOutputsReceived();
      stepTimingTracker.transitionDispatchStarted();
      awaitingTransition = new ArrayList<>();
      OutputCouplingMessages outputCouplingMessages = couplings.handleOutputBag(outputMap, childOrder);
      boolean hasOutputPortValues = false;
      for (Optional<List<PortValue<?>>> output : outputMap.values()) {
        if (output.isPresent() && output.get().size() > 0) {
          hasOutputPortValues = true;
          break;
        }
      }
      if (hasOutputPortValues && outputCouplingMessages.getExternalOutputMessages().isEmpty() && outputCouplingMessages.getInternalMessages().isEmpty()) {
        List<String> inputPorts = outputReport.getPayload().getOutputs().stream().map(PortValue::getPortName).toList();
        log(Level.WARN, "Output from " + outputReport.getSenderId() + " at port "
                + Arrays.toString(inputPorts.toArray()) + " not handled.");
      }
      modelOutput = outputCouplingMessages.getExternalOutputMessages();
      receivers = outputCouplingMessages.getInternalMessages();

      validateRecipients(receivers);
      for (String key : childOrder) {
        if (!receivers.containsKey(key) && !imminentModels.contains(key)) {
          continue;
        }
        List<PortValue<?>> value = receivers.getOrDefault(key, List.of());
        awaitingTransition.add(key);
        stepTimingTracker.transitionSent(key);
        modelSimulators.get(key).tell(ExecuteTransition.<T>builder()
            .eventTime(timeNext)
            .payload(ExecuteTransitionPayload.builder().addAllInputs(value).build())
            .simulationRunId(simulationRunId)
            .messageId(generateMessageId("ExecuteTransition"))
            .senderId(modelIdentifier)
            .receiverId(key)
            .build());
        if (getContext().getLog().isDebugEnabled()) {
          log(Level.DEBUG, "Sending input to " + key + ": " + Arrays.asList(value
              .stream().map(input -> input.getClass().getName()).toArray()));
        }
      }
      stepTimingTracker.transitionDispatchCompleted();
      // If the model outputReport have not generated any transitions, output is done. Send output
      // message.
      if (awaitingTransition.isEmpty()) {
        sendOutputs(outputReport.getEventTime());
      }
    }
    return this;
  }

  protected Behavior<DevsMessage> onOutputReportCheck(OutputReportCheck outputReportCheck) {
    if (!haveAllOutputs() && this.timeLast.compareTo(outputReportCheck.checkTime) <= 0) {
      for (String modelId: outputMap.keySet()) {
        if (!outputMap.get(modelId).isPresent()) {
          getContext().getLog().warn("{} waiting for outputReport from {}", modelIdentifier, modelId);
        }
      }
      getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), outputReportCheck);
    }
    return this;
  }

  /**
   * Reports legacy output after the output-driven child transitions have finished. The event time
   * remains the processed common-time instant, while {@code timeNext} is the recomputed child
   * minimum. Enhanced output is sent separately by {@link #finishEnhancedOutput} before transitions.
   *
   * @param eventTime common timestamp of the output-producing step, not its next candidate
   */
  void sendOutputs(T eventTime) {
    stepTimingTracker.stepCompleted();
    parent.tell(OutputReport.<T>builder()
        .eventTime(eventTime)
        .payload(OutputReportPayload.builder().addAllOutputs(modelOutput).build())
        .simulationRunId(simulationRunId)
        .messageId(generateMessageId("OutputReport"))
        .senderId(modelIdentifier)
        .receiverId(parentId)
        .nextInternalTime(timeNext)
        .build());
  }

  /**
   * Handles parent input/transition commands. Legacy mode accepts common event times in the
   * inclusive interval {@code [timeLast, timeNext]}, forwards nonempty routed input bags unchanged,
   * and simply acknowledges an empty input. Enhanced mode delegates to
   * {@link #executeEnhancedTransition}: a scheduled step must have finished output collection at
   * the same canonical time; an external-only step must precede the schedule minimum. Enhanced
   * native adaptation may defer inputs but never move them before their source event.
   *
   * @param executeTransition the {@code ExecuteTransition} message containing the simulation time
   *                          and optional input data for the model.
   * @return the updated behavior of the coordinator after processing the {@code ExecuteTransition}
   * message.
   * @throws RuntimeException if the message's time is not within the expected time range.
   */
  Behavior<DevsMessage> onExecuteTransition(ExecuteTransition<T> executeTransition) {
    if (executionConfig != null) {
      return executeEnhancedTransition(executeTransition);
    }
    if (executeTransition.getEventTime().compareTo(timeLast) < 0
        || executeTransition.getEventTime().compareTo(timeNext) > 0) {
      throw new RuntimeException("Bad synchronization.  " + modelIdentifier
          + " received ExecuteTransitionMessage where time " + executeTransition.getEventTime()
          + " is not between " + timeLast + " and " + timeNext + "inclusive");
    }
    if (executeTransition.getPayload().getInputs().isEmpty()) {
      // Inputs are empty, so nothing to do
      sendTransitionDone(executeTransition.getEventTime());
    } else {
      Map<String, List<PortValue<?>>> receivers =
          couplings.handleInputMessage(executeTransition.getPayload().getInputs());
      if (receivers.isEmpty()) {
        List<String> inputPorts = executeTransition.getPayload().getInputs().stream().map(PortValue::getPortName).toList();
        log(Level.WARN, "Input from " + executeTransition.getSenderId() + "at ports "
                + Arrays.toString(inputPorts.toArray()) + " not handled.");
      }
      awaitingTransition = new ArrayList<>();
      stepTimingTracker.transitionDispatchStarted();
      // Send execute transition messages to those models with input generated from the coordinator
      // input
      validateRecipients(receivers);
      for (String key : childOrder) {
        if (!receivers.containsKey(key)) {
          continue;
        }
        List<PortValue<?>> value = receivers.get(key);
        awaitingTransition.add(key);
        stepTimingTracker.transitionSent(key);
        modelSimulators.get(key).tell(ExecuteTransition.<T>builder()
            .eventTime(executeTransition.getEventTime())
            .payload(ExecuteTransitionPayload.builder().addAllInputs(value).build())
            .simulationRunId(simulationRunId)
            .messageId(generateMessageId("ExecuteTransition"))
            .senderId(modelIdentifier)
            .receiverId(key)
            .build());
      }
      stepTimingTracker.transitionDispatchCompleted();
      getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), new AwaitTransitionCheck());
    }
    return this;
  }

  private void validateRecipients(Map<String, List<PortValue<?>>> recipients) {
    for (String id : recipients.keySet()) {
      if (!modelSimulators.containsKey(id)) {
        throw new RuntimeException("Received input for model " + id
            + " that is not in the modelSimulators map: "
            + Arrays.toString(modelSimulators.keySet().toArray()));
      }
    }
  }

  /**
   * Acknowledges a legacy parent transition with its common event time and the current minimum
   * child next time. This does not convert timestamps or commit enhanced canonical state.
   *
   * @param time the current simulation time associated with the completed transition.
   */
  void sendTransitionDone(T time) {
    parent.tell(
        TransitionComplete.<T>builder()
          .eventTime(time)
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId("TransitionComplete"))
          .senderId(modelIdentifier)
          .receiverId(parentId)
          .nextInternalTime(timeNext)
          .build());
  }

  /**
   * Updates the completing child's candidate and waits for the transition barrier. Legacy mode
   * stores the common next time directly, then uses the final completion's event time for
   * {@code timeLast} and reports either output or transition completion. Enhanced mode delegates to
   * {@link #completeEnhancedChild}, requiring the dispatched native timestamp and its canonical
   * equivalent to match the active step before recomputing the candidate.
   *
   * @param transitionComplete the {@code TransitionDone} message containing the identifier of the
   *                       sender, its next simulation time, and the time of completion for the
   *                       transition.
   * @return the updated behavior of the coordinator after processing the {@code TransitionDone}
   * message.
   */
  Behavior<DevsMessage> onTransitionComplete(TransitionComplete<T> transitionComplete) {
    if (executionConfig != null) {
      return completeEnhancedChild(transitionComplete);
    }
    log(Level.DEBUG, transitionComplete.getSenderId() + " sent TransitionDone with next time of "
        + transitionComplete.getNextInternalTime());
    nextTimeMap.put(transitionComplete.getSenderId(), transitionComplete.getNextInternalTime());
    stepTimingTracker.transitionCompleteReceived(transitionComplete.getSenderId());
    awaitingTransition.remove(transitionComplete.getSenderId());
    if (awaitingTransition.isEmpty()) {
      stepTimingTracker.allTransitionsCompleted();
      timeLast = transitionComplete.getEventTime();
      // System.out.println("Last time for " + modelIdentifier + " is " + timeLast);
      timeNext = getNextTime();
      // System.out.println("In onTransitionDone, Next time for " + modelIdentifier + " is " +
      // timeNext);
      if (!generatingOutput) {
        log(Level.DEBUG, "Sending transiton done.");
        sendTransitionDone(transitionComplete.getEventTime());
      } else {
        log(Level.DEBUG, "Sending outputs.");
        generatingOutput = false;
        sendOutputs(transitionComplete.getEventTime());
      }
    }
    return this;
  }

  protected Behavior<DevsMessage> onAwaitTransitionCheck(AwaitTransitionCheck awaitTransitionCheck) {
    if (!awaitingTransition.isEmpty()) {
      for (String modelId: awaitingTransition) {
        getContext().getLog().warn("{} waiting for transition from {}", modelIdentifier, modelId);
      }
      getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), new AwaitTransitionCheck());
    }
    return this;
  }

  /**
   * Propagates termination and waits for child acknowledgments. Legacy mode forwards the parent's
   * common event time unchanged. Enhanced mode requires a finite canonical termination time at or
   * after {@code canonicalLast} while ready, discards pending deliveries, and terminates each child
   * at its own last initialized/completed timestamp. It neither adapts an unrepresentable horizon
   * nor advances inactive children just to align their local clocks with that horizon.
   *
   * @param simulationTerminate parent termination request and reason on the mode's parent clock
   * @return The updated behavior of the coordinator, maintaining its current state.
   */
  Behavior<DevsMessage> onSimulationTerminate(SimulationTerminate<T> simulationTerminate) {
    if (executionConfig != null) {
      checkEnhancedParent(simulationTerminate);
      DecimalSimTime end = canonicalTime(simulationTerminate.getEventTime());
      if (enhancedPhase != EnhancedPhase.READY || end.compareTo(canonicalLast) < 0) {
        throw new IllegalStateException("Termination during an active step or before last time");
      }
      enhancedPhase = EnhancedPhase.TERMINATING;
      canonicalSchedule.clear();
      routedOutput = List.of();
      awaitingTransition = new ArrayList<>(childOrder);
      for (String id : childOrder) {
        // Shutdown does not create a native step at a potentially unrepresentable parent horizon.
        SimTime local = nativeLast.get(id);
        modelSimulators.get(id).tell(SimulationTerminate.<SimTime>builder()
            .eventTime(local).simulationRunId(simulationRunId)
            .messageId(generateMessageId("SimulationTerminate"))
            .senderId(modelIdentifier).receiverId(id).payload(simulationTerminate.getPayload()).build());
      }
      return this;
    }
    awaitingTransition = new ArrayList<>();
    for (String key : childOrder) {
      awaitingTransition.add(key);
      modelSimulators.get(key).tell(SimulationTerminate.<T>builder()
          .eventTime(simulationTerminate.getEventTime())
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId("SimulationTerminate"))
          .senderId(modelIdentifier)
          .receiverId(key)
          .payload(simulationTerminate.getPayload())
          .build());
    }
    getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), new ModelTerminatedCheck());
    return this;
  }

  /**
   * Handles the completion of a model's operation, represented by the `ModelDone` message. This
   * method updates the internal state by removing the sender from the set of awaiting models. If no
   * models are awaiting transitions, it notifies the parent actor of the completion and stops the
   * actor's behavior. Otherwise, it maintains the current behavior.
   *
   * @param modelTerminated The `ModelDone` message containing the sender's identifier and completion
   *                  information, including the simulation time.
   * @return The updated behavior of the actor after processing the `ModelDone` message.
   */
  Behavior<DevsMessage> onModelTerminated(ModelTerminated<T> modelTerminated) {
    if (executionConfig != null) {
      checkEnhancedResponse(modelTerminated, modelTerminated.getSenderId());
      if (enhancedPhase != EnhancedPhase.TERMINATING
          || !awaitingTransition.contains(modelTerminated.getSenderId())) {
        throw new IllegalArgumentException("Unexpected termination acknowledgment");
      }
    }
    awaitingTransition.remove(modelTerminated.getSenderId());
    if (awaitingTransition.isEmpty()) {
      String timingSummary = stepTimingTracker.formatSummary(modelIdentifier);
      if (!timingSummary.isBlank()) {
        log(Level.INFO, timingSummary);
      }
      parent.tell(ModelTerminated.<T>builder()
          .simulationRunId(simulationRunId)
          .messageId(generateMessageId("ModelTerminated"))
          .senderId(modelIdentifier)
          .receiverId(parentId)
          .build());
      return Behaviors.stopped();
    }
    return this;
  }

  Behavior<DevsMessage> onTerminated(Terminated terminated) {
    getContext().getLog().info("{} Received terminated: {}", modelIdentifier, terminated);
    return Behaviors.same();
  }

  Behavior<DevsMessage> onModelTerminatedCheck(ModelTerminatedCheck modelTerminatedCheck) {
    if (!awaitingTransition.isEmpty()) {
      for (String modelId: awaitingTransition) {
        getContext().getLog().warn("{} waiting for terminations from {}", modelIdentifier, modelId);
      }
      getContext().scheduleOnce(Duration.ofSeconds(10), getContext().getSelf(), new ModelTerminatedCheck());
    }
    return this;
  }

  /**
   * Handles the failure of a child actor.
   *
   * @param childFailed the object containing details about the child actor failure, including the
   *                    cause of the failure.
   * @return the current behavior of the actor after handling the child failure.
   */
  Behavior<DevsMessage> onChildFailed(ChildFailed childFailed) {
    if (executionConfig != null) {
      throw new IllegalStateException("Enhanced child failed", childFailed.cause());
    }
    getContext().getLog()
        .error("Child actor failed with cause " + childFailed.cause().getMessage());
    return this;
  }

  /** Requires a finite canonical parent event time; native timestamps and infinity are not steps. */
  private DecimalSimTime canonicalTime(SimTime time) {
    if (!(time instanceof DecimalSimTime canonical) || canonical.isInfinite()) {
      throw new IllegalArgumentException("Expected finite canonical seconds");
    }
    return canonical;
  }

  /** Checks the parent/run/address and rejects reused message identities before phase/time checks. */
  private void checkEnhancedParent(devs.iso.DevsSimMessage message) {
    if (!java.util.Objects.equals(message.getSenderId(), parentId)
        || !java.util.Objects.equals(message.getReceiverId(), modelIdentifier)
        || !java.util.Objects.equals(message.getSimulationRunId(), simulationRunId)
        || !seenResponseIds.add(new ResponseKey(message.getSenderId(), message.getMessageType(), message.getMessageId()))) {
      throw new IllegalArgumentException("Unexpected enhanced parent message");
    }
  }

  /** Checks child membership, run/address, and message identity before validating native times. */
  private void checkEnhancedResponse(devs.iso.DevsSimMessage message, String child) {
    if (!modelSimulators.containsKey(child)
        || !java.util.Objects.equals(message.getReceiverId(), modelIdentifier)
        || !java.util.Objects.equals(message.getSimulationRunId(), simulationRunId)
        || !seenResponseIds.add(new ResponseKey(child, message.getMessageType(), message.getMessageId()))) {
      throw new IllegalArgumentException("Unexpected, stale, or duplicate child response");
    }
  }

  /**
   * Opens the enhanced output phase at the finite canonical schedule minimum while ready.
   * Only children with internal candidates at that instant are imminent; due input deliveries do
   * not by themselves request target output. Each requested native timestamp must encode the
   * instant exactly. A delivery-only step immediately reports output without child requests.
   *
   * @param request parent output request on the shared canonical clock
   * @return the current behavior collecting output or awaiting the parent's transition command
   * @throws IllegalStateException if the phase or requested time is out of synchronization
   */
  private Behavior<DevsMessage> requestEnhancedOutput(RequestOutput<?> request) {
    checkEnhancedParent(request);
    DecimalSimTime step = canonicalTime(request.getEventTime());
    if (enhancedPhase != EnhancedPhase.READY || !step.equals(canonicalSchedule.next())
        || step.compareTo(canonicalLast) < 0) {
      throw new IllegalStateException("Output request out of synchronization");
    }
    canonicalActive = step;
    enhancedPhase = EnhancedPhase.COLLECTING;
    // A pending delivery may be the minimum even when no child's internal event is imminent.
    imminentModels = canonicalSchedule.imminent(step);
    outputMap = new HashMap<>();
    stepTimingTracker.stepStarted();
    for (String id : imminentModels) {
      outputMap.put(id, Optional.empty());
      SimTime local = childBindings.get(id).exact(step);
      stepTimingTracker.outputRequestSent(id);
      modelSimulators.get(id).tell(RequestOutput.<SimTime>builder().eventTime(local)
          .simulationRunId(simulationRunId).messageId(generateMessageId("RequestOutput"))
          .senderId(modelIdentifier).receiverId(id).build());
    }
    if (imminentModels.isEmpty()) {
      finishEnhancedOutput();
    }
    return this;
  }

  /**
   * Collects one output report per imminent child without committing a transition. Decoded event
   * time must equal {@code canonicalActive}; decoded next time must still equal the child's stored
   * internal candidate, because output collection does not advance model state. Actor response
   * arrival order does not determine routing order.
   *
   * @param report output using the child's native clock, or canonical identity for a coordinator
   * @return the current behavior after collecting output and optionally reporting to the parent
   * @throws IllegalArgumentException if the report, phase, or mapped timestamps are inconsistent
   */
  private Behavior<DevsMessage> receiveEnhancedOutput(OutputReport<?> report) {
    String id = report.getSenderId();
    checkEnhancedResponse(report, id);
    if (enhancedPhase != EnhancedPhase.COLLECTING || !outputMap.containsKey(id)
        || outputMap.get(id).isPresent()
        || !canonicalSchedule.decode(id, report.getEventTime()).equals(canonicalActive)
        || !canonicalSchedule.decode(id, report.getNextInternalTime()).equals(canonicalSchedule.candidate(id))) {
      throw new IllegalArgumentException("Unexpected or inconsistent output report");
    }
    stepTimingTracker.outputReportReceived(id);
    outputMap.put(id, Optional.of(report.getPayload().getOutputs()));
    if (haveAllOutputs()) {
      finishEnhancedOutput();
    }
    return this;
  }

  /**
   * Routes the complete enhanced output bag in configured source-child order and reports it at
   * the unchanged canonical active time, before child transitions. External output stays on the
   * parent's canonical clock without adaptation; any configured budget
   * observes identical source/effective times. Internal routes are retained until the parent
   * commands a transition, when they can be merged with parent inputs and already-due deliveries.
   * The reported next time is the pre-transition schedule minimum, not a newly committed candidate.
   */
  private void finishEnhancedOutput() {
    stepTimingTracker.allOutputsReceived();
    OutputCouplingMessages routed = couplings.handleOutputBag(outputMap, childOrder);
    routedOutput = routed.getRoutedDeliveries();
    for (RoutedDelivery delivery : routedOutput) {
      if (delivery.targetModel().equals(modelIdentifier)) {
        executionConfig.observe(interaction(delivery), canonicalActive, canonicalActive);
      }
    }
    enhancedPhase = EnhancedPhase.OUTPUT_READY;
    parent.tell(OutputReport.<DecimalSimTime>builder().eventTime(canonicalActive)
        .payload(OutputReportPayload.builder().addAllOutputs(routed.getExternalOutputMessages()).build())
        .simulationRunId(simulationRunId).messageId(generateMessageId("OutputReport"))
        .senderId(modelIdentifier).receiverId(parentId)
        .nextInternalTime(canonicalSchedule.next()).build());
  }

  /** Preserves route provenance for queued inputs and optional timing-error diagnostics. */
  private InteractionKey interaction(RoutedDelivery delivery) {
    return new InteractionKey(delivery.sourceModel(), delivery.sourcePort(),
        delivery.targetModel(), delivery.targetPort());
  }

  /**
   * Enqueues child-target routes using {@code canonicalActive} as the actual source event time.
   * {@link CanonicalSchedule#enqueue} applies codec-owned adaptation, validates target native time
   * and causality, observes any budget, and stores both source and effective timestamps. Later
   * effective deliveries remain queued; adaptation never changes the source event's time. Routes
   * to this coupled model are parent outputs already handled on the exact shared clock.
   *
   * @param routes provenance-preserving internal-output or parent-input routes
   */
  private void queueEnhancedRoutes(List<RoutedDelivery> routes) {
    for (RoutedDelivery delivery : routes) {
      if (!delivery.targetModel().equals(modelIdentifier)) {
        canonicalSchedule.enqueue(interaction(delivery), delivery.portValue(), canonicalActive);
      }
    }
  }

  /**
   * Executes the enhanced transition phase at one finite canonical instant. After output collection
   * the command must match {@code canonicalActive}. While ready, only a nonempty external-input
   * command in {@code [canonicalLast, canonicalSchedule.next())} is accepted; it has no imminent
   * internal output to collect.
   *
   * <p>Current internal-output and parent-input routes are adapted/enqueued before draining exactly
   * the deliveries due now. Earlier queued inputs and exact current inputs merge into one bag per
   * target; inputs adapted to later times stay pending. Each imminent or input-receiving child gets
   * one transition, in resolved child order, at the stored delivery timestamp or exactly mapped
   * step. Native timestamps for one bag must agree and dispatch cannot precede that child's last
   * local time. Coincident internal events and input are left to the child's confluent semantics.
   *
   * @param message parent transition/input command on the shared canonical clock
   * @return the current behavior awaiting completions, or reporting completion if no child is due
   * @throws IllegalStateException if synchronization, native bag times, or causality is violated
   */
  private Behavior<DevsMessage> executeEnhancedTransition(ExecuteTransition<?> message) {
    checkEnhancedParent(message);
    DecimalSimTime step = canonicalTime(message.getEventTime());
    if (enhancedPhase == EnhancedPhase.READY) {
      if (step.compareTo(canonicalLast) < 0 || step.compareTo(canonicalSchedule.next()) >= 0
          || message.getPayload().getInputs().isEmpty()) {
        throw new IllegalStateException("External transition out of synchronization");
      }
      canonicalActive = step;
      imminentModels = List.of();
      routedOutput = List.of();
      stepTimingTracker.stepStarted();
      stepTimingTracker.allOutputsReceived();
    } else if (enhancedPhase != EnhancedPhase.OUTPUT_READY || !step.equals(canonicalActive)) {
      throw new IllegalStateException("Transition before output collection or at wrong time");
    }
    // Enqueue before draining: exact current routes join older deliveries due at this same instant.
    queueEnhancedRoutes(routedOutput);
    queueEnhancedRoutes(couplings.handleInputBag(message.getPayload().getInputs()).getRoutedDeliveries());
    Map<String, List<PortValue<?>>> bags = new java.util.LinkedHashMap<>();
    Map<String, SimTime> times = new HashMap<>();
    // Drain only this instant; deferred inputs must not skip intervening internal events.
    for (PendingDelivery delivery : canonicalSchedule.drain(step)) {
      String target = delivery.interaction().targetModel();
      bags.computeIfAbsent(target, ignored -> new ArrayList<>()).add(delivery.portValue());
      SimTime previous = times.put(target, delivery.nativeTime());
      if (previous != null && !previous.equals(delivery.nativeTime())) {
        throw new IllegalStateException("Inconsistent native delivery times");
      }
    }
    awaitingTransition = new ArrayList<>();
    dispatchedTimes.clear();
    enhancedPhase = EnhancedPhase.TRANSITIONING;
    stepTimingTracker.transitionDispatchStarted();
    // One request per child merges input with imminence, allowing a real simulator's confluence.
    for (String id : childOrder) {
      if (!imminentModels.contains(id) && !bags.containsKey(id)) {
        continue;
      }
      SimTime local = times.getOrDefault(id, childBindings.get(id).exact(step));
      if (canonicalSchedule.decode(id, local).compareTo(
          canonicalSchedule.decode(id, nativeLast.get(id))) < 0) {
        throw new IllegalStateException("Native dispatch rolls back time");
      }
      awaitingTransition.add(id);
      dispatchedTimes.put(id, local);
      stepTimingTracker.transitionSent(id);
      modelSimulators.get(id).tell(ExecuteTransition.<SimTime>builder().eventTime(local)
          .payload(ExecuteTransitionPayload.builder().addAllInputs(bags.getOrDefault(id, List.of())).build())
          .simulationRunId(simulationRunId).messageId(generateMessageId("ExecuteTransition"))
          .senderId(modelIdentifier).receiverId(id).build());
    }
    stepTimingTracker.transitionDispatchCompleted();
    if (awaitingTransition.isEmpty()) {
      finishEnhancedTransition();
    }
    return this;
  }

  /**
   * Accepts completion only from an awaited child at the exact dispatched native timestamp, whose
   * decoded value must equal the active canonical instant. Maps the new candidate (including native
   * infinity) and rejects candidates before that instant. Other child candidates remain unchanged;
   * the canonical step is committed only after all dispatched children complete.
   *
   * @param report native/identity completion timestamp and recomputed next internal time
   * @return the current behavior after updating the child and optionally completing the step
   * @throws IllegalArgumentException if completion is unexpected or its timestamps are inconsistent
   */
  private Behavior<DevsMessage> completeEnhancedChild(TransitionComplete<?> report) {
    String id = report.getSenderId();
    checkEnhancedResponse(report, id);
    if (enhancedPhase != EnhancedPhase.TRANSITIONING || !awaitingTransition.contains(id)
        || !report.getEventTime().equals(dispatchedTimes.get(id))
        || !canonicalSchedule.decode(id, report.getEventTime()).equals(canonicalActive)) {
      throw new IllegalArgumentException("Unexpected or inconsistent transition completion");
    }
    canonicalSchedule.update(id, report.getNextInternalTime(), canonicalActive);
    nativeLast.put(id, report.getEventTime());
    awaitingTransition.remove(id);
    stepTimingTracker.transitionCompleteReceived(id);
    if (awaitingTransition.isEmpty()) {
      finishEnhancedTransition();
    }
    return this;
  }

  /**
   * Commits the completed canonical step and releases the barrier to the parent. The next report
   * takes the minimum of updated child internal candidates and remaining effective deliveries;
   * a future queued input can therefore be next even when every child reports infinity. Pending
   * deliveries are deliberately retained, while per-step output and dispatch bookkeeping is reset.
   */
  private void finishEnhancedTransition() {
    stepTimingTracker.allTransitionsCompleted();
    canonicalLast = canonicalActive;
    canonicalActive = null;
    routedOutput = List.of();
    dispatchedTimes.clear();
    enhancedPhase = EnhancedPhase.READY;
    stepTimingTracker.stepCompleted();
    parent.tell(TransitionComplete.<DecimalSimTime>builder().eventTime(canonicalLast)
        .simulationRunId(simulationRunId).messageId(generateMessageId("TransitionComplete"))
        .senderId(modelIdentifier).receiverId(parentId)
        .nextInternalTime(canonicalSchedule.next()).build());
  }
}
