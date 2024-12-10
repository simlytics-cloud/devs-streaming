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

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.*;
import devs.msg.log.DevsLogMessage;
import devs.msg.log.DevsModelLogMessage;
import devs.msg.log.StateMessage;
import devs.msg.time.SimTime;
import devs.utils.DevsObjectMapper;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;

import java.nio.charset.StandardCharsets;

public class StateLoggingSimulator<T extends SimTime, S,
    M extends PDEVSModel<T, S>> extends PDevsSimulator<T, S, M> {

  final ActorRef<DevsLogMessage> loggingActor;
  final Serialization serialization;
  final ObjectMapper objectMapper;

  public static <TT extends SimTime, SS,
      MM extends PDEVSModel<TT, SS>> Behavior<DevsMessage> create(MM aDevsModel, TT initialTime, ActorRef<DevsLogMessage> loggingActor) {
    return Behaviors.setup(
        context -> new StateLoggingSimulator<>(aDevsModel, initialTime, context,
            loggingActor));
  }

  public StateLoggingSimulator(M devsModel, T initialTime, ActorContext<DevsMessage> context,
      ActorRef<DevsLogMessage> loggingActor) {
    super(devsModel, initialTime, context);
    this.loggingActor = loggingActor;
    this.serialization = SerializationExtension.get(context.getSystem());
    this.objectMapper = DevsObjectMapper.buildObjectMapper();
  }

  String serialize(DevsMessage devsMessage) {
    return new String(serialization.serialize(devsMessage).get(), StandardCharsets.UTF_8);
  }

  protected void logState(T simTime) {
    StateMessage<?, ?> stateMessage = StateMessage.builder()
        .modelId(devsModel.getModelIdentifier())
        .modelState(devsModel.getModelState())
        .time(simTime)
        .build();
    loggingActor.tell(stateMessage);
  }

  @Override
  protected Behavior<DevsMessage> onExecuteTransitionMessage(
      ExecuteTransition<T> executeTransition) {
    Behavior<DevsMessage> behavior = super.onExecuteTransitionMessage(executeTransition);
    DevsModelLogMessage<?> devsModelLogMessage = DevsModelLogMessage.builder()
        .time(executeTransition.getTime())
        .modelId(devsModel.getModelIdentifier())
        .devsMessage(executeTransition)
        .build();
    loggingActor.tell(devsModelLogMessage);
    logState(executeTransition.getTime());
    return behavior;
  }

  @Override
  protected Behavior<DevsMessage> onInitSimMessage(InitSimMessage<T> initSimMessage) {
    Behavior<DevsMessage> behavior = super.onInitSimMessage(initSimMessage);
    logState(initSimMessage.getInitSim().getTime());
    return behavior;
  }

  @Override
  protected Behavior<DevsMessage> onSendOutputMessage(SendOutput<T> sendOutput) {
    if (sendOutput.getTime().compareTo(timeNext) != 0) {
      throw new RuntimeException("Bad synchronization.  Received SendOutputMessage where time "
          + sendOutput.getTime() + " did not equal " + timeNext);
    }
    Bag modelOutput = devsModel.outputFunction();
    ModelOutputMessage<?> modelOutputMessage = ModelOutputMessage.builder()
        .modelOutput(modelOutput)
        .nextTime(timeNext)
        .time(sendOutput.getTime())
        .sender(devsModel.getModelIdentifier())
        .build();
    parent.tell(modelOutputMessage);
    DevsModelLogMessage<?> devsModelLogMessage = DevsModelLogMessage.builder()
        .time(sendOutput.getTime())
        .modelId(devsModel.getModelIdentifier())
        .devsMessage(modelOutputMessage)
        .build();
    loggingActor.tell(devsModelLogMessage);
    return this;
  }


}
