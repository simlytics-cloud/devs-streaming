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

package devs;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ChildFailed;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.slf4j.Logger;
import org.slf4j.event.Level;


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
  private boolean generatingOutput;

  public static Behavior<DevsMessage> create(String modelIdentifier, String parentId,
                                             Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings) {
    return Behaviors.setup(context -> new PDevsCoordinator<>(modelIdentifier, parentId,
        modelsSimulators, couplings, context));
  }


  public PDevsCoordinator(String modelIdentifier, String parentId,
                          Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
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

  public String getModelIdentifier() {
    return modelIdentifier;
  }

  protected boolean shouldLog(String message) {
    return true;
  }

  protected void log(Level level, String message) {
    if (shouldLog(message)) {
      Logger logger = getContext().getLog();
      if (logger.isTraceEnabled() && level.toInt() >= Level.TRACE.toInt()) {
        logger.trace(message);
      } else if (logger.isDebugEnabled() && level.toInt() >= Level.DEBUG.toInt()) {
        logger.debug(message);
      } else if (logger.isInfoEnabled() && level.toInt() >= Level.INFO.toInt()) {
        logger.info(message);
      }
      if (logger.isWarnEnabled() && level.toInt() >= Level.INFO.toInt()) {
        logger.warn(message);
      }
      if (logger.isErrorEnabled() && level.toInt() >= Level.INFO.toInt()) {
        logger.error(message);
      }
    }

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
    builder.onSignal(ChildFailed.class, this::onChildFailed);

    return builder.build();
  }

  private Behavior<DevsMessage> onInitSimMessage(InitSimMessage<T> initSimMessage) {
    this.parent = initSimMessage.getParent();
    timeLast = initSimMessage.getInitSim().getTime();
    // System.out.println("Last time for " + modelIdentifier + " is " + timeLast);
    modelsSimulators.values().forEach(
        s -> s.tell(new InitSimMessage<>(initSimMessage.getInitSim(), getContext().getSelf())));
    return this;
  }

  private T getNextTime() {
    return nextTimeMap.values().stream().min(T::compareTo).get();
  }

  private T buildImminentModels() {
    T minTime = getNextTime();
    imminentModels = nextTimeMap.entrySet().stream()
        .filter(es -> es.getValue().compareTo(minTime) == 0).map(es -> es.getKey()).toList();
    return minTime;
  }

  private Behavior<DevsMessage> onNextTimeMessage(NextTime<T> nextTime) {
    // System.out.println("Next time for " + nextTime.getSender() + " is " + nextTime.getTime());
    nextTimeMap.put(nextTime.getSender(), nextTime.getTime());

    // If we have all next time messages from models, the next time if the min
    if (nextTimeMap.size() == modelsSimulators.size()) {
      timeNext = getNextTime();
      // System.out.println("In onNextTimeMessage, Next time for " + modelIdentifier + " is " +
      // timeNext);
      parent.tell(NextTime.builder().time(timeNext).sender(modelIdentifier).build());
    }
    return this;
  }

  private Behavior<DevsMessage> onSendOutputMessage(SendOutput<T> sendOutput) {
    if (sendOutput.getTime().compareTo(timeNext) != 0) {
      throw new RuntimeException("Bad synchronization.  Received SendOutputMessage where time "
          + sendOutput.getTime() + " did not equal " + timeNext);
    }
    generatingOutput = true;
    buildImminentModels();
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG, "Immenent models are " + Arrays.toString(imminentModels.toArray()));
    }
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
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG,
          "Got model outputs at " + outputs.getNextTime() + " from " + outputs.getSender() + ": "
              + Arrays.toString(outputs.getModelOutput().getPortValueList().stream()
              .map(pv -> pv.getPortIdentifier()).toArray()));
    }
    // outputs.getModelOutput().getPortValueList().forEach(portValue -> getContext().getLog().debug(
    // " " + portValue.getPortIdentifier() + ": " + portValue.getValue()));
    outputMap.put(outputs.getSender(), Optional.of(outputs.getModelOutput()));
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG, "Have outputs from " + Arrays.toString(outputMap.keySet().toArray()));
    }

    if (haveAllOutputs()) {
      // Send outputs to parent based on mappings and translations
      // Send inputs to children based on mappings and translations
      log(Level.DEBUG, "We have all outputs.");
      awaitingTransition = new ArrayList<>();
      OutputCouplingMessages outputCouplingMessages = couplings.handleOutputBag(outputMap);
      modelOutput = outputCouplingMessages.getOutputMessage();
      receivers = outputCouplingMessages.getInternalMessages();

      receivers.forEach((key, value) -> {
        awaitingTransition.add(key);
        modelsSimulators.get(key).tell(
            ExecuteTransition.builder().time(outputs.getTime()).modelInputsOption(value).build());
        if (getContext().getLog().isDebugEnabled()) {
          log(Level.DEBUG, "Sending input to " + key + ": " + Arrays.asList(value.getPortValueList()
              .stream().map(input -> input.getClass().getName()).toArray()));
        }
      });

      // For models not in receivers, tell them to execute internal transition to current time
      List<String> internalTransitions =
          imminentModels.stream().filter(s -> !receivers.containsKey(s)).toList();
      if (getContext().getLog().isDebugEnabled()) {
        log(Level.DEBUG,
            "Imminent models executing transition: " + Arrays.asList(internalTransitions));
      }
      internalTransitions.forEach(modelId -> {
        awaitingTransition.add(modelId);
        modelsSimulators.get(modelId)
            .tell(ExecuteTransition.builder().time(outputs.getTime()).build());
      });
      // If the model outputs have not generated any transitions, output is done. Send output
      // message.
      if (awaitingTransition.isEmpty()) {
        sendOutputs(outputs.getTime());
      }
    }
    return this;
  }

  private void sendOutputs(T time) {
    parent.tell(ModelOutputMessage.builder().modelOutput(modelOutput).nextTime(timeNext).time(time)
        .sender(modelIdentifier).build());
  }

  private Behavior<DevsMessage> onExecuteTransitionMessage(ExecuteTransition<T> executeTransition) {
    if (executeTransition.getTime().compareTo(timeLast) < 0
        || executeTransition.getTime().compareTo(timeNext) > 0) {
      throw new RuntimeException("Bad synchronization.  " + modelIdentifier
          + " received ExecuteTransitionMessage where time " + executeTransition.getTime()
          + " is not between " + timeLast + " and " + timeNext + "inclusive");
    }
    if (executeTransition.getModelInputsOption().isEmpty()) {
      // Inputs are empty, so nothing to do
      sendTransitionDone(executeTransition.getTime());
    } else {
      Map<String, Bag> receivers =
          couplings.handleInputMessage(executeTransition.getModelInputsOption().get());
      awaitingTransition = new ArrayList<>();
      // Send execute transition messages to those models with input generated from the coordinator
      // input
      receivers.forEach((key, value) -> {
        awaitingTransition.add(key);
        modelsSimulators.get(key).tell(ExecuteTransition.builder().time(executeTransition.getTime())
            .modelInputsOption(value).build());
      });
    }
    return this;
  }

  private void sendTransitionDone(T time) {
    parent.tell(
        TransitionDone.builder().nextTime(timeNext).time(time).sender(modelIdentifier).build());
  }

  private Behavior<DevsMessage> onTransitionDone(TransitionDone<T> transitionDone) {
    log(Level.DEBUG, transitionDone.getSender() + " sent TransitionDone with next time of "
        + transitionDone.getNextTime());
    nextTimeMap.put(transitionDone.getSender(), transitionDone.getNextTime());
    awaitingTransition.remove(transitionDone.getSender());
    if (awaitingTransition.isEmpty()) {
      timeLast = transitionDone.getTime();
      // System.out.println("Last time for " + modelIdentifier + " is " + timeLast);
      timeNext = getNextTime();
      // System.out.println("In onTransitionDone, Next time for " + modelIdentifier + " is " +
      // timeNext);
      if (!generatingOutput) {
        log(Level.DEBUG, "Sending transiton done.");
        sendTransitionDone(transitionDone.getTime());
      } else {
        log(Level.DEBUG, "Sending outputs.");
        generatingOutput = false;
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

  protected Behavior<DevsMessage> onChildFailed(ChildFailed childFailed) {
    getContext().getLog()
        .error("Child actor failed with cause " + childFailed.cause().getMessage());
    return this;
  }
}
