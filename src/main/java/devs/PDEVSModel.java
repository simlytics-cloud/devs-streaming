/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and DEVS Streaming Framework
 * Java contributors. All rights reserved.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a DEVS atomic model.
 *
 * @param <T> the time type used by the model
 * @param <S> the internal state type for the modelState
 */
public abstract class PDEVSModel<T extends SimTime, S> implements PDevsInterface<T, S> {

  protected S modelState;
  protected Logger logger;

  protected PDevsSimulator<T, S, ?> simulator;
  protected final String modelIdentifier;

  /**
   * Constructs a new instance of the PDEVSModel class.
   *
   * @param modelState the initial state of the DEVS model
   * @param modelIdentifier a unique identifier for this model instance
   */
  public PDEVSModel(S modelState, String modelIdentifier) {
    this.modelState = modelState;
    this.modelIdentifier = modelIdentifier;
    this.logger = LoggerFactory.getLogger(getClass());
  }

  public String getModelIdentifier() {
    return modelIdentifier;
  }

  public S getModelState() {
    return modelState;
  }

  public void setSimulator(PDevsSimulator<T, S, ?> simulator) {
    this.simulator = simulator;
  }

  public DevsSimulatorProvider<T> getDevsSimulatorProvider() {
    return new DevsSimulatorProvider<>(this);
  }

}
