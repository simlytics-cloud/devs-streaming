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

/**
 * Transport SPI for outbound message publishing.
 *
 * <p>Implementations deliver serialized ISO-21175 JSON strings to a transport backend (e.g. Kafka,
 * NATS, DDS). The {@code sequence} parameter is a monotonically increasing key that the transport
 * MAY use (for example, Kafka uses it as the producer record key) or ignore.
 *
 * <p>A single {@link MessagePublisher} instance is created at actor startup and {@link #close()}
 * is called exactly once when the simulation terminates.
 */
public interface MessagePublisher {

  /**
   * Publish a serialized DEVS message payload.
   *
   * @param sequence monotonically increasing sequence number for this outbound stream; the
   *                 transport may use it as a record key or ordering hint, or ignore it entirely.
   * @param payload  the ISO-21175 JSON string to deliver to the transport.
   */
  void publish(long sequence, String payload);

  /**
   * Release all resources held by this publisher (connections, threads, buffers).
   * Called once when the simulation proxy actor stops.
   */
  void close();
}
