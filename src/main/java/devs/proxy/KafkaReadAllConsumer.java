/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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
 */

package devs.proxy;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.typesafe.config.Config;
import devs.utils.DevsObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.ActorAttributes;
import org.apache.pekko.stream.Supervision;
import org.apache.pekko.stream.javadsl.Sink;

/**
 * Abstract base class for consuming all records from a Kafka topic using a Kafka consumer
 * implemented via the Apache Pekko Kafka connector. This class is designed for use in environments
 * that require continuous processing of all records in a Kafka topic.
 */
public abstract class KafkaReadAllConsumer {

  protected final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  private final Consumer.DrainingControl<Done> control;
  protected ActorSystem classicActorSystem;

  /**
   * Creates a consumer that constantly reads a Kafka topic for records and procceses each record
   * with the processRecord method.
   *
   * @param pekkoKafkaConsumerConfig A HOCON configuration for the Pekko Kafka consumer, see
   *                                 https://github.com/apache/pekko-connectors-kafka/blob/main/core/src/main/resources/reference.conf
   * @param classicActorSystem       classic actor system in which the stream runs
   * @param topic                    topic where messages are stored
   */
  public KafkaReadAllConsumer(Config pekkoKafkaConsumerConfig, ActorSystem classicActorSystem,
                              String topic) {
    this.classicActorSystem = classicActorSystem;
    objectMapper.registerModule(new Jdk8Module());
    ConsumerSettings<String, String> consumerSettings = ConsumerSettings
        .create(pekkoKafkaConsumerConfig, new StringDeserializer(), new StringDeserializer())
        .withGroupId(UUID.randomUUID().toString());

    // Using a Kafka consumer from the Pekko Kafka project because this consumer does a better job
    // of managing
    // threads. For example, the Java Kafka consumer uses an infinite loop to poll for data
    // consuming an entire thread for this purpose
    // The planSource consumer does not auto commit and subscribes to the webLvcTopic
    // The consumer's auto.offset.reset property is set to earliest so it always reads all data
    this.control = Consumer.plainSource(consumerSettings, Subscriptions.topics(topic))
        .map(record -> {
          processRecord(record);
          return NotUsed.notUsed();
        })
        // This supervisor strategy will drop the current record being processed in the event of
        // an
        // error and will continue consuming with the next message
        .withAttributes(
            ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()))
        // This statement enables logging of messages in the previous step of the stream
        .log("LopConsumerLog")
        // Connect to a sink to continuously run the stream and a materializer that gives a
        // control
        // to shut down the stream on command.
        .toMat(Sink.ignore(), Consumer::createDrainingControl).run(classicActorSystem);
  }

  public abstract void processRecord(ConsumerRecord<String, String> record);

  /**
   * Shuts down the Kafka consumer stream and releases associated resources.
   * <p>
   * This method ensures proper termination of the consumer stream by draining all pending messages
   * being processed before shutting down. The shutdown process uses the consumer's control object
   * to signal the underlying actor system dispatcher associated with the stream.
   * <p>
   * This is particularly important to ensure a graceful shutdown, avoiding unprocessed or partially
   * processed messages in case of cleanup or termination scenarios.
   */
  public void shutDown() {
    control.drainAndShutdown(classicActorSystem.getDispatcher());
  }
}
