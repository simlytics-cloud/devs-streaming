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


/**
 * The PDevsCoordinator class is responsible for coordinating the simulation execution of multiple
 * child components in a Parallel DEVS (P-DEVS) simulation model. It implements the behaviors
 * required for model initialization, message handling, output generation, and state transitions.
 * The coordinator acts as the parent controller for child simulators or models within a
 * hierarchical simulation.
 *
 * @param <T> The type parameter representing simulation time. The simulation time must extend the
 *            {@link SimTime} abstraction.
 */
public class PDevsCoordinator<T extends SimTime>
    extends AbstractBehavior<DevsMessage> {

  final String modelIdentifier;
  ActorRef<DevsMessage> parent;
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

  public static Behavior<DevsMessage> create(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings) {
    return Behaviors.setup(context -> new PDevsCoordinator<>(modelIdentifier,
        modelsSimulators, couplings, context));
  }


  /**
   * Constructs a new instance of PDevsCoordinator to manage the coordination of Parallel DEVS
   * simulations, including its models, simulators, and couplings.
   *
   * @param modelIdentifier  the unique identifier of the coordinator model.
   * @param modelsSimulators a map containing the identifiers of component models and their
   *                         associated simulator actor references.
   * @param couplings        the coupling information defining input-output relationships between
   *                         the component models.
   * @param context          the actor context for the current Pekko actor.
   * @throws IllegalArgumentException if the modelsSimulators map is empty.
   */
  public PDevsCoordinator(String modelIdentifier,
      Map<String, ActorRef<DevsMessage>> modelsSimulators, PDevsCouplings couplings,
      ActorContext<DevsMessage> context) {
    // Check for valid data
    super(context);
    if (modelsSimulators.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot create a PDevsCoordinator with no component models");
    }
    this.modelIdentifier = modelIdentifier;
    this.modelsSimulators = modelsSimulators;
    this.couplings = couplings;
  }

  public String getModelIdentifier() {
    return modelIdentifier;
  }

  protected boolean shouldLog(String message) {
    return true;
  }

  /**
   * Logs a message at the specified severity level, if logging conditions are met. This method
   * determines whether the message should be logged using {@code shouldLog(String message)} and
   * delegates the logging operation to the appropriate logger based on the severity level.
   *
   * @param level   the severity level of the log message (e.g., TRACE, DEBUG, INFO, WARN, ERROR).
   * @param message the log message to be recorded.
   */
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

  /**
   * Creates a {@code Receive} instance for the {@code PDevsCoordinator} actor, defining the message
   * handling logic. The method builds a builder with message handlers for various
   * {@code DevsMessage} subtypes and actor signals.
   *
   * <p>The handlers process messages related to initializing the simulation, managing outputs,
   * executing transitions, handling time updates, and other simulation-related operations.
   *
   * @return a {@code Receive} object that specifies the actor's message handling behavior.
   */
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

  /**
   * Handles an {@link InitSimMessage} to initialize the simulation. This includes updating the
   * `parent` reference, saving the initial simulation time, and propagating the initialization
   * message to all registered model simulators.
   *
   * @param initSimMessage The {@code InitSimMessage} containing the initial simulation
   *                       configuration and the reference to the parent actor.
   * @return The updated behavior of the actor after processing the initialization message.
   */
  private Behavior<DevsMessage> onInitSimMessage(InitSimMessage<T> initSimMessage) {
    this.parent = initSimMessage.getParent();
    timeLast = initSimMessage.getInitSim().getTime();
    // System.out.println("Last time for " + modelIdentifier + " is " + timeLast);
    modelsSimulators.values().forEach(
        s -> s.tell(new InitSimMessage<>(initSimMessage.getInitSim(), getContext().getSelf())));
    return this;
  }

  /**
   * Retrieves the next scheduled simulation time from the collection of scheduled times. The method
   * identifies the minimum time value from the entries in `nextTimeMap`.
   *
   * @return The next simulation time of type {@code T}, representing the minimum value present in
   * the `nextTimeMap`.
   */
  T getNextTime() {
    return nextTimeMap.values().stream().min(T::compareTo).get();
  }

  /**
   * Builds the list of imminent models and retrieves the next scheduled simulation time. The method
   * determines the imminent models by collecting all models associated with the next simulation
   * time (minimum time) from the `nextTimeMap`.
   *
   * @return The next simulation time of type {@code T}, representing the earliest scheduled time
   * from the `nextTimeMap`.
   */
  T buildImminentModels() {
    T minTime = getNextTime();
    imminentModels = nextTimeMap.entrySet().stream()
        .filter(es -> es.getValue().compareTo(minTime) == 0).map(es -> es.getKey()).toList();
    return minTime;
  }

  /**
   * Handles the reception of a {@code NextTime} message, which communicates the next simulation
   * time from a model's simulator. Updates the internal mapping of next times for each simulator
   * and calculates the minimum next time once all simulators have reported their times.
   *
   * <p>If all next times have been received, the method determines the global next simulation time
   * and notifies the parent actor.
   *
   * @param nextTime The {@code NextTime} message containing the sender's identifier and the next
   *                 simulation time determined by the sender model's simulator.
   * @return The updated behavior of this actor after processing the {@code NextTime} message.
   */
  Behavior<DevsMessage> onNextTimeMessage(NextTime<T> nextTime) {
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

  /**
   * Handles the `SendOutput` message for managing the output generation process within the
   * PDevsCoordinator. This involves verifying synchronization, building imminent models, and
   * notifying the simulators of the imminent models to generate outputs.
   *
   * @param sendOutput The `SendOutput` message containing the current simulation time and a signal
   *                   to trigger output generation for imminent models.
   * @return The updated behavior of this actor after processing the `SendOutput` message.
   * @throws RuntimeException if the simulation time in the `SendOutput` message does not match the
   *                          expected next simulation time (`timeNext`).
   */
  Behavior<DevsMessage> onSendOutputMessage(SendOutput<T> sendOutput) {
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

  /**
   * Determines whether all outputs in the output map are complete. The method checks the values in
   * the `outputMap` to verify if any output is empty, indicating an incomplete state. If no empty
   * outputs are found, the method returns {@code true}, confirming that all outputs are present.
   *
   * @return {@code true} if all outputs in the `outputMap` are non-empty, {@code false} otherwise.
   */
  private boolean haveAllOutputs() {
    return outputMap.values().stream().filter(o -> o.isEmpty()).findFirst().isEmpty();
  }

  /**
   * Handles the model output messages, processes them, and coordinates the next steps
   * such as forwarding messages to child models, sending outputs to parent models,
   * and executing transitions. This method ensures that all outputs are received
   * and processed appropriately before proceeding.
   *
   * @param outputs the {@link ModelOutputMessage} containing the outputs from a model,
   *                including the sender, the next simulation time, and the associated
   *                port-value list.
   * @return the current behavior of the actor, allowing for further processing.
   */
  Behavior<DevsMessage> onModelOutputs(ModelOutputMessage<T> outputs) {
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG,
          "Got model outputs at " + timeNext + " from " + outputs.getSender() + ": "
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
            ExecuteTransition.builder().time(timeNext).modelInputsOption(value)
                .build());
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
            .tell(ExecuteTransition.builder().time(timeNext).build());
      });
      // If the model outputs have not generated any transitions, output is done. Send output
      // message.
      if (awaitingTransition.isEmpty()) {
        sendOutputs();
      }
    }
    return this;
  }

  /**
   * Sends the output data encapsulated in a ModelOutputMessage to the parent.
   *
   * The message includes the current model output, the next scheduled time for processing,
   * and the identifier of the sender. The message is built using the ModelOutputMessage builder
   * and sent to the parent using the `tell` method.
   */
  void sendOutputs() {
    parent.tell(ModelOutputMessage.builder().modelOutput(modelOutput).nextTime(timeNext)
        .sender(modelIdentifier).build());
  }

  /**
   * Handles an {@code ExecuteTransition} message to execute the transition function for the
   * simulation model. This method validates the synchronization of the message's time against the
   * current simulation time, processes inputs if provided, and forwards transition requests to the
   * appropriate models with input data.
   *
   * @param executeTransition the {@code ExecuteTransition} message containing the simulation time
   *                          and optional input data for the model.
   * @return the updated behavior of the coordinator after processing the {@code ExecuteTransition}
   * message.
   * @throws RuntimeException if the message's time is not within the expected time range.
   */
  Behavior<DevsMessage> onExecuteTransitionMessage(ExecuteTransition<T> executeTransition) {
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

  /**
   * Sends a {@code TransitionDone} message to the parent actor, indicating the completion of a
   * transition for the current simulation step.
   *
   * @param time the current simulation time associated with the completed transition.
   */
  void sendTransitionDone(T time) {
    parent.tell(
        TransitionDone.builder().nextTime(timeNext).time(time).sender(modelIdentifier).build());
  }

  /**
   * Handles the receipt of a {@code TransitionDone} message from a model or simulator, processes it
   * by updating internal state, and determines subsequent simulation steps. If all awaiting
   * transitions have completed, it advances the simulation's state and triggers the next actions,
   * such as sending outputs or notifying the parent of transition completion.
   *
   * @param transitionDone the {@code TransitionDone} message containing the identifier of the
   *                       sender, its next simulation time, and the time of completion for the
   *                       transition.
   * @return the updated behavior of the coordinator after processing the {@code TransitionDone}
   * message.
   */
  Behavior<DevsMessage> onTransitionDone(TransitionDone<T> transitionDone) {
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
        sendOutputs();
      }
    }
    return this;
  }

  /**
   * Handles the completion of the simulation by propagating the {@code SimulationDone} message to
   * all registered model simulators. This method updates the internal state to prepare for the next
   * stage of simulation or finalization.
   *
   * @param simulationDone The {@code SimulationDone} message indicating that the simulation process
   *                       has concluded.
   * @return The updated behavior of the coordinator, maintaining its current state.
   */
  Behavior<DevsMessage> onSimulationDone(SimulationDone<T> simulationDone) {
    awaitingTransition = new ArrayList<>();
    modelsSimulators.values().forEach(modelSimulator -> modelSimulator.tell(simulationDone));
    return this;
  }

  /**
   * Handles the completion of a model's operation, represented by the `ModelDone` message. This
   * method updates the internal state by removing the sender from the set of awaiting models. If no
   * models are awaiting transitions, it notifies the parent actor of the completion and stops the
   * actor's behavior. Otherwise, it maintains the current behavior.
   *
   * @param modelDone The `ModelDone` message containing the sender's identifier and completion
   *                  information, including the simulation time.
   * @return The updated behavior of the actor after processing the `ModelDone` message.
   */
  Behavior<DevsMessage> onModelDone(ModelDone<T> modelDone) {
    awaitingTransition.remove(modelDone.getSender());
    if (awaitingTransition.isEmpty()) {
      parent.tell(ModelDone.builder().time(modelDone.getTime()).sender(modelIdentifier).build());
      return Behaviors.stopped();
    }
    return this;
  }

  /**
   * Handles the failure of a child actor.
   *
   * @param childFailed the object containing details about the child actor failure, including the
   *                    cause of the failure.
   * @return the current behavior of the actor after handling the child failure.
   */
  Behavior<DevsMessage> onChildFailed(ChildFailed childFailed) {
    getContext().getLog()
        .error("Child actor failed with cause " + childFailed.cause().getMessage());
    return this;
  }
}
