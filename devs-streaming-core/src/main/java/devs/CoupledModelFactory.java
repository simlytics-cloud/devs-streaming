/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and DEVS Streaming Framework
 * Java contributors. All rights reserved.
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
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.pekko.Actors;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.utils.ModelUtils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * A factory to create a PDevsCoordinator for a DEVS coupled model consisting of subordinate atomic
 * models.
 * Both modes snapshot providers/bindings and resolve child order when the factory is constructed.
 * No-order overloads sort child identifiers by Java's natural, case-sensitive String order.
 * Custom-order overloads defensively copy an exact permutation of identifiers, rejecting null,
 * blank, duplicate, missing, and unknown entries. The resolved order governs spawning and is
 * forwarded unchanged to the coordinator; it does not serialize child actor execution.
 *
 * @param <T> common simulation time in legacy mode; {@link QuantizedSimTime} in enhanced mode
 */
public class CoupledModelFactory<T extends SimTime> implements SimulatorProvider<T> {

  protected final String modelIdentifier;
  protected final List<SimulatorProvider<T>> simulatorProviders;
  protected final PDevsCouplings couplings;
  protected final Supplier<StepTimingTracker> stepTimingTrackerSupplier;
  private final CoordinatorExecutionConfig executionConfig;
  private final Map<String, ChildBinding<?>> childBindings;
  private final List<String> childOrder;
  private final Map<String, SimulatorProvider<T>> providersById;

  /**
   * Constructs a CoupledModelFactory.
   *
   * @param modelIdentifier the unique string identifying this model
   * @param simulatorProviders providers for the subordinate DEVS model simulators
   * @param couplings a list of PDevsCouplings used to couple subordinate DEVS models
   */
  @Deprecated
  public CoupledModelFactory(
      String modelIdentifier,
      List<SimulatorProvider<T>> simulatorProviders,
      PDevsCouplings couplings) {
    this(modelIdentifier, simulatorProviders, couplings, StepTimingTracker::disabled);
  }

  /**
   * Constructs a legacy common-time factory with a timing tracker for each coordinator.
   *
   * @param modelIdentifier the unique string identifying this model
   * @param simulatorProviders providers whose simulators share the coordinator's time base
   * @param couplings routing between subordinate models and the coupled model
   * @param stepTimingTrackerSupplier supplies a tracker when the coordinator is created
   * @deprecated Use {@link #enhanced(String, List, PDevsCouplings, CoordinatorExecutionConfig,
   *     Supplier)} for explicitly configured canonical-time coordination.
   */
  @Deprecated
  public CoupledModelFactory(
      String modelIdentifier,
      List<SimulatorProvider<T>> simulatorProviders,
      PDevsCouplings couplings,
      Supplier<StepTimingTracker> stepTimingTrackerSupplier) {
    this(modelIdentifier, simulatorProviders, couplings, null, stepTimingTrackerSupplier, false);
  }

  @Deprecated
  public CoupledModelFactory(String modelIdentifier, List<SimulatorProvider<T>> simulatorProviders,
      PDevsCouplings couplings, List<String> childOrder) {
    this(modelIdentifier, simulatorProviders, couplings, childOrder, StepTimingTracker::disabled);
  }

  @Deprecated
  public CoupledModelFactory(String modelIdentifier, List<SimulatorProvider<T>> simulatorProviders,
      PDevsCouplings couplings, List<String> childOrder,
      Supplier<StepTimingTracker> stepTimingTrackerSupplier) {
    this(modelIdentifier, simulatorProviders, couplings,
        Objects.requireNonNull(childOrder), stepTimingTrackerSupplier, true);
  }

