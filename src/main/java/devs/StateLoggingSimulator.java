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

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.Bag;
import devs.msg.DevsMessage;
import devs.msg.ExecuteTransition;
import devs.msg.InitSimMessage;
import devs.msg.ModelOutputMessage;
import devs.msg.SendOutput;
import devs.msg.SimulationDone;
import devs.msg.log.DevsLogMessage;
import devs.msg.log.DevsModelLogMessage;
import devs.msg.log.PekkoReceptionistListingResponse;
import devs.msg.log.StateMessage;
import devs.msg.time.SimTime;
import devs.utils.DevsObjectMapper;
import devs.utils.ModelUtils;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;

/**
 * StateLoggingSimulator is a specialized PDEVS simulator that incorporates logging functionality to
 * log state changes, outputs, and other important events during simulation execution. It ensures
 * that simulations are traceable and provides serialized logging information for better analysis
 * and debugging.
 *
 * @param <T> the type of the simulation time
 * @param <S> the state type of the PDEVS model
 * @param <M> the PDEVS model type
 */
public class StateLoggingSimulator<T extends SimTime, S, M extends PDEVSModel<T, S>>
    extends PDevsSimulator<T, S, M> {
      
  public static final ServiceKey<DevsLogMessage> stateLoggerKey =
      ServiceKey.create(DevsLogMessage.class, "devsLoggingService");

  ActorRef<DevsLogMessage> loggingActor;
  private final ActorRef<Receptionist.Listing> listingResponseAdapter;
  final Serialization serialization;
  final ObjectMapper objectMapper;

  /**
   * Creates an Pekko Behavior for a state-logging DEVS simulator.
   *
   * @param <T1>         the type extending {@code SimTime} that represents the time system used in
   *                     the simulation
   * @param <S1>         the state type used within the model
   * @param <M1>         the type of the DEVS model that extends {@code PDEVSModel<T1, S1>}
   * @param devsModel    the Parallel DEVS (PDEVS) model to be simulated
   * @param initialTime  the initial simulation time
   * @return the behavior representing the state-logging simulator associated with the provided
   * model
   */
  public static final <T1 extends SimTime, S1, M1 extends PDEVSModel<T1, S1>> Behavior<DevsMessage>
      createStateLoggingSimulator(M1 devsModel, T1 initialTime) {
    return Behaviors.setup(
        context -> new StateLoggingSimulator<>(devsModel, initialTime, context));
  }

  /**
   * Constructs a StateLoggingSimulator instance for simulating a PDEVS model with state logging
   * functionality.
   *
   * @param devsModel    the DEVS model to be simulated, an instance of {@code M} extending
   *                     {@link PDEVSModel}
   * @param initialTime  the initial simulation time, of type {@code T} extending {@link SimTime}
   * @param context      the actor context for the simulator, of type {@link ActorContext}
   *                     interacting with {@link DevsMessage}
   */
  public StateLoggingSimulator(M devsModel, T initialTime, ActorContext<DevsMessage> context) {
    super(devsModel, initialTime, context);
    this.serialization = SerializationExtension.get(context.getSystem());
    this.objectMapper = DevsObjectMapper.buildObjectMapper();
    this.listingResponseAdapter = context.messageAdapter(Receptionist.Listing.class, PekkoReceptionistListingResponse::new);

    context.getSystem().receptionist().tell(Receptionist.subscribe(stateLoggerKey, listingResponseAdapter));
  }

  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = super.createReceiveBuilder();
    builder.onMessage(PekkoReceptionistListingResponse.class, this::onPekkoReceptionistListingResponse);
    return builder.build();
  }

  /**
   * Serializes a given {@link DevsMessage} instance to its UTF-8 string representation.
   *
   * @param devsMessage the {@link DevsMessage} instance to be serialized
   * @return the serialized string representation of the provided {@link DevsMessage} in UTF-8
   * encoding
   */
  String serialize(DevsMessage devsMessage) {
    return new String(serialization.serialize(devsMessage).get(), StandardCharsets.UTF_8);
  }

  /**
   * Logs the current state of the DEVS model along with the simulation time to the designated
   * logging actor.
   *
   * @param simTime the current simulation time, of type {@code T} representing the time system used
   *                in the simulation
   */
  protected void logState(T simTime) {
    StateMessage<?, ?> stateMessage = StateMessage.builder().modelId(devsModel.getModelIdentifier())
        .modelState(devsModel.getModelState()).time(simTime).build();
    sendLogMessage(stateMessage);
  }

  protected void sendLogMessage(DevsLogMessage devsLogMessage) {
    if (loggingActor != null) {
      loggingActor.tell(devsLogMessage);
    } else {
      getContext().getLog().warn("Cannot send log message because loggingActor Pekko Receptionist has not sent listing");
    }
  }

  protected Behavior<DevsMessage> onPekkoReceptionistListingResponse(PekkoReceptionistListingResponse listingResponse) {
    Set<ActorRef<DevsLogMessage>> loggingActors = listingResponse.getListing().getAllServiceInstances(stateLoggerKey);
    if (!loggingActors.isEmpty()) {
      loggingActors.forEach(devsLoggingActor -> this.loggingActor = devsLoggingActor);
    } else {
      getContext().getLog().warn("Received emply PekkoReceptionistListingResponse for logging actors");
    }
    
    return Behaviors.same();
  }

  @Override
  protected Behavior<DevsMessage> onExecuteTransitionMessage(
      ExecuteTransition<T> executeTransition) {
    Behavior<DevsMessage> behavior = super.onExecuteTransitionMessage(executeTransition);
    DevsModelLogMessage<?> devsModelLogMessage =
        DevsModelLogMessage.builder().time(executeTransition.getTime())
            .modelId(devsModel.getModelIdentifier()).devsMessage(executeTransition).build();
    sendLogMessage(devsModelLogMessage);
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
    ModelOutputMessage<?> modelOutputMessage =
        ModelOutputMessage.builder().modelOutput(modelOutput).nextTime(timeNext)
            .sender(devsModel.getModelIdentifier()).build();
    parent.tell(modelOutputMessage);
    DevsModelLogMessage<?> devsModelLogMessage =
        DevsModelLogMessage.builder().time(sendOutput.getTime())
            .modelId(devsModel.getModelIdentifier()).devsMessage(modelOutputMessage).build();
    sendLogMessage(devsModelLogMessage);
    return this;
  }


}
