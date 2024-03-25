/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs;

import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.SimTime;

/**
 * This class implements a DEVS atomic model
 * @param <T> the time type used by the model
 * @param <S> the internal state type for the modelState
 */
public abstract class PDEVSModel<T extends SimTime, S> {

  protected S modelState;

  protected PDevsSimulator<T, S, ?> simulator;
  final protected String modelIdentifier;

  public PDEVSModel(S modelState, String modelIdentifier) {
    this.modelState = modelState;
    this.modelIdentifier = modelIdentifier;
  }

  /**
   * The DEVS internal state transition function.  This function is called when the model is executing a
   * schedule internal transition that occurs with the passage of time
   * @param currentTime the current time of the transition
   */
  protected abstract void internalStateTransitionFunction(T currentTime);

  /**
   * The DEVS external state transition function.  This function is called when an external event arrives at
   * one of the model's input ports.  Note two important considerations.  First, the type of event is not
   * known in advance, so this function will have to use the {@link PortValue PortValues} contained in the
   * input {@link devs.msg.Bag Bag} to identify the type of event and process it appropriately.  In addition,
   * more than one input is possible at an intant in time.
   * @param currentTime the current time of the transition
   * @param bag the bag of inputs that has arrived at the current time.
   */
  protected abstract void externalSateTransitionFunction(T currentTime, Bag bag);

  protected abstract void confluentStateTransitionFunction(T currentTime, Bag bag);

  protected abstract T timeAdvanceFunction(T currentTime);

  protected abstract Bag outputFunction();

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
