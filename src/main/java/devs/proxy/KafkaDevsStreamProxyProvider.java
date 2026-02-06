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

public class KafkaDevsStreamProxyProvider<T extends SimTime> implements SimulatorProvider<T> {
  private final String componentName;
  private final Config pekkoProducerConfig;
  private final String producerTopic;
  
  public KafkaDevsStreamProxyProvider(String componentName, String producerTopic, Config pekkoProducerConfig) {
    this.componentName = componentName;
    this.pekkoProducerConfig = pekkoProducerConfig;
    this.producerTopic = producerTopic;
  }

  @Override
  public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime) {
    return context.spawn(KafkaDevsStreamProxy.create(componentName, producerTopic, pekkoProducerConfig), "KafkaDevsStreamProxy");
  }

  @Override
  public String getModelIdentifier() {
    return componentName;
  }
}
