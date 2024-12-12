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

package devs.msg;

import devs.msg.time.SimTime;
import org.apache.pekko.actor.typed.ActorRef;

public class InitSimMessage<T extends SimTime> implements DevsMessage {

  private final ActorRef<DevsMessage> parent;
  private final InitSim<T> initSim;

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
