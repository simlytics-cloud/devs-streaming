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

import devs.Port;
import devs.ScheduledDevsModel;
import devs.msg.Bag;
import devs.msg.time.SimTime;
import devs.utils.Schedule;
import java.util.Map;


/**
 * Represents an abstract Generator model as part of the PDEVS (Parallel Discrete Event System)
 * framework. The Generator is responsible for producing scheduled events on predefined ports
 * during a simulation. It operates based on an internal schedule and does not handle external
 * events.
 *
 * @param <T> The type representing simulation time, extending the SimTime class.
 */
public abstract class Generator<T extends SimTime> extends ScheduledDevsModel<T, Void> {


  public static final String MODEL_ID = "Generator";
  protected final Map<String, Port<?>> ports;

  /**
   * Constructs a new instance of the Generator class.
   *
   * @param modelIdentifier a unique string identifier for this Generator instance
   * @param schedule the schedule associated with the Generator, used to manage
   *                 the timing and firing of events
   */
  protected Generator(String modelIdentifier, Schedule<T> schedule) {
    super(null, modelIdentifier, schedule);
    this.ports = buildPorts();
  }


  protected abstract Map<String, Port<?>> buildPorts();

  @Override
  public void externalStateTransitionFunction(T currentTime, Bag bag) {
    // No external events
    throw new IllegalArgumentException("Generator does not expect external events.  \n"
        + "Got event with port identifier of "
        + bag.getPortValueList().get(0).getPortIdentifier());

  }

  @Override
  public void confluentStateTransitionFunction(T currentTime, Bag bag) {
    externalStateTransitionFunction(currentTime, bag); // Will throw an error. No external
    // events expected
    internalStateTransitionFunction(currentTime);
  }


  public Map<String, Port<?>> getPorts() {
    return ports;
  }


}
