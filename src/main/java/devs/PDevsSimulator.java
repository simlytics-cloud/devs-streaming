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
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import devs.msg.Bag;
import devs.msg.DevsMessage;
import devs.msg.ExecuteTransition;
import devs.msg.InitSimMessage;
import devs.msg.ModelDone;
import devs.msg.ModelOutputMessage;
import devs.msg.NextTime;
import devs.msg.SendOutput;
import devs.msg.SimulationDone;
import devs.msg.TransitionDone;
import devs.msg.time.SimTime;

public class PDevsSimulator<T extends SimTime, S,
    M extends PDEVSModel<T, S>> extends AbstractBehavior<DevsMessage> {

  protected T timeLast;
  protected T timeNext;
  protected ActorRef<DevsMessage> parent;

  protected final M devsModel;

  public static <TT extends SimTime, SS,
      MM extends PDEVSModel<TT, SS>> Behavior<DevsMessage>
  create(MM aDevsModel, TT initialTime) {
    return Behaviors.setup(context -> new PDevsSimulator(aDevsModel, initialTime, context));
  }

  public PDevsSimulator(M devsModel, T initialTime, ActorContext<DevsMessage> context) {
    super(context);
    this.devsModel = devsModel;
    this.timeLast = initialTime;
    this.timeNext = initialTime;
    devsModel.setSimulator(this);
  }

  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();

    builder.onMessage(InitSimMessage.class, this::onInitSimMessage);
    builder.onMessage(SendOutput.class, this::onSendOutputMessage);
    builder.onMessage(ExecuteTransition.class, this::onExecuteTransitionMessage);
    builder.onMessage(SimulationDone.class, this::onSimulationDone);

    return builder.build();
  }

  protected Behavior<DevsMessage> onInitSimMessage(InitSimMessage<T> initSimMessage) {
    this.parent = initSimMessage.getParent();
    timeNext = (T) timeLast.plus(
        devsModel.timeAdvanceFunction(initSimMessage.getInitSim().getTime()));
    parent.tell(NextTime.builder().time(timeNext).sender(devsModel.getModelIdentifier()).build());
    return this;
  }

  protected Behavior<DevsMessage> onSendOutputMessage(SendOutput<T> sendOutput) {
    if (sendOutput.getTime().compareTo(timeNext) != 0) {
      throw new RuntimeException("Bad synchronization.  Received SendOutputMessage where time " +
          sendOutput.getTime() + " did not equal " + timeNext);
    }
    Bag modelOutput = devsModel.outputFunction();
    parent.tell(ModelOutputMessage.builder()
        .modelOutput(modelOutput)
        .nextTime(timeNext)
        .time(sendOutput.getTime())
        .sender(devsModel.getModelIdentifier())
        .build());
    return this;
  }

  protected Behavior<DevsMessage> onExecuteTransitionMessage(
      ExecuteTransition<T> executeTransition) {
    if (executeTransition.getTime().compareTo(timeLast) < 0 ||
        executeTransition.getTime().compareTo(timeNext) > 0) {
      throw new RuntimeException(
          "Bad synchronization.  Received ExecuteTransitionMessage where time " +
              executeTransition.getTime() + " is not between " + timeLast + " and " + timeNext
              + "inclusive");
    }
    if (executeTransition.getTime().compareTo(timeNext) == 0) {
      if (executeTransition.getModelInputsOption().isEmpty()) {
        return internalStateTransition(executeTransition.getTime());
      } else {
        return confluentStateTransition(executeTransition.getTime(),
            executeTransition.getModelInputsOption().get());
      }
    } else {
      return externalStateTransition(executeTransition.getTime(),
          executeTransition.getModelInputsOption().get());
    }
  }

  protected void transitionDome(T time) {
    timeLast = time;
    timeNext = devsModel.timeAdvanceFunction(time);
    parent.tell(TransitionDone.builder()
        .nextTime(timeNext)
        .time(time)
        .sender(devsModel.getModelIdentifier())
        .build());
  }

  protected Behavior<DevsMessage> internalStateTransition(T time) {
    devsModel.internalStateTransitionFunction(time);
    transitionDome(time);
    return this;
  }

  protected Behavior<DevsMessage> confluentStateTransition(T time, Bag input) {
    devsModel.confluentStateTransitionFunction(time, input);
    transitionDome(time);
    return this;
  }

  protected Behavior<DevsMessage> externalStateTransition(T time, Bag input) {
    devsModel.externalSateTransitionFunction(time, input);
    transitionDome(time);
    return this;
  }

  protected Behavior<DevsMessage> onSimulationDone(SimulationDone<T> simulationDone) {
    parent.tell(
        ModelDone.builder().time(simulationDone.getTime()).sender(devsModel.getModelIdentifier())
            .build());
    return Behaviors.stopped();
  }
}
