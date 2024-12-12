/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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
 */

package devs;

import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.SimTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a DEVS atomic model.

 * @param <T> the time type used by the model
 * @param <S> the internal state type for the modelState
 */
public abstract class PDEVSModel<T extends SimTime, S> {

  protected S modelState;
  protected Logger logger;

  protected PDevsSimulator<T, S, ?> simulator;
  protected final String modelIdentifier;

  public PDEVSModel(S modelState, String modelIdentifier) {
    this.modelState = modelState;
    this.modelIdentifier = modelIdentifier;
    this.logger = LoggerFactory.getLogger(getClass());
  }

  /**
   * The DEVS internal state transition function. This function is called when the model is
   * executing a schedule internal transition that occurs with the passage of time

   * @param currentTime the current time of the transition
   */
  public abstract void internalStateTransitionFunction(T currentTime);

  /**
   * The DEVS external state transition function. This function is called when an external event
   * arrives at one of the model's input ports. Note two important considerations. First, the type
   * of event is not known in advance, so this function will have to use the {@link PortValue
   * PortValue} contained in the input {@link devs.msg.Bag Bag} to identify the type of event and
   * process it appropriately. In addition, more than one input is possible at an intant in time.

   * @param currentTime the current time of the transition
   * @param bag the bag of inputs that has arrived at the current time
   */
  public abstract void externalStateTransitionFunction(T currentTime, Bag bag);

  /**
   * The parallel DEVS (PDEVS) confluent transition function. This function is called if an external
   * event arrives at the same time the DEVS model is also sheculed for an internal state
   * transition. A trivial implementation could call the external state transition function, then
   * the internal state transition function. In other cases, the order may depend on the type of
   * event received.

   * @param currentTime the current time of the transition
   * @param bag the bag of inputs that has arrived at the current time
   */
  public abstract void confluentStateTransitionFunction(T currentTime, Bag bag);

  /**
   * This is the DEVS time advance function. The DEVS simulator calls this method to determine the
   * time of this model's next internal state transition.

   * @param currentTime the simulator's current time
   * @return the time of the next scheduled internal state transition
   */
  public abstract T timeAdvanceFunction(T currentTime);

  /**
   * The DEVS output function. The simulator calls this function immediately prior to an internal
   * state transition. Its implementation adds any outputs from this model to the
   * {@link devs.msg.Bag Bag}.

   * @return the {@link devs.msg.Bag Bag} of outputs
   */
  public abstract Bag outputFunction();

  public String getModelIdentifier() {
    return modelIdentifier;
  }

  public S getModelState() {
    return modelState;
  }

  public void setSimulator(PDevsSimulator<T, S, ?> simulator) {
    this.simulator = simulator;
  }
}
