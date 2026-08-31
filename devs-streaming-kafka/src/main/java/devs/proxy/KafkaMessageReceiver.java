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
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.kafka.javadsl.Consumer.DrainingControl;
import org.apache.pekko.stream.ActorAttributes;
import org.apache.pekko.stream.Supervision;
import org.apache.pekko.stream.javadsl.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka implementation of {@link MessageReceiver} — the single, shared Kafka consumer pipeline
 * used by both {@link KafkaReceiver} and {@link KafkaLocalProxy}.
 *
 * <p>Uses Pekko Kafka's {@code Consumer.plainSource} with a stable, run-scoped consumer group id
 * ({@code runId:receiverId}) so that the consumer can resume from its committed offset after a
 * restart and so that multiple runs can safely share a single Kafka topic without cross-run
 * pollution. Records whose {@code X-Run-Id} header does not match the configured {@code runId}
 * are silently dropped <em>before</em> JSON deserialization — making per-run isolation cheap.
 *
 * <p>The resuming supervision strategy drops malformed records and continues without stopping the
 * stream.
 *
 * <p>{@link #subscribe(java.util.function.Consumer)} starts the Pekko stream and may be called
 * only once. {@link #shutdown()} drains and stops the stream.
 */
public class KafkaMessageReceiver implements MessageReceiver {

  private static final Logger logger = LoggerFactory.getLogger(KafkaMessageReceiver.class);

  private final Config pekkoKafkaConsumerConfig;
  private final String consumerTopic;
  private final String runId;
  private final String receiverId;
  private final ActorSystem<?> system;
  private DrainingControl<Done> control;

  /**
   * Constructs a receiver that will consume from {@code consumerTopic}, accepting only records
   * whose {@code X-Run-Id} header equals {@code runId}.
   *
   * @param pekkoKafkaConsumerConfig Pekko config block containing Kafka consumer properties
   * @param consumerTopic            the Kafka topic to subscribe to
   * @param runId                    simulation run identifier; records with a different (or absent)
   *                                 {@code X-Run-Id} header are dropped before deserialization
   * @param receiverId               target component name; combined with {@code runId} to form the
   *                                 stable consumer group id {@code runId:receiverId}
   * @param system                   the Pekko actor system used to materialize the stream
   */
  public KafkaMessageReceiver(Config pekkoKafkaConsumerConfig, String consumerTopic,
      String runId, String receiverId, ActorSystem<?> system) {
    this.pekkoKafkaConsumerConfig = pekkoKafkaConsumerConfig;
    this.consumerTopic = consumerTopic;
    this.runId = runId;
    this.receiverId = receiverId;
    this.system = system;
  }

  /**
   * Starts the Kafka consumer stream, delivering each record value to {@code handler}.
   *
   * <p>This method must be called exactly once, at actor startup. The stream uses:
   * <ul>
   *   <li>A stable group id ({@code runId:receiverId}) so the consumer can resume after a
   *       restart.</li>
   *   <li>A pre-deserialization header filter that drops records whose {@code X-Run-Id} header
   *       does not match the configured {@code runId}.</li>
   *   <li>Resuming supervision so that a malformed record is dropped and the stream continues.</li>
   *   <li>Stream logging via {@code .log("LopConsumerLog")}.</li>
   * </ul>
   *
   * @param handler callback that receives each raw ISO-21175 JSON payload string
   */
  @Override
  public void subscribe(java.util.function.Consumer<String> handler) {
    ConsumerSettings<String, String> consumerSettings = ConsumerSettings
        .create(pekkoKafkaConsumerConfig, new StringDeserializer(), new StringDeserializer())
        .withGroupId(runId + ":" + receiverId);

    // Using a Kafka consumer from the Pekko Kafka project because this consumer does a better job
    // of managing threads. For example, the Java Kafka consumer uses an infinite loop to poll for
    // data consuming an entire thread for this purpose.
    // The plainSource consumer does not auto commit and subscribes to the consumerTopic.
    // The consumer's auto.offset.reset property is set to earliest so it always reads all data.
    this.control = Consumer
        .plainSource(consumerSettings, Subscriptions.topics(consumerTopic))
        // Drop records from other runs cheaply, before JSON deserialization.
        .filter(record -> {
          String recordRunId = headerValue(record, "X-Run-Id");
          if (runId.equals(recordRunId)) {
            return true;
          }
          logger.debug(
              "Dropping record on topic {} with X-Run-Id={} (expected {})",
              consumerTopic, recordRunId, runId);
          return false;
        })
        .map(record -> {
          logger.debug("Kafka receiver received record on topic {}: {}", consumerTopic,
              record.value());
          handler.accept(record.value()); // handler is java.util.function.Consumer<String>
          return NotUsed.notUsed();
        })
        // This supervisor strategy will drop the current record being processed in the event of
        // an error and will continue consuming with the next message
        .withAttributes(ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()))
        // This statement enables logging of messages in the previous step of the stream
        .log("LopConsumerLog")
        // Connect to a sink to continuously run the stream and a materializer that gives a
        // control to shut down the stream on command.
        .toMat(Sink.ignore(), Consumer::createDrainingControl)
        .run(system);
  }

  /**
   * Drains and shuts down the Kafka consumer stream, releasing all resources.
   */
  @Override
  public void shutdown() {
    if (control != null) {
      control.shutdown();
    }
  }

  /**
   * Extracts the value of a Kafka record header as a UTF-8 string.
   *
   * @param record     the Kafka consumer record
   * @param headerName the name of the header to look up
   * @return the header value decoded as UTF-8, or {@code null} if the header is absent
   */
  private String headerValue(ConsumerRecord<String, String> record, String headerName) {
    Header header = record.headers().lastHeader(headerName);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