  private CoupledModelFactory(String modelIdentifier, List<SimulatorProvider<T>> simulatorProviders,
      PDevsCouplings couplings, List<String> order,
      Supplier<StepTimingTracker> stepTimingTrackerSupplier, boolean custom) {
    this.modelIdentifier = modelIdentifier;
    this.simulatorProviders = List.copyOf(simulatorProviders);
    this.couplings = couplings;
    this.stepTimingTrackerSupplier = stepTimingTrackerSupplier;
    this.executionConfig = null;
    this.childBindings = Map.of();
    Map<String, SimulatorProvider<T>> providers = new LinkedHashMap<>();
    for (SimulatorProvider<T> provider : this.simulatorProviders) {
      if (providers.putIfAbsent(provider.getModelIdentifier(), provider) != null) {
        throw new IllegalArgumentException("Duplicate child identifier: " + provider.getModelIdentifier());
      }
    }
    this.providersById = Map.copyOf(providers);
    this.childOrder = custom ? PDevsCoordinator.customChildOrder(providers.keySet(), order)
        : PDevsCoordinator.defaultChildOrder(providers.keySet());
  }

  /**
   * Constructs an enhanced factory after validating the complete child binding set.
   *
   * @param modelIdentifier identifier of the coupled model
   * @param bindings children with explicit native clock mappings
   * @param couplings routing between children and the coupled model
   * @param executionConfig canonical clock and optional timing-error monitors
   * @param stepTimingTrackerSupplier supplies a tracker when the coordinator is created
   * @throws IllegalArgumentException if child identifiers, order, or clocks are inconsistent
   */
  private CoupledModelFactory(String modelIdentifier, List<ChildBinding<?>> bindings,
      PDevsCouplings couplings, CoordinatorExecutionConfig executionConfig,
      Supplier<StepTimingTracker> stepTimingTrackerSupplier, List<String> order, boolean custom) {
    this.modelIdentifier = Objects.requireNonNull(modelIdentifier);
    this.simulatorProviders = List.of();
    this.couplings = Objects.requireNonNull(couplings);
    this.executionConfig = Objects.requireNonNull(executionConfig);
    this.stepTimingTrackerSupplier = Objects.requireNonNull(stepTimingTrackerSupplier);
    Map<String, ChildBinding<?>> children = new LinkedHashMap<>();
    for (ChildBinding<?> binding : bindings) {
      Objects.requireNonNull(binding).validateClock(executionConfig.clock());
      if (children.putIfAbsent(binding.modelIdentifier(), binding) != null) {
        throw new IllegalArgumentException("Duplicate child identifier: " + binding.modelIdentifier());
      }
    }
    this.childOrder = custom ? PDevsCoordinator.customChildOrder(children.keySet(), order)
        : PDevsCoordinator.defaultChildOrder(children.keySet());
    this.childBindings = Map.copyOf(children);
    this.providersById = Map.of();
  }

  /**
   * Creates a canonical-time factory with timing instrumentation disabled.
   * Child clocks are validated against the execution configuration before actors are spawned.
   *
   * @param modelIdentifier identifier of the coupled model
   * @param bindings native-time providers or already bound actors for the children
   * @param couplings routing between children and the coupled model
   * @param executionConfig canonical clock and optional timing-error monitors
   * @return a factory accepting canonical {@link QuantizedSimTime} initialization times
   * @throws IllegalArgumentException if child identifiers, order, or clocks are inconsistent
   */
  public static CoupledModelFactory<QuantizedSimTime> enhanced(String modelIdentifier,
      List<ChildBinding<?>> bindings, PDevsCouplings couplings,
      CoordinatorExecutionConfig executionConfig) {
    return enhanced(modelIdentifier, bindings, couplings, executionConfig, StepTimingTracker::disabled);
  }

  /**
   * Creates a canonical-time factory with a supplied coordinator timing tracker.
   * Children are spawned in natural identifier order; initialization must be exactly representable
   * on every child's native clock.
   *
   * @param modelIdentifier identifier of the coupled model
   * @param bindings native-time providers or already bound actors for the children
   * @param couplings routing between children and the coupled model
   * @param executionConfig canonical clock and optional timing-error monitors
   * @param stepTimingTrackerSupplier supplies a tracker when the coordinator is created
   * @return a factory accepting canonical {@link QuantizedSimTime} initialization times
   * @throws IllegalArgumentException if child identifiers, order, or clocks are inconsistent
   */
  public static CoupledModelFactory<QuantizedSimTime> enhanced(String modelIdentifier,
      List<ChildBinding<?>> bindings, PDevsCouplings couplings,
      CoordinatorExecutionConfig executionConfig, Supplier<StepTimingTracker> stepTimingTrackerSupplier) {
    return new CoupledModelFactory<>(modelIdentifier, bindings, couplings, executionConfig,
        stepTimingTrackerSupplier, null, false);
  }

