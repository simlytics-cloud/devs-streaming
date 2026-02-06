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


import com.fasterxml.jackson.core.JsonProcessingException;
import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.OutputReportPayload;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.TransitionComplete;
import devs.iso.time.SimTime;
import devs.utils.DevsObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ChildFailed;
import org.apache.pekko.actor.typed.Terminated;
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
  String simulationId;
  ActorRef<DevsMessage> parent;
  String parentId;
  final Map<String, ActorRef<DevsMessage>> modelSimulators;
  private final PDevsCouplings couplings;
  private T timeLast;
  private T timeNext;
  private final Map<String, T> nextTimeMap = new HashMap<>();
  private List<String> imminentModels = new ArrayList<>();
  private Map<String, Optional<List<PortValue<?>>>> outputMap = new HashMap<>();

  Map<String, List<PortValue<?>>> receivers = new HashMap<>();
  List<PortValue<?>> modelOutput;
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
    this.modelSimulators = modelsSimulators;
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

    builder.onMessage(SimulationInitMessage.class, this::onSimulationInit);
    builder.onMessage(RequestOutput.class, this::onRequestOutput);
    builder.onMessage(ExecuteTransition.class, this::onExecuteTransition);
    builder.onMessage(NextInternalTimeReport.class, this::onNextInternalTimeReport);
    builder.onMessage(OutputReport.class, this::onOutputReport);
    builder.onMessage(TransitionComplete.class, this::onTransitionComplete);
    builder.onMessage(SimulationTerminate.class, this::onSimulationTerminate);
    builder.onMessage(ModelTerminated.class, this::onModelTerminated);
    builder.onSignal(Terminated.class, this::onTerminated);
    builder.onSignal(ChildFailed.class, this::onChildFailed);

    return builder.build();
  }

  /**
   * Handles an {@link SimulationInitMessage} to initialize the simulation. This includes updating the
   * `parent` reference, saving the initial simulation time, and propagating the initialization
   * message to all registered model simulators.
   *
   * @param tSimulationInitMessage The {@code InitSimMessage} containing the initial simulation
   *                       configuration and the reference to the parent actor.
   * @return The updated behavior of the actor after processing the initialization message.
   */
  private Behavior<DevsMessage> onSimulationInit(SimulationInitMessage<T> tSimulationInitMessage) {
    this.parent = tSimulationInitMessage.getParent();
    this.parentId = tSimulationInitMessage.getSimulationInit().getSenderId();
    this.simulationId = tSimulationInitMessage.getSimulationInit().getSimulationId();
    timeLast = tSimulationInitMessage.getSimulationInit().getEventTime();
    // System.out.println("Last time for " + modelIdentifier + " is " + timeLast);

    modelSimulators.forEach(
        (modelId, model) -> {
          SimulationInit<T> coordinatorInit = SimulationInit.<T>builder()
              .eventTime(tSimulationInitMessage.getSimulationInit().getEventTime())
              .simulationId(tSimulationInitMessage.getSimulationInit().getSimulationId())
              .messageId(generateMessageId(""))
              .senderId(modelIdentifier)
              .receiverId(modelId)
              .build();
          model.tell(new SimulationInitMessage<>(coordinatorInit, getContext().getSelf()));
        });
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

  protected String generateMessageId(String messageType) {
    return java.util.UUID.randomUUID().toString();
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
   * @param nextInternalTimeReport The {@code NextTime} message containing the sender's identifier and the next
   *                 simulation time determined by the sender model's simulator.
   * @return The updated behavior of this actor after processing the {@code NextTime} message.
   */
  Behavior<DevsMessage> onNextInternalTimeReport(NextInternalTimeReport<T> nextInternalTimeReport) {
    nextTimeMap.put(nextInternalTimeReport.getSenderId(), nextInternalTimeReport.getNextInternalTime());

    // If we have all next time messages from models, the next time if the min
    if (nextTimeMap.size() == modelSimulators.size()) {
      timeNext = getNextTime();
      // System.out.println("In onNextTimeMessage, Next time for " + modelIdentifier + " is " +
      // timeNext);
      parent.tell(NextInternalTimeReport.<T>builder()
          .eventTime(nextInternalTimeReport.getEventTime())
          .simulationId(simulationId)
          .messageId(generateMessageId("NextTime"))
          .senderId(modelIdentifier)
          .receiverId(parentId)
          .nextInternalTime(timeNext)
          .build());
    }
    return this;
  }

  /**
   * Handles the `SendOutput` message for managing the output generation process within the
   * PDevsCoordinator. This involves verifying synchronization, building imminent models, and
   * notifying the simulators of the imminent models to generate outputs.
   *
   * @param requestOutput The `SendOutput` message containing the current simulation time and a signal
   *                   to trigger output generation for imminent models.
   * @return The updated behavior of this actor after processing the `SendOutput` message.
   * @throws RuntimeException if the simulation time in the `SendOutput` message does not match the
   *                          expected next simulation time (`timeNext`).
   */
  Behavior<DevsMessage> onRequestOutput(RequestOutput<T> requestOutput) {
    if (requestOutput.getEventTime().compareTo(timeNext) != 0) {
      try {
        String sendOutputMessage = DevsObjectMapper.buildObjectMapper().writeValueAsString(requestOutput);
        throw new RuntimeException("Bad synchronization.  Received SendOutputMessage "
            + sendOutputMessage + "where time "
            + requestOutput.getEventTime() + " did not equal " + timeNext);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Bad synchronization.  Received SendOutputMessage where time "
            + requestOutput.getEventTime() + " did not equal " + timeNext);
      }
    }
    generatingOutput = true;
    buildImminentModels();
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG, "Immenent models are " + Arrays.toString(imminentModels.toArray()));
    }
    outputMap = new HashMap<>();
    imminentModels.forEach(m -> {
      modelSimulators.get(m).tell(RequestOutput.<T>builder()
          .eventTime(requestOutput.getEventTime())
          .simulationId(simulationId)
          .messageId(generateMessageId("SendOutput"))
          .senderId(modelIdentifier)
          .receiverId(m)
          .build()

          );
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
   * such as forwarding messages to child models, sending outputReport to parent models,
   * and executing transitions. This method ensures that all outputReport are received
   * and processed appropriately before proceeding.
   *
   * @param outputReport the {@link OutputReport} containing the outputReport from a model,
   *                including the sender, the next simulation time, and the associated
   *                port-value list.
   * @return the current behavior of the actor, allowing for further processing.
   */
  Behavior<DevsMessage> onOutputReport(OutputReport<T> outputReport) {
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG,
          "Got model outputReport at " + timeNext + " from " + outputReport.getSenderId() + ": "
              + Arrays.toString(outputReport.getPayload().getOutputs().stream()
              .map(pv -> pv.getPortName()).toArray()));
    }
    // outputReport.getModelOutput().getPortValueList().forEach(portValue -> getContext().getLog().debug(
    // " " + portValue.getPortIdentifier() + ": " + portValue.getValue()));
    outputMap.put(outputReport.getSenderId(), Optional.of(outputReport.getPayload().getOutputs()));
    if (getContext().getLog().isDebugEnabled()) {
      log(Level.DEBUG, "Have outputReport from " + Arrays.toString(outputMap.keySet().toArray()));
    }

    if (haveAllOutputs()) {
      // Send outputReport to parent based on mappings and translations
      // Send inputs to children based on mappings and translations
      log(Level.DEBUG, "We have all outputReport.");
      awaitingTransition = new ArrayList<>();
      OutputCouplingMessages outputCouplingMessages = couplings.handleOutputBag(outputMap);
      modelOutput = outputCouplingMessages.getOutputMessages();
      receivers = outputCouplingMessages.getInternalMessages();

      receivers.forEach((key, value) -> {
        awaitingTransition.add(key);
        modelSimulators.get(key).tell(ExecuteTransition.<T>builder()
            .eventTime(timeNext)
            .payload(ExecuteTransitionPayload.builder().addAllInputs(value).build())
            .simulationId(simulationId)
            .messageId(generateMessageId("ExecuteTransition"))
            .senderId(modelIdentifier)
            .receiverId(key)
            .build());
        if (getContext().getLog().isDebugEnabled()) {
          log(Level.DEBUG, "Sending input to " + key + ": " + Arrays.asList(value
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
        modelSimulators.get(modelId).tell(ExecuteTransition.<T>builder()
            .eventTime(timeNext)
            .payload(ExecuteTransitionPayload.builder().build())
            .simulationId(simulationId)
            .messageId(generateMessageId("ExecuteTransition"))
            .senderId(modelIdentifier)
            .receiverId(modelId)
            .build());
      });
      // If the model outputReport have not generated any transitions, output is done. Send output
      // message.
      if (awaitingTransition.isEmpty()) {
        sendOutputs(outputReport.getEventTime());
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
  void sendOutputs(T eventTime) {
    parent.tell(OutputReport.<T>builder()
        .eventTime(eventTime)
        .payload(OutputReportPayload.builder().addAllOutputs(modelOutput).build())
        .simulationId(simulationId)
        .messageId(generateMessageId("OutputReport"))
        .senderId(modelIdentifier)
        .receiverId(parentId)
        .nextInternalTime(timeNext)
        .build());
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
  Behavior<DevsMessage> onExecuteTransition(ExecuteTransition<T> executeTransition) {
    if (executeTransition.getEventTime().compareTo(timeLast) < 0
        || executeTransition.getEventTime().compareTo(timeNext) > 0) {
      throw new RuntimeException("Bad synchronization.  " + modelIdentifier
          + " received ExecuteTransitionMessage where time " + executeTransition.getEventTime()
          + " is not between " + timeLast + " and " + timeNext + "inclusive");
    }
    if (executeTransition.getPayload().getInputs().isEmpty()) {
      // Inputs are empty, so nothing to do
      sendTransitionDone(executeTransition.getEventTime());
    } else {
      Map<String, List<PortValue<?>>> receivers =
          couplings.handleInputMessage(executeTransition.getPayload().getInputs());
      awaitingTransition = new ArrayList<>();
      // Send execute transition messages to those models with input generated from the coordinator
      // input
      receivers.forEach((key, value) -> {
        awaitingTransition.add(key);
        modelSimulators.get(key).tell(ExecuteTransition.<T>builder()
            .eventTime(executeTransition.getEventTime())
            .payload(ExecuteTransitionPayload.builder().addAllInputs(value).build())
            .simulationId(simulationId)
            .messageId(generateMessageId("ExecuteTransition"))
            .senderId(modelIdentifier)
            .receiverId(key)
            .build());
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
        TransitionComplete.<T>builder()
          .eventTime(time)
          .simulationId(simulationId)
          .messageId(generateMessageId("TransitionComplete"))
          .senderId(modelIdentifier)
          .receiverId(parentId)
          .nextInternalTime(timeNext)
          .build());
  }

  /**
   * Handles the receipt of a {@code TransitionDone} message from a model or simulator, processes it
   * by updating internal state, and determines subsequent simulation steps. If all awaiting
   * transitions have completed, it advances the simulation's state and triggers the next actions,
   * such as sending outputs or notifying the parent of transition completion.
   *
   * @param transitionComplete the {@code TransitionDone} message containing the identifier of the
   *                       sender, its next simulation time, and the time of completion for the
   *                       transition.
   * @return the updated behavior of the coordinator after processing the {@code TransitionDone}
   * message.
   */
  Behavior<DevsMessage> onTransitionComplete(TransitionComplete<T> transitionComplete) {
    log(Level.DEBUG, transitionComplete.getSenderId() + " sent TransitionDone with next time of "
        + transitionComplete.getNextInternalTime());
    nextTimeMap.put(transitionComplete.getSenderId(), transitionComplete.getNextInternalTime());
    awaitingTransition.remove(transitionComplete.getSenderId());
    if (awaitingTransition.isEmpty()) {
      timeLast = transitionComplete.getEventTime();
      // System.out.println("Last time for " + modelIdentifier + " is " + timeLast);
      timeNext = getNextTime();
      // System.out.println("In onTransitionDone, Next time for " + modelIdentifier + " is " +
      // timeNext);
      if (!generatingOutput) {
        log(Level.DEBUG, "Sending transiton done.");
        sendTransitionDone(transitionComplete.getEventTime());
      } else {
        log(Level.DEBUG, "Sending outputs.");
        generatingOutput = false;
        sendOutputs(transitionComplete.getEventTime());
      }
    }
    return this;
  }

  /**
   * Handles the completion of the simulation by propagating the {@code SimulationDone} message to
   * all registered model simulators. This method updates the internal state to prepare for the next
   * stage of simulation or finalization.
   *
   * @param simulationTerminate The {@code SimulationDone} message indicating that the simulation process
   *                       has concluded.
   * @return The updated behavior of the coordinator, maintaining its current state.
   */
  Behavior<DevsMessage> onSimulationTerminate(SimulationTerminate<T> simulationTerminate) {
    awaitingTransition = new ArrayList<>();
    for (String key : modelSimulators.keySet()) {
      awaitingTransition.add(key);
      modelSimulators.get(key).tell(SimulationTerminate.<T>builder()
          .eventTime(simulationTerminate.getEventTime())
          .simulationId(simulationId)
          .messageId(generateMessageId("SimulationTerminate"))
          .senderId(modelIdentifier)
          .receiverId(key)
          .payload(simulationTerminate.getPayload())
          .build());
    }
    return this;
  }

  /**
   * Handles the completion of a model's operation, represented by the `ModelDone` message. This
   * method updates the internal state by removing the sender from the set of awaiting models. If no
   * models are awaiting transitions, it notifies the parent actor of the completion and stops the
   * actor's behavior. Otherwise, it maintains the current behavior.
   *
   * @param modelTerminated The `ModelDone` message containing the sender's identifier and completion
   *                  information, including the simulation time.
   * @return The updated behavior of the actor after processing the `ModelDone` message.
   */
  Behavior<DevsMessage> onModelTerminated(ModelTerminated<T> modelTerminated) {
    awaitingTransition.remove(modelTerminated.getSenderId());
    if (awaitingTransition.isEmpty()) {
      parent.tell(ModelTerminated.<T>builder()
          .simulationId(simulationId)
          .messageId(generateMessageId("ModelTerminated"))
          .senderId(modelIdentifier)
          .receiverId(parentId)
          .build());
      return Behaviors.stopped();
    }
    return this;
  }

  Behavior<DevsMessage> onTerminated(Terminated terminated) {
    getContext().getLog().warn("{} Received terminated: {}", modelIdentifier, terminated);
    return Behaviors.same();
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
