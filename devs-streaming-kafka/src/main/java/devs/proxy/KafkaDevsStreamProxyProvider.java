/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and
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
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;

/**
 * Supplies outbound Kafka proxy actors for a configured component and simulation run.
 *
 * @param <T> simulation time type
 */
public class KafkaDevsStreamProxyProvider<T extends SimTime> implements SimulatorProvider<T> {
  private final String componentName;
  private final String runId;
  private final Config kafkaConfig;
  private final String producerTopic;
  private final TimeEndpointPolicy policy;

  /**
   * Creates a provider using the legacy time endpoint policy.
   *
   * @param componentName represented component identifier
   * @param runId simulation run identifier
   * @param producerTopic topic receiving outbound DEVS messages
   * @param kafkaConfig shared Kafka client configuration
   */
  public KafkaDevsStreamProxyProvider(String componentName, String runId,
      String producerTopic, Config kafkaConfig) {
    this(componentName, runId, producerTopic, kafkaConfig, TimeEndpointPolicy.legacy());
  }

  /**
   * Creates a provider with an explicit time representation policy.
   *
   * @param componentName represented component identifier
   * @param runId simulation run identifier
   * @param producerTopic topic receiving outbound DEVS messages
   * @param kafkaConfig shared Kafka client configuration
   * @param policy time endpoint policy for spawned proxies
   */
  public KafkaDevsStreamProxyProvider(String componentName, String runId,
      String producerTopic, Config kafkaConfig, TimeEndpointPolicy policy) {
    this.componentName = componentName;
    this.runId = runId;
    this.kafkaConfig = kafkaConfig;
    this.producerTopic = producerTopic;
    this.policy = java.util.Objects.requireNonNull(policy);
  }

  /**
   * Returns the time endpoint policy passed to each spawned proxy.
   *
   * @return configured time endpoint policy
   */
  public TimeEndpointPolicy getTimeEndpointPolicy() {
    return policy;
  }

  /**
   * Spawns a named outbound proxy; the proxy does not use the supplied initial time.
   *
   * @param context parent actor context
   * @param initialTime initial simulation time, unused by this provider
   * @return spawned Kafka proxy actor
   */
  @Override
  public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime) {
    return context.spawn(
        KafkaDevsStreamProxy.create(componentName, runId, producerTopic,
            kafkaConfig, policy),
        componentName + "KafkaDevsStreamProxy");
  }

  /**
   * Returns the component represented by the proxy.
   *
   * @return component identifier
   */
  @Override
  public String getModelIdentifier() {
    return componentName;
  }
}
