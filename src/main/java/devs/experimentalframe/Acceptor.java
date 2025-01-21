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

import devs.PDEVSModel;
import devs.msg.Bag;
import devs.msg.time.SimTime;

/**
 * The Acceptor class is an abstract DEVS (Discrete Event System Specification) model that
 * represents a model processing incoming events without generating any output events.  Users of
 * this class should override the externalStateTransitionFunction to validate that the incoming
 * messages are correct.
 *
 * @param <T> the time type used by the model, extending the SimTime class
 * @param <S> the internal state type of the model
 */
public abstract class Acceptor<T extends SimTime, S> extends PDEVSModel<T, S> {

  public static String modelIdentifier = "Acceptor";

  public Acceptor(S initialState) {
    super(initialState, modelIdentifier);
  }

  @Override
  public void internalStateTransitionFunction(T currentTime) {
    // Nothing to do

  }


  @Override
  public void confluentStateTransitionFunction(T currentTime, Bag bag) {
    externalStateTransitionFunction(currentTime, bag);
    internalStateTransitionFunction(currentTime);

  }

  @Override
  public T timeAdvanceFunction(T currentTime) {
    return (T) currentTime.getMaxValue().minus(currentTime);
  }

  @Override
  public Bag outputFunction() {
    // No output from Acceptor
    return Bag.builder().build();
  }

}
