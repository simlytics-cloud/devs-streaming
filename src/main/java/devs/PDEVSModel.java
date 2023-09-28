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
import devs.msg.time.SimTime;

public abstract class PDEVSModel<T extends SimTime, S> {

  protected S modelState;

  protected PDevsSimulator<T, S, ?> simulator;
  final protected String modelIdentifier;

  public PDEVSModel(S modelState, String modelIdentifier) {
    this.modelState = modelState;
    this.modelIdentifier = modelIdentifier;
  }


  protected abstract void internalStateTransitionFunction(T currentTime);

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
