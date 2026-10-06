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

import com.fasterxml.jackson.core.JsonProcessingException;
import devs.iso.DevsMessage;
import devs.iso.ModelTerminated;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.time.SimTime;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;

/**
 * Abstract base actor that implements the outbound DEVS stream proxy behavior.
 *
 * <p>Subclasses provide a transport-specific {@link MessagePublisher}; this class owns all
 * DEVS-facing serialization, sequence numbering, and termination logic.
 *
 * <p>On each outbound {@link DevsMessage}:
 * <ol>
 *   <li>If the message is a {@link SimulationInitMessage}, serializes the wrapped
 *       {@code SimulationInit} (not the wrapper itself) to ISO-21175 JSON.</li>
 *   <li>Otherwise serializes the message directly.</li>
 *   <li>Calls {@link MessagePublisher#publish(long, String)} with a monotonically increasing
 *       sequence number.</li>
 *   <li>On {@link SimulationTerminate} or {@link ModelTerminated}, calls
 *       {@link MessagePublisher#close()} and stops the actor.</li>
 * </ol>
 *
 * @param <T> the simulation time type
 */
public abstract class AbstractDevsStreamProxy<T extends SimTime>
    extends AbstractBehavior<DevsMessage> {

  private final MessagePublisher publisher;
  private final String componentName;
  private long index = 0;
  protected final DevsJsonBinding binding;

  /**
   * Constructs the outbound proxy base.
   *
   * @param context       the Pekko actor context
   * @param componentName the name of the local DEVS component (used for logging)
   * @param publisher     the transport publisher to use for outbound messages
   */
  protected AbstractDevsStreamProxy(ActorContext<DevsMessage> context, String componentName,
      MessagePublisher publisher) {
    this(context, componentName, publisher, TimeEndpointPolicy.legacy());
  }

  /**
   * Constructs an outbound proxy with explicit transport timestamp permissions.
   *
   * @param context the Pekko actor context
   * @param componentName the local component name used for logging
   * @param publisher the transport publisher for validated outbound messages
   * @param policy the wire profile and accepted timestamp types
   */
  protected AbstractDevsStreamProxy(ActorContext<DevsMessage> context, String componentName,
      MessagePublisher publisher, TimeEndpointPolicy policy) {
    super(context);
    this.componentName = componentName;
    this.publisher = publisher;
    this.binding = new DevsJsonBinding(policy);
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
   * Processes an outbound DEVS message: serializes it to ISO-21175 JSON, publishes it via the
   * transport, and stops the actor on termination messages.
   *
   * @param devsMessage the incoming DEVS message
   * @return the next actor behavior
   */
  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    return onEncodedMessage(devsMessage, encode(devsMessage));
  }

  /**
   * Validates and encodes before any publishing or lifecycle side effects occur.
   *
   * @param devsMessage the outbound message
   * @return the encoded transport envelope
   * @throws IllegalArgumentException if validation or serialization fails
   */
  protected final String encode(DevsMessage devsMessage) {
    try {
      return binding.encode(devsMessage);
    } catch (JsonProcessingException e) {
      String diagnostic = DevsJsonBinding.diagnostic(e);
      getContext().getLog().error("Could not serialize DEVS message: {}", diagnostic);
      throw new IllegalArgumentException(diagnostic);
    }
  }

  /**
   * Publishes an already validated envelope with the next sequence number and handles termination.
   *
   * @param devsMessage the original message used to determine whether to close the publisher
   * @param json the envelope produced by {@link #encode(DevsMessage)}
   * @return stopped behavior for termination messages, otherwise the current behavior
   */
  protected final Behavior<DevsMessage> onEncodedMessage(DevsMessage devsMessage, String json) {
    publisher.publish(index, json);
    index = index + 1;
    if (devsMessage instanceof SimulationTerminate<?> || devsMessage instanceof ModelTerminated<?>) {
      publisher.close();
      return Behaviors.stopped();
    }
    return Behaviors.same();
  }


  /**
   * Returns the component name this proxy represents.
   */
  protected String getComponentName() {
    return componentName;
  }
}
