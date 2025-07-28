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

import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.SimTime;
import devs.utils.Schedule;
import java.util.ArrayList;
import java.util.List;

/**
 * The ScheduledDevsModel interface represents a DEVS (Discrete Event System Specification) model
 * with a schedule-driven simulation workflow. This interface extends the PDevsInterface and
 * enforces methods for handling scheduled state transitions, internal and confluent state
 * transitions, and output processing within the DEVS framework. The design provides default
 * implementations for the DEVS formalism's time-advance, output, internal, and confluent state
 * transition functions.
 *
 * @param <T> A type parameter representing the simulation time type, which must extend the abstract
 *            SimTime class.
 * @param <S> A type parameter representing the state type of the DEVS model.
 */
public interface ScheduledDevsModel<T extends SimTime, S> extends PDevsInterface<T, S> {


  /**
   * Retrieves the schedule associated with this DEVS model. The schedule stores events mapped to
   * their respective time points and is used for managing state transitions and outputs within the
   * simulation.
   *
   * @return the {@code Schedule<T>} containing events and their associated time points for this
   * DEVS model.
   */
  public abstract Schedule<T> getSchedule();

  /**
   * Executes the internal state transition function for the DEVS model at the specified simulation
   * time. This method clears any pending output events from the internal schedule and delegates to
   * the implementation of the scheduled internal state transition logic defined by the subclass.
   *
   * @param currentTime the current simulation time at which the internal state transition occurs
   */
  @Override
  default public void internalStateTransitionFunction(T currentTime) {
    clearScheduledOutput();
    scheduledInternalStateTransitionFunction(currentTime);
  }


  /**
   * Executes the scheduled internal state transition logic for the DEVS model at the specified
   * simulation time. This abstract method is invoked during the internal state transition process
   * when a scheduled state change is due. The implementation should define the specific behavior
   * and changes to the model's state at the given time.
   *
   * @param currentTime the current simulation time at which the scheduled internal state transition
   *                    occurs
   */
  public abstract void scheduledInternalStateTransitionFunction(T currentTime);

  /**
   * Executes the scheduled confluent state transition logic for the DEVS (Discrete Event System
   * Specification) model at the given simulation time. This abstract method is invoked when a
   * confluent state transition is required, which occurs when both an external and internal state
   * transition are scheduled to occur simultaneously. The method processes the provided external
   * inputs (if any) along with managing the internal state of the model.
   *
   * @param currentTime the current simulation time at which the confluent state transition occurs
   * @param bag         a Bag containing external input events received by the model at the
   *                    specified simulation time
   */
  public abstract void scheduledConfluentStateTransitionFunction(T currentTime, Bag bag);

  /**
   * Executes the confluent state transition function for the DEVS model at the specified simulation
   * time. This method clears any pending output events from the schedule and delegates the actual
   * confluent state transition logic to the scheduled implementation defined by the subclass.
   *
   * @param currentTime the current simulation time at which the confluent state transition occurs
   * @param bag         a Bag containing external input events received by the model at the
   *                    specified simulation time
   */
  @Override
  default void confluentStateTransitionFunction(T currentTime, Bag bag) {
    clearScheduledOutput();
    scheduledConfluentStateTransitionFunction(currentTime, bag);
  }

  /**
   * Determines the next scheduled internal state transition time for the model. If the schedule is
   * empty, the method returns the maximum time value (indicating no further transitions).
   * Otherwise, it returns the next scheduled time from the internal schedule.
   *
   * @param currentTime the current time of the simulator
   * @return the time of the next scheduled internal state transition, or the maximum time value if
   * the schedule is empty
   */
  @Override
  default public T timeAdvanceFunction(T currentTime) {
    Schedule<T> schedule = getSchedule();
    T nextTime = null;
    if (schedule.isEmpty()) {
      nextTime = (T) currentTime.getMaxValue();
    } else {
      nextTime = schedule.firstKey();
    }
    T timeAdvance = (T) nextTime.minus(currentTime);
    return timeAdvance;
  }

  /**
   * Checks if there is any pending output in the current state of the schedule.
   * <p>
   * The method iterates through the first entry in the schedule. If an event of type
   * {@code PortValue<?>} is found, it indicates that there is pending output. The method logs the
   * current state of the schedule and whether a pending output is detected before returning.
   *
   * @return {@code true} if there is pending output based on the current schedule, {@code false}
   * otherwise
   */
  default public boolean hasScheduleOutput() {
    Schedule<T> schedule = getSchedule();
    if (!schedule.isEmpty()) {
      for (Object event : schedule.firstEntry().getValue()) {
        if (event instanceof PortValue<?>) {
          break;
        }
      }
    }
    return false;
  }

  /**
   * Retrieves a list of pending outputs from the current schedule.
   * <p>
   * The method checks the first entry in the schedule and extracts events of type
   * {@code PortValue<?>}. These extracted events are considered as pending outputs. Additionally,
   * it logs the current state of the schedule and the pending outputs for debugging purposes.
   *
   * @return a list of {@code PortValue<?>} representing the pending outputs from the schedule. If
   * the schedule is empty, an empty list is returned.
   */
  default public List<PortValue<?>> getScheduledOutput() {
    Schedule<T> schedule = getSchedule();
    List<PortValue<?>> pendingOutputs = new ArrayList<>();
    if (!schedule.isEmpty()) {
      for (Object event : schedule.firstEntry().getValue()) {
        if (event instanceof PortValue<?> pv) {
          pendingOutputs.add(pv);
        }
      }
    }
    return pendingOutputs;
  }

  /**
   * Clears any pending output events from the schedule associated with the DEVS model.
   * <p>
   * This method checks the current schedule for any pending events and removes those classified as
   * {@code PortValue<?>}. If the first scheduled event list becomes empty after removing all
   * {@code PortValue<?>} entries, the corresponding key is also removed from the schedule.
   * Debugging information is logged both before and after the schedule is updated, providing
   * insights into the clearing process.
   * <p>
   * The method ensures that only non-output related events remain in the schedule, maintaining
   * consistency in the DEVS model's state transitions.
   */
  default public void clearScheduledOutput() {
    Schedule<T> schedule = getSchedule();
    if (!schedule.isEmpty()) {
      ArrayList<Object> currentEvents = schedule.firstEntry().getValue();
      ArrayList<Object> portValues = new ArrayList<>();
      // Find all the port values
      for (Object event : currentEvents) {
        if (event instanceof PortValue<?> pv) {
          portValues.add(pv);
        }
      }
      // Remove port values from schedule
      for (Object pv : portValues) {
        currentEvents.remove(pv);
      }
      if (currentEvents.isEmpty()) {
        schedule.remove(schedule.firstKey());
      } else {
        schedule.put(schedule.firstKey(), currentEvents);
      }
    }
  }

  /**
   * Generates the output of the DEVS model by collecting all pending output events from the
   * schedule. The pending outputs are aggregated into a Bag object for further processing or
   * dispatching.
   *
   * @return a Bag containing all pending output events collected from the current schedule. If
   * there are no pending outputs, an empty Bag is returned.
   */
  @Override
  default public Bag outputFunction() {
    Bag.Builder bagBuilder = Bag.builder();
    bagBuilder.addAllPortValueList(getScheduledOutput());
    return bagBuilder.build();
  }


}
