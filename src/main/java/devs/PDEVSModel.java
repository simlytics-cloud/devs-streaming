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
import devs.msg.DevsExternalMessage;
import devs.msg.DevsMessage;
import devs.msg.PortValue;
import devs.msg.log.PekkoReceptionistListingResponse;
import devs.msg.time.SimTime;

import org.apache.pekko.actor.typed.Behavior;
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
  protected boolean transitionDone = true;
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

  public void initialize(PDevsSimulator<T, S, ?> simulator) {
    this.simulator = simulator;
  }

  public DevsSimulatorProvider<T> getDevsSimulatorProvider() {
    return new DevsSimulatorProvider<>(this);
  }

  /**
   * A flag to indiate if a PDEVSModel has completed its state transition.  A typical reason for it
   * to be false is when the model is awaiting a response from an external actor or API call
   */
  public boolean isTransitionDone() {
    return transitionDone;
  }

  /**
   * A method to allow a PDEVSModel to interact with external actors with the 
   * <a href="https://pekko.apache.org/docs/pekko/current/typed/interaction-patterns.html#fire-and-forget">
   * Pekko fire and forget interaction pattern</a>, sending messages to external actors and receive
   * responses as a DevsExternalMessage via the PDEVSSimulator.  A typical pattern is, when executing
   * a state transition, the model needs to call an external actor to support calculation of state.
   * It sends a request to the external actor via a tell message, with a replyTo field set to 
   * the PDEVSSimulator's ActorRef, obatined by the call similator.getActorRef();  It then sets the
   * transitionDone field to false so that the simulator knows not to send a TransitionDone yet.
   * When the simulator receives the response via a DevsExternalMessage, it will pass it to this
   * method.  The model can override this method and  now use the data in the response to complete 
   * state transition.  Note that is possible to need data from multiple external sources prior to 
   * completion of state transition.  Once all expected data has been received, the model should set
   * the transitionDone value to true to indicate to the PDEVSSimulator that it is now time to send
   * a TransitionDone message and continue the advance of time.
   * @param externalMessage
   */
  protected void processExternalMessage(DevsExternalMessage externalMessage) {
    transitionDone = true;
  }

  /**
   * Override this method to process receptionist lising for any actors in the actor system that
   * this actor needs to commmunicate with.  See
   * <a href="https://pekko.apache.org/docs/pekko/current/typed/actor-discovery.html#receptionist">
   * Pekko actor discovery</a>.
   * @param receptionistListing
   */
  protected void processReceptionistListing(PekkoReceptionistListingResponse receptionistListing) {

  }

}
