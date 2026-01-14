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

import devs.iso.PortValue;
import devs.iso.time.SimTime;
import devs.utils.Schedule;
import java.util.ArrayList;
import java.util.List;

/**
 * The ScheduledDevsModel abstract class represents a DEVS (Discrete Event System Specification)
 * model with a schedule-driven simulation workflow. This class extends PDEVSModel and provides
 * implementations for the DEVS formalism's time-advance, output, internal, external, and confluent
 * state transition functions according to the parallel DEVS specification.
 *
 * <p>In this model, all outputs are stored on the schedule as {@link PortValue} objects at their
 * scheduled times. When a state transition occurs, the output function retrieves any PortValues
 * from the first entry of the schedule (if they exist), and the internal state transition clears
 * those outputs from the schedule before executing the scheduled state transition logic.
 *
 * @param <T> A type parameter representing the simulation time type, which must extend the abstract
 *            SimTime class.
 * @param <S> A type parameter representing the state type of the DEVS model.
 */
public abstract class ScheduledDevsModel<T extends SimTime, S> extends PDEVSModel<T, S> {
  
  protected final Schedule<T> schedule;
  
  /**
   * Constructs a new instance of ScheduledDevsModel.
   *
   * @param modelState the initial state of the DEVS model
   * @param modelIdentifier a unique identifier for this model instance
   */
  public ScheduledDevsModel(S modelState, String modelIdentifier) {
    super(modelState, modelIdentifier);
    this.schedule = new Schedule<>();
  }

  public ScheduledDevsModel(S modelState, String modelIdentifier, Schedule<T> schedule) {
    super(modelState, modelIdentifier);
    this.schedule = schedule;
  }
  

  /**
   * Executes the internal state transition function for the DEVS model. According to the parallel
   * DEVS specification, this is called when the elapsed time since the last transition equals
   * the time advance. This method:
   * <ol>
   *   <li>Clears any outputs (PortValues) from the first entry of the schedule</li>
   *   <li>Delegates to the scheduled internal state transition implementation</li>
   * </ol>
   */
  @Override
  public void internalStateTransitionFunction(T currentTime) {
    schedule.removeCurrentScheduledOutput(currentTime);
    scheduledInternalStateTransitionFunction(currentTime);
  }

  /**
   * Executes the scheduled internal state transition logic for the DEVS model. This abstract
   * method is invoked during the internal state transition process when a scheduled state change
   * is due. The implementation should define the specific behavior and changes to the model's
   * state. According to the parallel DEVS specification, the model stores the current time as
   * part of its state.
   */
  public abstract void scheduledInternalStateTransitionFunction(T currentTime);

  /**
   * Executes the scheduled external state transition logic for the DEVS model. This abstract
   * method is invoked when an external event arrives at the model's input ports during the
   * transition process.
   *
   * @param elapsedTime the time elapsed since the last state transition
   * @param inputs a List containing external input events (as PortValues) received by the model
   */
  public abstract void scheduledExternalStateTransitionFunction(T elapsedTime, List<PortValue<?>> inputs);

  /**
   * Executes the external state transition function for the DEVS model. This method delegates
   * directly to the scheduled external state transition implementation.
   *
   * @param currentTime the time elapsed since the last state transition
   * @param inputs a List containing external input events received by the model
   */
  @Override
  public void externalStateTransitionFunction(T currentTime, List<PortValue<?>> inputs) {
    scheduledExternalStateTransitionFunction(currentTime, inputs);
  }

  /**
   * Executes the scheduled confluent state transition logic for the DEVS (Discrete Event System
   * Specification) model. This abstract method is invoked when a confluent state transition is
   * required, which occurs when both an external and internal state transition are scheduled to
   * occur simultaneously. The method processes the provided external inputs along with managing
   * the internal state of the model.
   *
   * @param currentTime the time elapsed since the last state transition (equals timeAdvance for
   *                    internal transitions)
   * @param inputs a List containing external input events (as PortValues) received by the model
   *               at the current time
   */
  public abstract void scheduledConfluentStateTransitionFunction(T currentTime, List<PortValue<?>> inputs);

  /**
   * Executes the confluent state transition function for the DEVS model. This method clears any
   * outputs (PortValues) from the schedule and delegates the actual confluent state transition
   * logic to the scheduled implementation defined by the subclass.
   *
   * @param currentTime the time elapsed since the last state transition
   * @param inputs a List containing external input events received by the model
   */
  @Override
  public void confluentStateTransitionFunction(T currentTime, List<PortValue<?>> inputs) {
    schedule.removeCurrentScheduledOutput(currentTime);
    scheduledConfluentStateTransitionFunction(currentTime, inputs);
  }

  /**
   * Determines the next scheduled internal state transition time for the model based on its
   * current state. If the schedule is empty, the method returns the maximum time value (indicating
   * no further transitions). Otherwise, it returns the time advance based on the first scheduled
   * event time.
   *
   * @return the time until the next scheduled internal state transition, or the maximum time
   * value if the schedule is empty
   */
  @Override
  public T timeAdvanceFunction(T currentTime) {
    if (schedule.isEmpty()) {
      return (T) currentTime.getTimeUntilMax();
    } else {
      T nextScheduledTime = schedule.getFirstEventTime();
      return (T) nextScheduledTime.minus(currentTime);
    }
  }

  /**
   * Generates the output of the DEVS model by collecting all outputs (PortValues) from the first
   * scheduled entry. According to the parallel DEVS specification, this method is called
   * immediately prior to an internal state transition, allowing the model to generate outputs
   * before its state changes.
   *
   * @return a List containing all PortValues (outputs) from the first scheduled entry. If there
   * are no outputs, an empty list is returned.
   */
  @Override
  public List<PortValue<?>> outputFunction() {
    return schedule.getCurrentScheduledOutput();
  }
}