  public static CoupledModelFactory<QuantizedSimTime> enhanced(String modelIdentifier,
      List<ChildBinding<?>> bindings, PDevsCouplings couplings,
      CoordinatorExecutionConfig executionConfig, List<String> childOrder) {
    return enhanced(modelIdentifier, bindings, couplings, executionConfig, childOrder,
        StepTimingTracker::disabled);
  }

  public static CoupledModelFactory<QuantizedSimTime> enhanced(String modelIdentifier,
      List<ChildBinding<?>> bindings, PDevsCouplings couplings,
      CoordinatorExecutionConfig executionConfig, List<String> childOrder,
      Supplier<StepTimingTracker> stepTimingTrackerSupplier) {
    return new CoupledModelFactory<>(modelIdentifier, bindings, couplings, executionConfig,
        stepTimingTrackerSupplier, Objects.requireNonNull(childOrder), true);
  }

  /**
   * Indicates whether this factory uses explicit canonical-time coordination.
   *
   * @return {@code true} for an enhanced factory, or {@code false} for a legacy factory
   */
  public boolean isEnhanced() {
    return executionConfig != null;
  }

  /**
   * Returns the enhanced execution configuration, if configured.
   *
   * @return the canonical execution configuration, or {@code null} for a legacy factory
   */
  public CoordinatorExecutionConfig executionConfig() {
    return executionConfig;
  }

  /**
   * Creates the PDevsCoordinator for the coupled model.
   *
   * @param initialTime the initial time for the simulation; enhanced factories require a canonical
   *     time exactly representable on every child's native clock
   * @return the created PDevsCoordinator behavior
   * @throws IllegalArgumentException if enhanced initialization is not canonical or cannot be
   *     represented exactly by a child
   */
  public Behavior<DevsMessage> create(T initialTime) {
    if (isEnhanced()) {
      if (!(initialTime instanceof QuantizedSimTime canonicalTime)) {
        throw new IllegalArgumentException("Enhanced factories require canonical initial time");
      }
      for (ChildBinding<?> binding : childBindings.values()) {
        binding.exact(canonicalTime);
      }
      return Behaviors.setup(context -> {
        Map<String, ActorRef<DevsMessage>> modelSimulators = new LinkedHashMap<>();
        Map<String, ChildBinding<?>> spawnedBindings = new LinkedHashMap<>();
        for (String identifier : childOrder) {
          ChildBinding<?> binding = childBindings.get(identifier);
          ActorRef<DevsMessage> child = binding.spawn(context, canonicalTime);
          context.watch(child);
          modelSimulators.put(identifier, child);
          spawnedBindings.put(identifier, binding.withActor(child));
        }
        return PDevsCoordinator.resolved(modelIdentifier, modelSimulators, couplings,
            executionConfig, spawnedBindings, childOrder, stepTimingTrackerSupplier.get());
      });
    }
    return Behaviors.setup(
        context -> {
          Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
          for (String identifier : childOrder) {
            SimulatorProvider<T> simulatorProvider = providersById.get(identifier);
            ActorRef<DevsMessage> subordinateModel =
                simulatorProvider.provideSimulator(context, initialTime);
            context.watch(subordinateModel);
            modelSimulators.put(identifier, subordinateModel);
          }
          return PDevsCoordinator.resolved(modelIdentifier, modelSimulators, couplings,
              null, Map.of(), childOrder, stepTimingTrackerSupplier.get());
        });
  }

  /** Returns the model identifier of the underlying coupled model. */
  public String getModelIdentifier() {
    return modelIdentifier;
  }

  /** Provides the PDevsCoordinator for the underlying coupled model. */
  @Override
  public ActorRef<DevsMessage> provideSimulator(
      ActorContext<DevsMessage> context, T initialTime) {
    return Actors.spawn(
        context, create(initialTime), ModelUtils.toLegalActorName(modelIdentifier));
  }
}