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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class PDevsCoordinator<T extends SimTime>
    extends AbstractBehavior<DevsMessage> {

  final String modelIdentifier;
  ActorRef<DevsMessage> parent;
  final String parentId;
  final Map<String, ActorRef<DevsMessage>> modelsSimulators;
  private final PDevsCouplings couplings;
  private T timeLast;
  private T timeNext;
  private final Map<String, T> nextTimeMap = new HashMap<>();
  private List<String> imminentModels = new ArrayList<>();
  private Map<String, Optional<Bag>> outputMap = new HashMap<>();

  Map<String, Bag> receivers = new HashMap<>();
  Bag modelOutput;
  private List<String> awaitingTransition = new ArrayList<>();
  private boolean generatingOutput = false;

  public static Behavior<DevsMessage>
  create(String modelIdentifier,
      String parentId,
      Map<String, ActorRef<DevsMessage>> modelsSimulators,
      PDevsCouplings couplings) {
    return Behaviors.setup(context -> new PDevsCoordinator<>(
        modelIdentifier, parentId, modelsSimulators, couplings, context));
  }


  public PDevsCoordinator(String modelIdentifier,
      String parentId,
      Map<String, ActorRef<DevsMessage>> modelsSimulators,
      PDevsCouplings couplings,
      ActorContext<DevsMessage> context) {
    // Check for valid data
    super(context);
    if (modelsSimulators.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot create a PDevsCoordinator with no component models");
    }
    this.modelIdentifier = modelIdentifier;
    this.parentId = parentId;
    this.modelsSimulators = modelsSimulators;
    this.couplings = couplings;
  }

  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();

    builder.onMessage(InitSimMessage.class, this::onInitSimMessage);
    builder.onMessage(SendOutput.class, this::onSendOutputMessage);
    builder.onMessage(ExecuteTransition.class, this::onExecuteTransitionMessage);
    builder.onMessage(NextTime.class, this::onNextTimeMessage);
    builder.onMessage(ModelOutputMessage.class, this::onModelOutputs);
    builder.onMessage(TransitionDone.class, this::onTransitionDone);
    builder.onMessage(SimulationDone.class, this::onSimulationDone);
    builder.onMessage(ModelDone.class, this::onModelDone);

    return builder.build();
  }

  private Behavior<DevsMessage> onInitSimMessage(InitSimMessage<T> initSimMessage) {
    this.parent = initSimMessage.getParent();
    timeLast = initSimMessage.getInitSim().getTime();
    modelsSimulators.values().forEach(s -> {
      s.tell(new InitSimMessage<>(initSimMessage.getInitSim(), getContext().getSelf()));
    });
    return this;
  }

  private T getNextTime() {
    return nextTimeMap.values().stream().min(T::compareTo).get();
  }

  private T buildImminentModels() {
    T minTime = getNextTime();
    imminentModels = nextTimeMap.entrySet().stream()
        .filter(es -> es.getValue().compareTo(minTime) == 0)
        .map(es -> es.getKey()).toList();
    return minTime;
  }

  private Behavior<DevsMessage> onNextTimeMessage(NextTime<T> nextTime) {
    nextTimeMap.put(nextTime.getSender(), nextTime.getTime());

    // If we have all next time messages from models, the next time if the min
    if (nextTimeMap.size() == modelsSimulators.size()) {
      timeNext = getNextTime();
      parent.tell(NextTime.builder().time(timeNext).sender(modelIdentifier).build());
    }
    return this;
  }

  private Behavior<DevsMessage> onSendOutputMessage(SendOutput<T> sendOutput) {
    if (sendOutput.getTime().compareTo(timeNext) != 0) {
      throw new RuntimeException("Bad synchronization.  Received SendOutputMessage where time " +
          sendOutput.getTime() + " did not equal " + timeNext);
    }
    generatingOutput = true;
    buildImminentModels();
    outputMap = new HashMap<>();
    imminentModels.forEach(m -> {
      modelsSimulators.get(m).tell(sendOutput);
      outputMap.put(m, Optional.empty());
    });
    return this;
  }

  private boolean haveAllOutputs() {
    return outputMap.values().stream().filter(o -> o.isEmpty()).findFirst().isEmpty();
  }

  private Behavior<DevsMessage> onModelOutputs(ModelOutputMessage<T> outputs) {
    getContext().getLog().debug("Got model output:" + outputs.getModelOutput().getClass().getName() + "\n");

    outputs.getModelOutput().getPortValueList().forEach(portValue -> getContext().getLog().debug(
        "  " + portValue.getPortIdentifier() + ": " + portValue.getValue()));
    outputMap.put(outputs.getSender(), Optional.of(outputs.getModelOutput()));

    if (haveAllOutputs()) {
      // Send outputs to parent based on mappings and translations
      // Send inputs to children based on mappings and translations
      awaitingTransition = new ArrayList<>();
      OutputCouplingMessages outputCouplingMessages = couplings.handleOutputBag(outputMap);
      modelOutput = outputCouplingMessages.getOutputMessage();
      receivers = outputCouplingMessages.getInternalMessages();

      receivers.forEach((key, value) -> {
        awaitingTransition.add(key);
        modelsSimulators.get(key).tell(ExecuteTransition.builder()
            .time(outputs.getTime()).modelInputsOption(value).build());
      });

      // For models not in receivers, tell them to execute internal transitioin to current time
      List<String> internalTransitions = imminentModels.stream()
          .filter(s -> !receivers.containsKey(s)).toList();
      internalTransitions.forEach(modelId -> {
        awaitingTransition.add(modelId);
        modelsSimulators.get(modelId)
            .tell(ExecuteTransition.builder().time(outputs.getTime()).build());
      });
      // If the model outputs have not generated any transitions, output is done.  Send output message.
      if (awaitingTransition.isEmpty()) {
        sendOutputs(outputs.getTime());
      }
    }
    return this;
  }

  private void sendOutputs(T time) {
    parent.tell(ModelOutputMessage.builder()
        .modelOutput(modelOutput)
        .nextTime(timeNext)
        .time(time)
        .sender(modelIdentifier)
        .build());
  }

  private Behavior<DevsMessage> onExecuteTransitionMessage(ExecuteTransition<T> executeTransition) {
    if (executeTransition.getTime().compareTo(timeLast) < 0 ||
        executeTransition.getTime().compareTo(timeNext) > 0) {
      throw new RuntimeException(
          "Bad synchronization.  Received ExecuteTransitionMessage where time " +
              executeTransition.getTime() + " is not between " + timeLast + " and " + timeNext
              + "inclusive");
    }
    if (executeTransition.getModelInputsOption().isEmpty()) {
      // Inputs are empty, so nothing to do
      sendTransitionDone(executeTransition.getTime());
    } else {
      Map<String, Bag> receivers = couplings.handleInputMessage(
          executeTransition.getModelInputsOption().get());
      awaitingTransition = new ArrayList<>();
      // Send execute transition messages to those models with input generated from the coordinator input
      receivers.forEach((key, value) -> {
        awaitingTransition.add(key);
        modelsSimulators.get(key).tell(ExecuteTransition.builder().time(
            executeTransition.getTime()).modelInputsOption(value).build());
      });
    }
    return this;
  }

  private void sendTransitionDone(T time) {
    parent.tell(TransitionDone.builder()
        .nextTime(timeNext)
        .time(time)
        .sender(modelIdentifier)
        .build());
  }

  private Behavior<DevsMessage> onTransitionDone(TransitionDone<T> transitionDone) {
    nextTimeMap.put(transitionDone.getSender(), transitionDone.getNextTime());
    awaitingTransition.remove(transitionDone.getSender());
    if (awaitingTransition.isEmpty()) {
      timeLast = transitionDone.getTime();
      timeNext = getNextTime();
      if (!generatingOutput) {
        sendTransitionDone(transitionDone.getTime());
      } else {
        sendOutputs(transitionDone.getTime());
      }
    }
    return this;
  }

  private Behavior<DevsMessage> onSimulationDone(SimulationDone<T> simulationDone) {
    awaitingTransition = new ArrayList<>();
    modelsSimulators.values().forEach(modelSimulator -> modelSimulator.tell(simulationDone));
    return this;
  }

  private Behavior<DevsMessage> onModelDone(ModelDone<T> modelDone) {
    awaitingTransition.remove(modelDone.getSender());
    if (awaitingTransition.isEmpty()) {
      parent.tell(ModelDone.builder().time(modelDone.getTime()).sender(modelIdentifier).build());
      return Behaviors.stopped();
    }
    return this;
  }
}



