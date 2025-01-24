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


import devs.msg.DevsMessage;
import devs.msg.InitSim;
import devs.msg.InitSimMessage;
import devs.msg.ModelDone;
import devs.msg.ModelOutputMessage;
import devs.msg.NextTime;
import devs.msg.SendOutput;
import devs.msg.SimulationDone;
import devs.msg.time.SimTime;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ChildFailed;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;

/**
 * RootCoordinator is a behavior for simulating a root coordinator in a hierarchical DEVS (Discrete
 * Event System Specification) model. It manages simulation time progression, communication with
 * child actors, and handles simulation lifecycle events.
 *
 * @param <T> A type extending SimTime that represents the simulation's time base.
 */
public class RootCoordinator<T extends SimTime> extends AbstractBehavior<DevsMessage> {

  private T time;
  private final T endTime;
  private final ActorRef<DevsMessage> child;

  /**
   * Creates a behavior for the root coordinator in a hierarchical DEVS model. The root coordinator
   * manages simulation lifecycle events, simulation time progression, and communication with a
   * child actor in the system.
   *
   * @param <TT>    A type extending {@link SimTime} that represents the simulation's time base.
   * @param endTime the simulation's termination time
   * @param child   the reference to the child actor participating in the simulation
   * @return the root coordinator's behavior
   */
  public static <TT extends SimTime> Behavior<DevsMessage> create(TT endTime,
                                                                  ActorRef<DevsMessage> child) {
    return Behaviors.setup(context -> new RootCoordinator<>(context, endTime, child));
  }

  /**
   * Constructs a RootCoordinator, which manages the simulation lifecycle, time progression, and
   * interactions with a child actor in a hierarchical DEVS simulation model.
   *
   * @param context the actor context for this RootCoordinator
   * @param endTime the simulation's termination time
   * @param child   the reference to the child actor participating in the simulation
   */
  public RootCoordinator(ActorContext<DevsMessage> context, T endTime,
                         ActorRef<DevsMessage> child) {
    super(context);
    this.endTime = endTime;
    this.child = child;
  }

  /**
   * Creates a receive behavior for handling various messages and signals in the hierarchical DEVS
   * simulation model. This method defines specific handlers for simulation lifecycle events and
   * failure scenarios.
   *
   * @return the receive behavior configured to process messages and signals relevant to the root
   * coordinator's role in the simulation.
   */
  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(InitSim.class, this::onInitSim);
    builder.onMessage(NextTime.class, this::onNextTime);
    builder.onMessage(ModelOutputMessage.class, this::onModelOutputMessage);
    builder.onMessage(ModelDone.class, this::onModelDone);
    builder.onSignal(ChildFailed.class, this::onChildFailed);
    builder.onSignal(Terminated.class, this::onTerminated);

    return builder.build();
  }

  /**
   * Handles the initialization of the simulation by processing the {@code InitSim} message. This
   * method sets the simulation start time and delegates the initialization message to the child
   * actor for further processing.
   *
   * @param initSim the initialization message containing the start time of the simulation and
   *                related data.
   * @return the updated behavior of the root coordinator after processing the initialization
   * message.
   */
  Behavior<DevsMessage> onInitSim(InitSim<T> initSim) {
    this.time = initSim.getTime();
    child.tell(new InitSimMessage(initSim, getContext().getSelf()));
    return this;
  }

  /**
   * Handles the NextTime message to update the simulation time and notify the child actor. This
   * method processes the next scheduled simulation time by storing the time value and directing the
   * child actor to send its output for the given time step.
   *
   * @param nextTime the message containing the next scheduled simulation time.
   * @return the updated behavior of the
   */
  Behavior<DevsMessage> onNextTime(NextTime<T> nextTime) {
    time = nextTime.getTime();
    child.tell(SendOutput.builder().time(time).build());
    return this;
  }

  /**
   * Handles the {@code ModelOutputMessage} in the simulation. This method processes the output
   * message from a DEVS model at the specified time step, evaluates whether the simulation time
   * exceeds the
   */
  Behavior<DevsMessage> onModelOutputMessage(ModelOutputMessage<T> modelOutputMessage) {
    if (modelOutputMessage.getNextTime().compareTo(endTime) <= 0) {
      time = modelOutputMessage.getNextTime();
      child.tell(SendOutput.builder().time(time).build());
    } else {
      child.tell(SimulationDone.builder().time(time).build());
    }
    return this;
  }

  /**
   * Handles the {@code ModelDone} message in the simulation. This method processes the completion
   * event of a DEVS model's execution, typically signaling that the model has finished its
   * computation or has reached its terminal state.
   *
   * @param modelDone the message indicating the completion of the model's execution, encapsulating
   *                  necessary details about the completed model.
   * @return the behavior indicating the termination of the root coordinator's actor after
   * processing the {@code ModelDone} message.
   */
  Behavior<DevsMessage> onModelDone(ModelDone<T> modelDone) {
    return Behaviors.stopped();
  }

  /**
   * Handles the failure of a child actor within the DEVS simulation system. This method logs the
   * failure message and retains the current behavior.
   *
   * @param childFailed the signal containing information about the failed child actor and the cause
   *                    of the failure.
   * @return the current behavior of the RootCoordinator after handling the failure.
   */
  Behavior<DevsMessage> onChildFailed(ChildFailed childFailed) {
    getContext().getLog()
        .error("Child actor failed with cause " + childFailed.cause().getMessage());
    return this;
  }

  Behavior<DevsMessage> onTerminated(Terminated terminated) {
    getContext().getLog().debug("Root coordinator received terminated {}", terminated);
    return Behaviors.same();
  }
}
