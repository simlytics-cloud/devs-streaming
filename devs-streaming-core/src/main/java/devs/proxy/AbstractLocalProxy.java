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
import devs.iso.DevsSimMessage;
import devs.iso.ModelTerminated;
import devs.iso.SimulationInitMessage;
import devs.iso.time.SimTime;
import devs.utils.DevsObjectMapper;
import java.util.Optional;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;

/**
 * Abstract base actor that implements the bidirectional DEVS local proxy behavior.
 *
 * <p>This actor is substitutable for a local {@code PDevsSimulator}/{@code PDevsCoordinator}:
 * a DEVS coordinator cannot tell it apart because it (a) accepts the same DEVS message protocol
 * on {@code ActorRef<DevsMessage>} and (b) is spawned via a {@link devs.SimulatorProvider}, just
 * like any local simulator.
 *
 * <p><strong>Outbound path</strong> (parent coordinator → transport):
 * Delegates to {@link AbstractDevsStreamProxy#onDevsMessage(DevsMessage)} after:
 * <ul>
 *   <li>Capturing {@code localParentCoordinator} from an outbound {@link SimulationInitMessage}'s
 *       parent reference.</li>
 *   <li>Shutting down the inbound receiver when a {@link ModelTerminated} is forwarded out.</li>
 * </ul>
 *
 * <p><strong>Inbound path</strong> (transport → local parent coordinator):
 * The {@link MessageReceiver} delivers each raw JSON payload to {@link #onInboundPayload(String)},
 * which:
 * <ul>
 *   <li>Deserializes to {@link DevsSimMessage}.</li>
 *   <li>Drops the message (with an error log) if {@code localParentCoordinator} is not yet
 *       known (replaces the original {@code System.exit(1)} — an intentional, safer change).</li>
 *   <li>Forwards the message to {@code localParentCoordinator} if the message's
 *       {@code receiverId} matches {@code localComponentName} (no {@link devs.iso.SimulationInit}
 *       wrapping — this path behaves differently from the standalone receiver).</li>
 * </ul>
 *
 * @param <T> the simulation time type
 */
public abstract class AbstractLocalProxy<T extends SimTime>
    extends AbstractDevsStreamProxy<T> {

  private final MessageReceiver receiver;
  private final String localComponentName;
  private Optional<ActorRef<DevsMessage>> localParentCoordinator;
  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  /**
   * Constructs the bidirectional local proxy base and starts the inbound transport subscription
   * immediately.
   *
   * @param context            the Pekko actor context
   * @param componentName      the remote component name (used for outbound publishing identity)
   * @param localComponentName the local component name used to filter inbound messages by
   *                           {@code receiverId}
   * @param publisher          the transport publisher for outbound messages
   * @param receiver           the transport receiver for inbound messages
   */
  protected AbstractLocalProxy(ActorContext<DevsMessage> context, String componentName,
      String localComponentName, MessagePublisher publisher, MessageReceiver receiver) {
    super(context, componentName, publisher);
    this.receiver = receiver;
    this.localComponentName = localComponentName;
    this.localParentCoordinator = Optional.empty();
    // Subscribe at construction time to preserve ordering guarantees.
    this.receiver.subscribe(this::onInboundPayload);
  }

  /**
   * Handles an outbound DEVS message from the local parent coordinator.
   *
   * <p>Captures the {@code localParentCoordinator} from an outbound {@link SimulationInitMessage},
   * shuts down the inbound receiver on {@link ModelTerminated}, then delegates to the outbound
   * base to serialize and publish the message.
   *
   * @param devsMessage the outbound DEVS message
   * @return the next actor behavior
   */
  @Override
  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    if (devsMessage instanceof SimulationInitMessage<?> initSimMessage) {
      this.localParentCoordinator = Optional.of(initSimMessage.getParent());
    }
    if (devsMessage instanceof ModelTerminated<?>) {
      this.receiver.shutdown();
    }
    return super.onDevsMessage(devsMessage);
  }

  /**
   * Handles a raw JSON payload delivered by the inbound transport subscription.
   *
   * <p>Deserializes, filters on {@code receiverId == localComponentName}, and forwards to the
   * dynamically-learned {@code localParentCoordinator}. No {@link devs.iso.SimulationInit}
   * wrapping is performed on this inbound path (unlike the standalone receiver).
   *
   * <p>If deserialization fails or the parent coordinator is not yet known, logs an error and
   * drops the message (replaces the original {@code System.exit(1)}).
   *
   * @param payload the raw ISO-21175 JSON string from the transport
   */
  private void onInboundPayload(String payload) {
    DevsSimMessage devsMessage;
    try {
      devsMessage = objectMapper.readValue(payload, DevsSimMessage.class);
    } catch (JsonProcessingException e) {
      getContext().getLog().error("Could not deserialize JSON record: {}", payload, e);
      return;
    }

    if (localParentCoordinator.isEmpty()) {
      getContext().getLog().error(
          "Message received before parent coordinator known; dropping: {}", payload);
      return;
    }

    if (devsMessage.getReceiverId().equals(localComponentName)) {
      localParentCoordinator.get().tell(devsMessage);
    } else {
      getContext().getLog().debug(
          "Received message for another component, ignoring: {}", devsMessage);
    }
  }
}
