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
import devs.SimulatorProvider;
import devs.iso.DevsMessage;
import devs.iso.time.SimTime;
import devs.utils.ModelUtils;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;


/**
 * This class represents a local proxy for interfacing with Kafka for sending and receiving
 * simulation messages. It extends {@link AbstractLocalProxy} and provides bidirectional Kafka
 * integration: outbound DEVS messages are forwarded to Kafka, and inbound Kafka messages are
 * delivered back to the local parent coordinator.
 *
 * <p>This class is a thin Kafka wrapper over {@link AbstractLocalProxy}. All DEVS-specific
 * bidirectional proxy logic lives in the base class hierarchy; this class only constructs the
 * Kafka-specific {@link KafkaMessagePublisher} and {@link KafkaMessageReceiver} adapters and
 * preserves the original public API ({@code create(ProxyProperties)}, nested {@code ProxyProperties}
 * record, and nested {@code KafkaProxySimulatorProvider}) unchanged.
 *
 * @param <T> The type parameter that extends {@code SimTime}, representing the simulation time.
 */
public class KafkaLocalProxy<T extends SimTime> extends AbstractLocalProxy<T> {

  /**
   * Represents the configuration properties required for the operation of a Kafka proxy. This
   * record encapsulates details about the Kafka producer and consumer, including their topics,
   * configurations, and the component name associated with the proxy.
   * <p>
   * Fields:
   * <ul>
   *   <li>{@code runId}: Simulation run identifier. Used as the Kafka record key (publisher) and
   *       as the filter value for the {@code X-Run-Id} header (consumer), so that multiple runs can
   *       safely share a single Kafka topic.</li>
   *   <li>{@code localComponentName}: Specifies the local component name using the proxy, which
   *       helps in identifying the proxy's role.</li>
   *   <li>{@code producerTopic}: Defines the Kafka topic to which the producer sends messages.</li>
   *   <li>{@code kafkaProducerConfig}: Holds configuration for initializing the Kafka producer.</li>
   *   <li>{@code remoteComponentName}: The remote component name.</li>
   *   <li>{@code consumerTopic}: Specifies the Kafka topic from which the consumer reads
   *       messages.</li>
   *   <li>{@code kafkaConsumerConfig}: Contains configuration for initializing the Kafka
   *       consumer.</li>
   * </ul>
   */
  public static record ProxyProperties(String runId, String localComponentName,
      String producerTopic, Config kafkaProducerConfig, String remoteComponentName,
      String consumerTopic, Config kafkaConsumerConfig) {

  }

  /**
   * Provides a KafkaLocalProxy as a DEVS Simulator for the underlying proxy model.
   */
  public static class KafkaProxySimulatorProvider<T extends SimTime> implements
      SimulatorProvider<T> {
    protected final ProxyProperties properties;

    public KafkaProxySimulatorProvider(ProxyProperties properties) {
      this.properties = properties;
    }

    @Override
    public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context,
        T initialTime) {
      return context.spawn(KafkaLocalProxy.create(properties),
          ModelUtils.toLegalActorName(properties.remoteComponentName()));
    }

    @Override
    public String getModelIdentifier() {
      return properties.remoteComponentName();
    }

  }

  /**
   * Creates a new behavior that sets up a KafkaLocalProxy instance with the specified proxy
   * properties.
   *
   * @param <TT>  The type parameter that extends SimTime, representing the simulation time.
   * @param props The proxy properties used to configure and initialize the KafkaLocalProxy.
   * @return A behavior that initializes and returns a KafkaLocalProxy actor.
   */
  public static <TT extends SimTime> Behavior<DevsMessage> create(ProxyProperties props) {
    return Behaviors.setup(context -> new KafkaLocalProxy<TT>(context, props));
  }

  /**
   * Initializes a new instance of the KafkaLocalProxy class. This constructor builds a
   * {@link KafkaMessagePublisher} (outbound) and a {@link KafkaMessageReceiver} (inbound) from
   * the provided {@code ProxyProperties} and passes both to the abstract base class which owns
   * all DEVS bidirectional proxy behavior.
   *
   * @param context The actor context in which this proxy will operate.
   * @param props   The proxy properties containing configuration for the Kafka consumer and
   *                producer, including topics, component name, and Kafka configurations.
   */
  public KafkaLocalProxy(ActorContext<DevsMessage> context, ProxyProperties props) {
    super(context,
        props.remoteComponentName(),
        props.localComponentName(),
        new KafkaMessagePublisher(props.remoteComponentName(), props.runId(),
            props.remoteComponentName(), props.producerTopic(), props.kafkaProducerConfig()),
        new KafkaMessageReceiver(props.kafkaConsumerConfig(), props.consumerTopic(),
            props.runId(), props.localComponentName, context.getSystem()));
  }
}
