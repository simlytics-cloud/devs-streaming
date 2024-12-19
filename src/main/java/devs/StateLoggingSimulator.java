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

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.Bag;
import devs.msg.DevsMessage;
import devs.msg.ExecuteTransition;
import devs.msg.InitSimMessage;
import devs.msg.ModelOutputMessage;
import devs.msg.SendOutput;
import devs.msg.log.DevsLogMessage;
import devs.msg.log.DevsModelLogMessage;
import devs.msg.log.StateMessage;
import devs.msg.time.SimTime;
import devs.utils.DevsObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
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

  final ActorRef<DevsLogMessage> loggingActor;
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
   * @param loggingActor the ActorRef used to send logging messages
   * @return the behavior representing the state-logging simulator associated with the provided
   * model
   */
  public static <T1 extends SimTime, S1, M1 extends PDEVSModel<T1, S1>> Behavior<DevsMessage>
      create(M1 devsModel, T1 initialTime, ActorRef<DevsLogMessage> loggingActor) {
    return Behaviors.setup(
        context -> new StateLoggingSimulator<>(devsModel, initialTime, context, loggingActor));
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
   * @param loggingActor the actor to which state log messages, of type {@link DevsLogMessage}, will
   *                     be sent
   */
  public StateLoggingSimulator(M devsModel, T initialTime, ActorContext<DevsMessage> context,
      ActorRef<DevsLogMessage> loggingActor) {
    super(devsModel, initialTime, context);
    this.loggingActor = loggingActor;
    this.serialization = SerializationExtension.get(context.getSystem());
    this.objectMapper = DevsObjectMapper.buildObjectMapper();
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
    loggingActor.tell(stateMessage);
  }

  @Override
  protected Behavior<DevsMessage> onExecuteTransitionMessage(
      ExecuteTransition<T> executeTransition) {
    Behavior<DevsMessage> behavior = super.onExecuteTransitionMessage(executeTransition);
    DevsModelLogMessage<?> devsModelLogMessage =
        DevsModelLogMessage.builder().time(executeTransition.getTime())
            .modelId(devsModel.getModelIdentifier()).devsMessage(executeTransition).build();
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
    ModelOutputMessage<?> modelOutputMessage =
        ModelOutputMessage.builder().modelOutput(modelOutput).nextTime(timeNext)
            .time(sendOutput.getTime()).sender(devsModel.getModelIdentifier()).build();
    parent.tell(modelOutputMessage);
    DevsModelLogMessage<?> devsModelLogMessage =
        DevsModelLogMessage.builder().time(sendOutput.getTime())
            .modelId(devsModel.getModelIdentifier()).devsMessage(modelOutputMessage).build();
    loggingActor.tell(devsModelLogMessage);
    return this;
  }


}
