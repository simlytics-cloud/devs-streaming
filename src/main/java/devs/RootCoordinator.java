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

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ChildFailed;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import devs.msg.DevsMessage;
import devs.msg.InitSim;
import devs.msg.InitSimMessage;
import devs.msg.ModelDone;
import devs.msg.ModelOutputMessage;
import devs.msg.NextTime;
import devs.msg.SendOutput;
import devs.msg.SimulationDone;
import devs.msg.time.SimTime;

public class RootCoordinator<T extends SimTime> extends AbstractBehavior<DevsMessage> {

  private T time;
  private final T endTime;
  private final ActorRef<DevsMessage> child;

  public static <TT extends SimTime> Behavior<DevsMessage> create(TT endTime,
      ActorRef<DevsMessage> child) {
    return Behaviors.setup(context -> new RootCoordinator<>(context, endTime, child));
  }

  public RootCoordinator(ActorContext<DevsMessage> context, T endTime,
      ActorRef<DevsMessage> child) {
    super(context);
    this.endTime = endTime;
    this.child = child;
  }

  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(InitSim.class, this::onInitSim);
    builder.onMessage(NextTime.class, this::onNextTime);
    builder.onMessage(ModelOutputMessage.class, this::onModelOutputMessage);
    builder.onMessage(ModelDone.class, this::onModelDone);
    builder.onSignal(ChildFailed.class, this::onChildFailed);

    return builder.build();
  }

  private Behavior<DevsMessage> onInitSim(InitSim<T> initSim) {
    this.time = initSim.getTime();
    child.tell(new InitSimMessage(initSim, getContext().getSelf()));
    return this;
  }

  private Behavior<DevsMessage> onNextTime(NextTime<T> nextTime) {
    time = nextTime.getTime();
    child.tell(SendOutput.builder().time(time).build());
    return this;
  }

  private Behavior<DevsMessage> onModelOutputMessage(ModelOutputMessage<T> modelOutputMessage) {
    if (modelOutputMessage.getNextTime().compareTo(endTime) < 0) {
      time = modelOutputMessage.getNextTime();
      child.tell(SendOutput.builder().time(time).build());
    } else {
      child.tell(SimulationDone.builder().time(time).build());
    }
    return this;
  }

  private Behavior<DevsMessage> onModelDone(ModelDone<T> modelDone) {
    return Behaviors.stopped();
  }
  
  protected Behavior<DevsMessage> onChildFailed(ChildFailed childFailed) {
	  getContext().getLog().error("Child actor failed with cause " + childFailed.cause().getMessage());
	  return this;
  }
}
