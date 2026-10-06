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

package devs.proxy;

import devs.iso.DevsMessage;
import devs.iso.DevsSimMessage;
import devs.iso.ModelTerminated;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import java.io.IOException;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;

/**
 * Abstract base actor that implements the fixed-target inbound DEVS stream receiver behavior.
 *
 * <p>Subclasses provide a transport-specific {@link MessageReceiver}; this class owns all
 * DEVS-facing deserialization, {@code receiverId} filtering, {@link SimulationInit} wrapping, and
 * termination logic.
 *
 * <p>On each inbound payload delivered by the transport:
 * <ol>
 *   <li>Deserializes the JSON string to a {@link DevsSimMessage}.</li>
 *   <li>Drops the message if its {@code receiverId} does not match the configured
 *       {@code receiverId}.</li>
 *   <li>If the message is a {@link SimulationInit}, wraps it in a
 *       {@link SimulationInitMessage}(simulationInit, sender) so the target learns its return
 *       path.</li>
 *   <li>Forwards the (possibly wrapped) message to the fixed {@code devsComponent}.</li>
 *   <li>On {@link SimulationTerminate} or {@link ModelTerminated}, shuts down the receiver
 *       stream and stops the actor.</li>
 * </ol>
 *
 * <p>The actor also handles any {@link DevsMessage} forwarded to it via
 * {@link #onDevsMessage(DevsMessage)} — inbound payloads are dispatched from the transport
 * callback by telling this actor's self reference, so they arrive as normal actor messages.
 */
public abstract class AbstractDevsStreamReceiver extends AbstractBehavior<DevsMessage> {

  private final MessageReceiver receiver;
  private final ActorRef<DevsMessage> devsComponent;
  private final ActorRef<DevsMessage> sender;
  private final String receiverId;
  private final DevsJsonBinding binding;

  /**
   * Constructs the inbound receiver base and starts the transport subscription immediately.
   *
   * @param context       the Pekko actor context
   * @param devsComponent the fixed DEVS component to which inbound messages are forwarded
   * @param sender        the actor reference used to wrap inbound {@link SimulationInit} messages
   * @param receiverId    only messages whose {@code receiverId} matches this value are forwarded
   * @param receiver      the transport receiver to subscribe to
   */
  protected AbstractDevsStreamReceiver(ActorContext<DevsMessage> context,
      ActorRef<DevsMessage> devsComponent, ActorRef<DevsMessage> sender, String receiverId,
      MessageReceiver receiver) {
    this(context, devsComponent, sender, receiverId, receiver, TimeEndpointPolicy.legacy());
  }

  /**
   * Constructs a receiver with an explicit time policy and immediately subscribes to transport.
   * Invalid payloads are rejected before dispatch to the actor mailbox.
   *
   * @param context the Pekko actor context
   * @param devsComponent the fixed target for accepted inbound messages
   * @param sender the return-path actor reference supplied in initialization wrappers
   * @param receiverId the receiver identifier used to filter messages
   * @param receiver the inbound transport subscription
   * @param policy the required wire profile and accepted timestamp types
   */
  protected AbstractDevsStreamReceiver(ActorContext<DevsMessage> context,
      ActorRef<DevsMessage> devsComponent, ActorRef<DevsMessage> sender, String receiverId,
      MessageReceiver receiver, TimeEndpointPolicy policy) {
    super(context);
    this.devsComponent = devsComponent;
    this.sender = sender;
    this.receiverId = receiverId;
    this.receiver = receiver;
    this.binding = new DevsJsonBinding(policy);
    var log = context.getLog();
    ActorRef<DevsMessage> self = context.getSelf();
    // Subscribe at construction time to preserve the same ordering guarantees as before.
    this.receiver.subscribe(payload -> {
      DevsMessage devsMessage;
      try {
        devsMessage = binding.decode(payload);
      } catch (IOException | IllegalArgumentException e) {
        log.error("Rejected inbound DEVS record: {}", DevsJsonBinding.diagnostic(e));
        return;
      }
      self.tell(devsMessage);
    });
  }

  /**
   * Creates the receive handler, routing all {@link DevsMessage} instances to
   * {@link #onDevsMessage(DevsMessage)}.
   */
  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsMessage.class, this::onDevsMessage);
    return builder.build();
  }

  /**
   * Processes a DEVS message (deserialized from an inbound transport payload): filters on
   * {@code receiverId}, wraps {@link SimulationInit} in a {@link SimulationInitMessage}, forwards
   * to the fixed target component, and shuts down on terminate.
   *
   * @param devsMessage the incoming message
   * @return the next actor behavior
   */
  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    DevsSimMessage devsSimMessage;
    try {
      devsSimMessage = binding.validate(devsMessage);
    } catch (IllegalArgumentException e) {
      getContext().getLog().error("Rejected inbound DEVS message: {}", DevsJsonBinding.diagnostic(e));
      return Behaviors.same();
    }
    String messageReceiverId = devsSimMessage.getReceiverId();
    if (!messageReceiverId.equals(receiverId)) {
      getContext().getLog().debug("Dropping DEVS message for another component");
      return Behaviors.same();
    }
    DevsMessage toForward = devsSimMessage;
    if (devsSimMessage instanceof SimulationInit<?> simulationInit) {
      toForward = new SimulationInitMessage<>(simulationInit, sender);
    }
    devsComponent.tell(toForward);
    if (devsSimMessage instanceof SimulationTerminate<?> || devsSimMessage instanceof ModelTerminated<?>) {
      receiver.shutdown();
      return Behaviors.stopped();
    }
    return Behaviors.same();
  }

  /**
   * Returns the receiverId this actor filters inbound messages on.
   */
  public String getReceiverId() {
    return receiverId;
  }
}
