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
import devs.iso.time.LongSimTime;
import devs.iso.time.SimTime;
import devs.msg.state.ScheduleState;
import devs.utils.Schedule;
import devs.utils.Schedule.ScheduledEvent;
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
public abstract class ScheduledDevsModel<T extends SimTime, S extends ScheduleState<T>> extends PDEVSModel<T, S> {


  /**
   * Constructs a new instance of the PDEVSModel class.
   *
   * @param modelState      the initial state of the DEVS model
   * @param modelIdentifier a unique identifier for this model instance
   */
  public ScheduledDevsModel(S modelState, String modelIdentifier) {
    super(modelState, modelIdentifier);
  }
  
  @Override
  public void internalStateTransitionFunction() {
    T currentTime = (T)modelState.getCurrentTime().plus((T)timeAdvanceFunction());
    modelState.setCurrentTime(currentTime);
    modelState.getSchedule().removeCurrentScheduledOutput(currentTime);
    ArrayList<Object> events = new ArrayList<>(modelState.getSchedule()
        .removeCurrentScheduledEvents(currentTime));
    handleScheduledEvents(events);
  }
  
  public abstract void handleScheduledEvents(List<Object> events);
  

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
  public T timeAdvanceFunction() {
    if (modelState.getSchedule().isEmpty()) {
      return (T) modelState.getCurrentTime().getTimeUntilMax();
    } else {
      T nextScheduledTime = modelState.getSchedule().getFirstEventTime();
      return (T) nextScheduledTime.minus(modelState.getCurrentTime());
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
    return modelState.getSchedule().getCurrentScheduledOutput();
  }
}
