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

import java.util.function.Consumer;

/**
 * Transport SPI for inbound message reception.
 *
 * <p>Implementations subscribe to a transport backend (e.g. Kafka topic, NATS subject, DDS topic)
 * and deliver each raw ISO-21175 JSON string payload to the provided {@code handler}.
 *
 * <p>{@link #subscribe(Consumer)} is called exactly once at actor startup. {@link #shutdown()} is
 * called once when the simulation terminates to drain and release all underlying resources.
 */
public interface MessageReceiver {

  /**
   * Begin delivering each raw received ISO-21175 JSON payload to {@code handler}.
   *
   * <p>This method is called once at actor construction time. The {@code handler} will be invoked
   * (potentially on a transport thread) for every message that arrives on the inbound subscription.
   *
   * @param handler callback that receives each raw JSON string payload; must not be {@code null}.
   */
  void subscribe(Consumer<String> handler);

  /**
   * Stop and drain the underlying subscription, releasing all transport resources.
   * Called once when the simulation terminates.
   */
  void shutdown();
}
