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
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * KafkaDevsStreamProxy is a Pekko Typed Actor designed to bridge DEVS framework messages with a
 * Kafka messaging system. This class serializes DEVS messages and publishes them to a specified
 * Kafka topic.
 *
 * <p>This class is a thin Kafka wrapper over {@link AbstractDevsStreamProxy}. All DEVS-specific
 * proxy logic (serialization, sequence numbering, termination) lives in the base class; this class
 * only constructs the Kafka-specific {@link KafkaMessagePublisher}.
 *
 * @param <T> The type that extends SimTime to represent simulation time in the DEVS framework.
 */
public class KafkaDevsStreamProxy<T extends SimTime> extends AbstractDevsStreamProxy<T> {

  /**
   * Creates a new instance of KafkaDevsStreamProxy actor with the specified configuration. This
   * method initializes the actor and prepares it to handle DEVS messages, enabling the integration
   * with a Kafka messaging system.
   *
   * @param componentName       the name of the component being represented by the actor; this same
   *                            value is used as the target component name written to the
   *                            {@code X-Receiver-Id} header
   * @param runId               simulation run identifier; used as the Kafka record key and as the
   *                            value of the {@code X-Run-Id} header on every published record
   * @param producerTopic       the Kafka topic to which DEVS messages will be published
   * @param pekkoProducerConfig the Pekko configuration containing Kafka producer properties
   * @return a Behavior instance for the KafkaDevsStreamProxy actor
   */
  public static Behavior<DevsMessage> create(String componentName, String runId,
      String producerTopic, Config pekkoProducerConfig) {
    return Behaviors.setup(context -> new KafkaDevsStreamProxy<>(context, componentName, runId,
        producerTopic, pekkoProducerConfig));
  }

  /**
   * Constructs an instance of the KafkaDevsStreamProxy actor. This constructor builds a
   * {@link KafkaMessagePublisher} from the provided Kafka configuration and passes it to the
   * abstract base class which owns all DEVS proxy behavior.
   *
   * @param context             the actor context in which this actor operates
   * @param componentName       the name of the component represented by the actor; this same value
   *                            is forwarded to {@link KafkaMessagePublisher} as the receiver id
   * @param runId               simulation run identifier; forwarded to {@link KafkaMessagePublisher}
   * @param producerTopic       the Kafka topic to which DEVS messages will be published
   * @param pekkoProducerConfig the Pekko configuration containing Kafka producer properties
   */
  public KafkaDevsStreamProxy(ActorContext<DevsMessage> context, String componentName,
      String runId, String producerTopic, Config pekkoProducerConfig) {
    super(context, componentName,
        new KafkaMessagePublisher(componentName, runId, componentName, producerTopic,
            pekkoProducerConfig));
  }
}
