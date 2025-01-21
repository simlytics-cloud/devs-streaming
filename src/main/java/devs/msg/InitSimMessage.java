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

package devs.msg;

import devs.msg.time.SimTime;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Represents a message used to initialize a simulation within the DEVS framework.
 * <p>
 * This message is passed to an actor to initiate the simulation process. It encapsulates the
 * initialization details along with a reference to the parent actor for communication.
 *
 * @param <T> the type of simulation time associated with the initialization.
 */
public class InitSimMessage<T extends SimTime> implements DevsMessage {

  private final ActorRef<DevsMessage> parent;
  private final InitSim<T> initSim;

  /**
   * Constructs an InitSimMessage instance to initialize a simulation.
   *
   * @param initSim the initialization configuration for the simulation.
   * @param parent  the reference to the parent actor for communication.
   */
  public InitSimMessage(InitSim<T> initSim, ActorRef<DevsMessage> parent) {
    this.parent = parent;
    this.initSim = initSim;
  }

  public ActorRef<DevsMessage> getParent() {
    return parent;
  }

  public InitSim<T> getInitSim() {
    return initSim;
  }
}
