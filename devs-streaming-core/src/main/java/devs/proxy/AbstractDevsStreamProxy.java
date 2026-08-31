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
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.DevsMessage;
import devs.iso.ModelTerminated;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.time.SimTime;
import devs.utils.DevsObjectMapper;
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
  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  /**
   * Constructs the outbound proxy base.
   *
   * @param context       the Pekko actor context
   * @param componentName the name of the local DEVS component (used for logging)
   * @param publisher     the transport publisher to use for outbound messages
   */
  protected AbstractDevsStreamProxy(ActorContext<DevsMessage> context, String componentName,
      MessagePublisher publisher) {
    super(context);
    this.componentName = componentName;
    this.publisher = publisher;
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
    String json;
    try {
      if (devsMessage instanceof SimulationInitMessage<?> initSimMessage) {
        json = objectMapper.writeValueAsString(initSimMessage.getSimulationInit());
      } else {
        json = objectMapper.writeValueAsString(devsMessage);
      }
    } catch (JsonProcessingException e) {
      getContext().getLog().error("Could not serialize message to JSON: {}", devsMessage, e);
      throw new RuntimeException(e);
    }
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
