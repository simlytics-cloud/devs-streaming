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

package devs.experimentalframe;

import devs.ScheduledDevsModel;
import devs.iso.PortValue;
import devs.iso.time.SimTime;
import devs.msg.state.ScheduleState;
import java.util.List;


/**
 * Represents an abstract Generator model as part of the PDEVS (Parallel Discrete Event System)
 * framework. The Generator is responsible for producing scheduled events on predefined ports
 * during a simulation. It operates based on an internal schedule and does not handle external
 * events.
 *
 * @param <T> The type representing simulation time, extending the SimTime class.
 */
public abstract class Generator<T extends SimTime> extends ScheduledDevsModel<T, ScheduleState<T>> {

  /**
   * Constructs a new instance of the Generator class.
   *
   * @param modelIdentifier a unique string identifier for this Generator instance
   * @param scheduleState the schedule state with initial time and scheduled events
   */
  protected Generator(String modelIdentifier, ScheduleState<T> scheduleState) {
    super(scheduleState, modelIdentifier);
  }
  
  

  @Override
  public void externalStateTransitionFunction(T elapsedTime, List<PortValue<?>> inputs) {
    // No external events
    throw new IllegalArgumentException("Generator does not expect external events.  \n"
        + "Got event with port identifier of "
        + inputs.get(0).getPortName());

  }

  @Override
  public void confluentStateTransitionFunction(List<PortValue<?>> inputs) {
    // Will throw an error.  No external events allowed to a generator
    throw new IllegalArgumentException("Generator does not expect external events.  \n"
        + "Got event with port identifier of "
        + inputs.get(0).getPortName());
  }
  
}
