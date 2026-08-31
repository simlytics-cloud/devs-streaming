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

import com.typesafe.config.Config;
import devs.iso.DevsMessage;
import devs.iso.time.SimTime;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;


/**
 * KafkaReceiver is a Pekko actor implementation that interacts with a Kafka topic to consume
 * messages using the Pekko Kafka library. It processes incoming messages, transforms them into DEVS
 * framework messages, and forwards them to a given DEVS simulation component.
 *
 * <p>This class is a thin Kafka wrapper over {@link AbstractDevsStreamReceiver}. All DEVS-specific
 * receiver logic (deserialization, {@code receiverId} filtering, {@link devs.iso.SimulationInit}
 * wrapping, forwarding, termination) lives in the base class; this class only constructs the
 * Kafka-specific {@link KafkaMessageReceiver}.
 */
public class KafkaReceiver extends AbstractDevsStreamReceiver {

  /**
   * Creates a new behavior instance of KafkaReceiver to handle Kafka message consumption and
   * process the data.
   *
   * @param <TT>                     A generic type that extends SimTime, representing the
   *                                 simulation time concept in DEVS framework.
   * @param devsComponent            The actor reference representing the DEVS component to interact
   *                                 with.
   * @param sender                   The actor reference responsible for sending messages to the
   *                                 KafkaReceiver.
   * @param recieverId               The receiver ID used to filter inbound messages.
   * @param runId                    Simulation run identifier; records with a different
   *                                 {@code X-Run-Id} header are dropped before deserialization.
   * @param pekkoKafkaConsumerConfig The configuration for the Pekko Kafka consumer.
   * @param consumerTopic            The Kafka topic to subscribe to and consume messages from.
   * @return A behavior instance of type Behavior, configured to handle messages for KafkaReceiver.
   */
  public static <TT extends SimTime> Behavior<DevsMessage> create(
      ActorRef<DevsMessage> devsComponent, ActorRef<DevsMessage> sender, String recieverId,
      String runId, Config pekkoKafkaConsumerConfig, String consumerTopic) {
    return Behaviors.setup(context -> new KafkaReceiver(context, devsComponent, sender,
        recieverId, runId, pekkoKafkaConsumerConfig, consumerTopic));
  }

  /**
   * Constructs a KafkaReceiver to handle Kafka message consumption and processing. Builds a
   * {@link KafkaMessageReceiver} from the provided Kafka configuration and passes it to the
   * abstract base class which owns all DEVS receiver behavior.
   *
   * @param context                  The actor context for this actor, providing access to the actor
   *                                 system, logging, and other contextual features.
   * @param devsComponent            The actor reference representing the DEVS component to interact
   *                                 with.
   * @param sender                   The actor reference responsible for sending messages to the
   *                                 KafkaReceiver.
   * @param receiverId               The receiver ID used to filter inbound messages.
   * @param runId                    Simulation run identifier; forwarded to
   *                                 {@link KafkaMessageReceiver} for stable group ID and header
   *                                 filtering.
   * @param pekkoKafkaConsumerConfig The configuration for the Pekko Kafka consumer.
   * @param consumerTopic            The Kafka topic to subscribe to and consume messages from.
   */
  public KafkaReceiver(ActorContext<DevsMessage> context, ActorRef<DevsMessage> devsComponent,
      ActorRef<DevsMessage> sender, String receiverId, String runId,
      Config pekkoKafkaConsumerConfig, String consumerTopic) {
    super(context, devsComponent, sender, receiverId,
        new KafkaMessageReceiver(pekkoKafkaConsumerConfig, consumerTopic, runId, receiverId,
            context.getSystem()));
  }

  /**
   * Returns the receiver ID this actor filters inbound messages on.
   *
   * @return the receiver ID string
   */
  public String getRecieverId() {
    return getReceiverId();
  }
}
