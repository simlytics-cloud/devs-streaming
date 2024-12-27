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
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;

/**
 * {@code PDevsSimulator} is a generic simulation actor that implements the Parallel Discrete Event
 * System (PDEVS) formalism. It interacts with a {@link PDEVSModel} to handle simulation messages
 * and execute the model's state transitions based on incoming messages and time advancement rules.
 *
 * @param <T> The type of simulation time used for the simulation, which must extend the
 *            {@link SimTime} abstract class.
 * @param <S> The state representation type used by the PDEVS model.
 * @param <M> The type of the parallel DEVS model represented by {@code PDEVSModel}.
 */
public class PDevsSimulator<T extends SimTime, S,
    M extends PDEVSModel<T, S>> extends AbstractBehavior<DevsMessage> {

  protected T timeLast;
  protected T timeNext;
  protected ActorRef<DevsMessage> parent;

  protected final M devsModel;


  /**
   * Creates a new Behavior instance for simulating a PDEVS model. This method initializes a
   * PDevsSimulator with the provided DEVS model and starting time.
   *
   * @param <TT>        the type of simulation time, extending {@link SimTime}
   * @param devsModel   the DEVS model to be simulated, an instance of {@link PDEVSModel}
   * @param initialTime the initial simulation time for the model, of type {@link SimTime}
   * @return a new Behavior instance configured for the given DEVS model and initial time
   */
  public static <TT extends SimTime> Behavior<DevsMessage> create(PDEVSModel<TT, ?> devsModel,
                                                                  TT initialTime) {
    return Behaviors.setup(context -> new PDevsSimulator<>(devsModel, initialTime, context));
  }

  /**
   * Constructs a PDevsSimulator instance to simulate a PDEVS model.
   *
   * @param devsModel   the DEVS model to be simulated, an instance of {@link PDEVSModel}
   * @param initialTime the initial simulation time, of type {@link SimTime}
   * @param context     the actor context for the simulator, of type {@link ActorContext} that
   *                    interacts with {@link DevsMessage}
   */
  public PDevsSimulator(M devsModel, T initialTime, ActorContext<DevsMessage> context) {
    super(context);
    this.devsModel = devsModel;
    this.timeLast = initialTime;
    this.timeNext = initialTime;
    devsModel.setSimulator(this);
  }

  /**
   * Creates a receive handler for processing various types of DevsMessage. This method defines the
   * behavior of the simulator by specifying how it handles different message types, including
   * initialization, sending outputs, executing transitions, and indicating simulation completion.
   *
   * @return a Receive instance configured to handle DevsMessage types such as InitSimMessage,
   * SendOutput, ExecuteTransition, and SimulationDone.
   */
  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();

    builder.onMessage(InitSimMessage.class, this::onInitSimMessage);
    builder.onMessage(SendOutput.class, this::onSendOutputMessage);
    builder.onMessage(ExecuteTransition.class, this::onExecuteTransitionMessage);
    builder.onMessage(SimulationDone.class, this::onSimulationDone);

    return builder.build();
  }

  /**
   * Calculates the next internal transition time for the PDEVS model by invoking the model's time
   * advance function. Ensures that the calculated time is not earlier than the current simulation
   * time. Throws a runtime exception if a negative time advance is detected.
   *
   * @param currentTime the current simulation time
   * @return the time of the next internal state transition as determined by the model's time
   * advance function
   * @throws RuntimeException if the calculated time advance is less than the current simulation
   *                          time
   */
  protected T timeAdvance(T currentTime) {
    T time = devsModel.timeAdvanceFunction(currentTime);
    if (time.compareTo(currentTime) < 0) {
      throw new RuntimeException(devsModel.modelIdentifier + " generated a negative time advance.");
    }
    return time;
  }

  /**
   * Handles an InitSimMessage to initialize the simulation. This method sets the parent actor,
   * calculates the time for the next internal state transition using the model's time advance
   * function, and notifies the parent actor of the next scheduled transition time.
   *
   * @param initSimMessage the initialization message containing the parent actor reference and
   *                       initial simulation settings
   * @return the updated behavior of the simulator to handle subsequent messages
   */
  protected Behavior<DevsMessage> onInitSimMessage(InitSimMessage<T> initSimMessage) {
    this.parent = initSimMessage.getParent();
    timeNext = timeAdvance(initSimMessage.getInitSim().getTime());
    parent.tell(NextTime.builder().time(timeNext).sender(devsModel.getModelIdentifier()).build());
    return this;
  }

  /**
   * Handles the sending of an output message in the simulation. This method validates the
   * synchronization of the simulation time and triggers the model's output function to generate
   * output. The generated output and related information are sent to the parent actor.
   *
   * @param sendOutput the message containing information about the output scheduling in the
   *                   simulation, including the intended simulation time
   * @return the updated Behavior of the simulator to continue processing DevsMessages
   * @throws RuntimeException if the simulation time in the SendOutput message does not match the
   *                          expected next simulation time
   */
  protected Behavior<DevsMessage> onSendOutputMessage(SendOutput<T> sendOutput) {
    if (sendOutput.getTime().compareTo(timeNext) != 0) {
      throw new RuntimeException("Bad synchronization.  Received SendOutputMessage where time "
          + sendOutput.getTime() + " did not equal " + timeNext);
    }
    Bag modelOutput = devsModel.outputFunction();
    parent.tell(ModelOutputMessage.builder().modelOutput(modelOutput).nextTime(timeNext)
        .time(sendOutput.getTime()).sender(devsModel.getModelIdentifier()).build());
    return this;
  }

  /**
   * Handles the execution of a transition message in the PDEVS simulation. This method determines
   * the type of transition to execute (internal, external, or confluent) based on the received
   * message and the simulation state. It validates synchronization of the transition time, ensuring
   * that it lies within the expected time interval.
   *
   * @param executeTransition the {@link ExecuteTransition} message containing information about the
   *                          transition time and optional model inputs
   * @return a {@link Behavior} of {@link DevsMessage} that reflects the updated state of the
   * simulator after processing the transition
   * @throws RuntimeException         if the transition time is not within the valid range defined
   *                                  by the last and next simulation times
   * @throws IllegalArgumentException if an external transition for the DEVS model is empty but
   *                                  model inputs are required
   */
  protected Behavior<DevsMessage> onExecuteTransitionMessage(
      ExecuteTransition<T> executeTransition) {
    if (executeTransition.getTime().compareTo(timeLast) < 0
        || executeTransition.getTime().compareTo(timeNext) > 0) {
      throw new RuntimeException("Bad synchronization.  " + devsModel.modelIdentifier
          + " received ExecuteTransitionMessage where time " + executeTransition.getTime()
          + " is not between " + timeLast + " and " + timeNext + "inclusive");
    }
    if (executeTransition.getTime().compareTo(timeNext) == 0) {
      if (executeTransition.getModelInputsOption().isEmpty()) {
        return internalStateTransition(executeTransition.getTime());
      } else {
        return confluentStateTransition(executeTransition.getTime(),
            executeTransition.getModelInputsOption().get());
      }
    } else {
      if (executeTransition.getModelInputsOption().isEmpty()) {
        throw new IllegalArgumentException("External transition for model "
            + devsModel.getModelIdentifier() + " is empty.  Transition time is "
            + executeTransition.getTime() + ".  Next time is " + timeNext);
      } else {
        return externalStateTransition(executeTransition.getTime(),
            executeTransition.getModelInputsOption().get());
      }
    }
  }

  /**
   * Executes the dome transition for the PDEVS simulation. Updates the internal simulation time
   * variables and notifies the parent actor of the next scheduled transition time.
   *
   * @param time the current simulation time at which the transition occurs, of type T
   */
  protected void transitionDome(T time) {
    timeLast = time;
    timeNext = timeAdvance(time);
    parent.tell(TransitionDone.builder().nextTime(timeNext).time(time)
        .sender(devsModel.getModelIdentifier()).build());
  }

  /**
   * Executes the internal state transition for the PDEVS simulation. This method triggers the DEVS
   * model's internal state transition function and performs additional internal updates through the
   * transition dome logic.
   *
   * @param time the current simulation time at which the internal state transition occurs, an
   *             instance of T
   * @return the updated {@link Behavior} of {@link DevsMessage} representing the simulator's new
   * state
   */
  protected Behavior<DevsMessage> internalStateTransition(T time) {
    devsModel.internalStateTransitionFunction(time);
    transitionDome(time);
    return this;
  }

  /**
   * Executes the confluent state transition for the PDEVS simulation. This method combines the
   * internal and external transitions when both occur simultaneously at the current simulation
   * time. It invokes the DEVS model's confluent state transition function and performs additional
   * internal updates through the transition dome logic.
   *
   * @param time  the current simulation time at which the confluent transition occurs, an instance
   *              of T
   * @param input the input message/events received for the external transition, an instance of Bag
   * @return the updated {@link Behavior} of {@link DevsMessage} representing the simulator's new
   * state
   */
  protected Behavior<DevsMessage> confluentStateTransition(T time, Bag input) {
    devsModel.confluentStateTransitionFunction(time, input);
    transitionDome(time);
    return this;
  }

  /**
   * Executes the external state transition for the PDEVS simulation. This method invokes the DEVS
   * model's external state transition function and performs additional updates through the
   * transition dome logic.
   *
   * @param time  the current simulation time at which the external state transition occurs, an
   *              instance of T
   * @param input the input message/events received for the external transition, an instance of Bag
   * @return the updated {@link Behavior} of {@link DevsMessage} representing the simulator's new
   * state
   */
  protected Behavior<DevsMessage> externalStateTransition(T time, Bag input) {
    devsModel.externalStateTransitionFunction(time, input);
    transitionDome(time);
    return this;
  }

  /**
   * Handles the SimulationDone message to finalize the simulation process. This method notifies the
   * parent actor of the simulation's completion and the time at which it concluded by sending a
   * ModelDone message. The behavior transitions to a stopped state after processing the message.
   *
   * @param simulationDone the message indicating that the simulation has been completed, containing
   *                       the simulation end time
   * @return the updated {@link Behavior} of {@link DevsMessage}, which is set to a stopped state
   */
  protected Behavior<DevsMessage> onSimulationDone(SimulationDone<T> simulationDone) {
    parent.tell(ModelDone.builder().time(simulationDone.getTime())
        .sender(devsModel.getModelIdentifier()).build());
    return Behaviors.stopped();
  }


}
