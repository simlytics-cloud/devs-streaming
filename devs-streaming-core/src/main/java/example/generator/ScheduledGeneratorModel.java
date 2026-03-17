/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and 
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

package example.generator;

import devs.utils.Schedule;
import devs.utils.Schedule.ScheduledEvent;
import java.util.ArrayList;

import devs.PDEVSModel;
import devs.Port;
import devs.ScheduledDevsModel;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import java.util.List;

public class ScheduledGeneratorModel 
    extends ScheduledDevsModel<LongSimTime, ScheduledGeneratorModelState> {

  public static record FlipState() { }
  /**
   * Represents the output port of the GeneratorModel, used for emitting the current state of the
   * model during its execution. The port is identified with the name "OUTPUT" and is associated
   * with the data type {@link Integer}.
   * <p>
   * This port is primarily utilized to convey the periodic state transitions of the GeneratorModel
   * (0 or 1). It is designed to output the value of the model's current state whenever the output
   * function is invoked.
   * <p>
   * The data shared through this port is encapsulated in instances of PortValue.
   */
  public static final Port<Integer> generatorOutputPort = new Port<>("OUTPUT", Integer.class);


  /**
   * Constructs a new instance of the GeneratorModel with the given initial state.
   *
   * @param initialState the initial state of the GeneratorModel. This determines the starting state
   *                     of the model, either 0 or 1, and sets up the behavior of the periodic state
   *                     transitions.
   */
  public ScheduledGeneratorModel(int initialState, String identifier) {
    super(new ScheduledGeneratorModelState(initialState), identifier);
    LongSimTime t1 = LongSimTime.create(1L);
    modelState.getSchedule().scheduleInternalEvent(t1, new FlipState());
    modelState.getSchedule().scheduleOutputEvent(t1, generatorOutputPort.createPortValue(0));
  }


  @Override
  public void handleScheduledEvents(List<Object> events) {

    for (Object event: events) {
      if (event instanceof FlipState) {
        if (modelState.getiState() == 0) {
          this.modelState.setiState(1);
        } else {
          this.modelState.setiState(0);
        }
        Long timeAdvance = modelState.getiState() == 0 ? 1L : 0L;
        LongSimTime nextTime = LongSimTime.create(modelState.getCurrentTime().getT() + timeAdvance);
        modelState.getSchedule().scheduleOutputEvent(nextTime, generatorOutputPort.createPortValue(modelState.getiState()));
        modelState.getSchedule().scheduleInternalEvent(nextTime, new FlipState());
      }
    }

  }

  @Override
  public void externalStateTransitionFunction(LongSimTime elapsedTime,
      List<PortValue<?>> inputs) {
    // Nothing to do.  Generator should not get any inputs
  }

  @Override
  public void confluentStateTransitionFunction(List<PortValue<?>> inputs) {
    // Nothing to do.  Generator should not get any inputs
  }
}
