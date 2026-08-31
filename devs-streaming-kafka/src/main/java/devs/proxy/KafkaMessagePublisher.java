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
import devs.utils.ConfigUtils;
import devs.utils.KafkaUtils;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka implementation of {@link MessagePublisher}.
 *
 * <p>Wraps a {@link KafkaProducer}{@code <String, String>} and publishes each payload as a
 * {@link ProducerRecord} on {@code producerTopic} keyed by {@code runId}. Every record is
 * annotated with three Kafka headers:
 * <ul>
 *   <li>{@code X-Run-Id} — the simulation run identifier</li>
 *   <li>{@code X-Receiver-Id} — the target component name</li>
 *   <li>{@code X-Sequence} — the monotonically increasing sequence number (as a UTF-8 string)</li>
 * </ul>
 * Keying by {@code runId} routes all messages for a single run to the same partition, preserving
 * FIFO ordering within a run while allowing multiple runs to share a single topic. Consumers can
 * filter cheaply by inspecting the {@code X-Run-Id} header before deserializing the JSON payload.
 * The send delivery callback logs timing, partition, and offset information to SLF4J.
 */
public class KafkaMessagePublisher implements MessagePublisher {

  private static final Logger logger = LoggerFactory.getLogger(KafkaMessagePublisher.class);

  private final KafkaProducer<String, String> producer;
  private final String producerTopic;
  private final String componentName;
  private final String runId;
  private final String receiverId;

  /**
   * Constructs a publisher that will send to {@code producerTopic} using the Kafka producer
   * properties derived from {@code pekkoProducerConfig}.
   *
   * @param componentName       name of the component (used for error logging)
   * @param runId               simulation run identifier; used as the Kafka record key and as the
   *                            value of the {@code X-Run-Id} header
   * @param receiverId          target component name; written to the {@code X-Receiver-Id} header
   * @param producerTopic       the Kafka topic to publish to
   * @param pekkoProducerConfig Pekko config block containing Kafka producer properties
   */
  public KafkaMessagePublisher(String componentName, String runId, String receiverId,
      String producerTopic, Config pekkoProducerConfig) {
    this.componentName = componentName;
    this.runId = runId;
    this.receiverId = receiverId;
    this.producerTopic = producerTopic;
    Properties producerProperties = ConfigUtils.toProperties(pekkoProducerConfig);
    this.producer = KafkaUtils.createStringKeyProducer(producerProperties);
  }

  /**
   * Sends a serialized DEVS message to the Kafka topic, using {@code runId} as the record key and
   * attaching {@code X-Run-Id}, {@code X-Receiver-Id}, and {@code X-Sequence} headers.
   *
   * @param sequence monotonically increasing sequence number for this record (stored in the
   *                 {@code X-Sequence} header and in the payload)
   * @param payload  ISO-21175 JSON string to publish
   */
  @Override
  public void publish(long sequence, String payload) {
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(producerTopic, runId, payload);
    producerRecord.headers()
        .add("X-Run-Id", runId.getBytes(StandardCharsets.UTF_8))
        .add("X-Receiver-Id", receiverId.getBytes(StandardCharsets.UTF_8))
        .add("X-Sequence", Long.toString(sequence).getBytes(StandardCharsets.UTF_8));

    long sendStartNanos = System.nanoTime();

    producer.send(producerRecord, (metadata, exception) -> {
      double acknowledgementMs = (System.nanoTime() - sendStartNanos) / 1_000_000.0;
      if (exception != null) {
        logger.error("{} threw error writing to Kafka topic {}", componentName, producerTopic,
            exception);
      } else {
        logger.debug(
            "Java Kafka delivery acknowledgement: time={} ms, partition={}, offset={}",
            String.format("%.3f", acknowledgementMs),
            metadata.partition(),
            metadata.offset());
      }
    });
  }

  /**
   * Closes the underlying Kafka producer, flushing and releasing all resources.
   */
  @Override
  public void close() {
    producer.close();
  }
}
