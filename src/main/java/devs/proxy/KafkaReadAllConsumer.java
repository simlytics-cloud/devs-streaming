/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs.proxy;


import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.stream.ActorAttributes;
import akka.stream.Supervision;
import akka.stream.javadsl.Sink;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.typesafe.config.Config;
import devs.utils.DevsObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;

public abstract class KafkaReadAllConsumer {

  protected final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
  ;
  private final Consumer.DrainingControl<Done> control;
  protected ActorSystem classicActorSystem;

  /**
   * Creates a consumer that constantly reads a Kafka topic for records and procceses each record
   * with the processRecord method.
   *
   * @param akkaKafkaConsumerConfig A HOCON configuration for the Alpakka Kafka consumer, see
   *                                https://github.com/akka/alpakka-kafka/blob/master/core/src/main/resources/reference.conf
   * @param classicActorSystem      classic actor system in which the stream runs
   * @param topic                   topic where messages are stored
   */
  public KafkaReadAllConsumer(Config akkaKafkaConsumerConfig, ActorSystem classicActorSystem,
      String topic) {
    this.classicActorSystem = classicActorSystem;
    objectMapper.registerModule(new Jdk8Module());
    ConsumerSettings<String, String> consumerSettings =
        ConsumerSettings.create(akkaKafkaConsumerConfig, new StringDeserializer(),
                new StringDeserializer())
            .withGroupId(java.util.UUID.randomUUID().toString());

    // Using a Kafka consumer from the Alpakka Kafka project because this consumer does a better job of managing
    //  threads.  For example, the Java Kafka consumer uses an infinite loop to poll for data
    //  consuming an entire thread for this purpose
    //  The planSource consumer does not auto commit and subscribes to the webLvcTopic
    //  The consumer's auto.offset.reset property is set to earliest so it always reads all data
    this.control = Consumer.plainSource(consumerSettings, Subscriptions.topics(topic))
        .map(record -> {
          processRecord(record);
          return NotUsed.notUsed();
        })
        // This supervisor strategy will drop the current record being processed in the event of an
        //   error and will continue consuming with the next message
        .withAttributes(ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()))
        // This statement enables logging of messages in the previous step of the stream
        .log("LopConsumerLog")
        // Connect to a sink to continuously run the stream and a materializer that gives a control
        //   to shut down the stream on command.
        .toMat(Sink.ignore(), Consumer::createDrainingControl)
        .run(classicActorSystem);
  }

  public abstract void processRecord(ConsumerRecord<String, String> record);

  public void shutDown() {
    control.drainAndShutdown(classicActorSystem.getDispatcher());
  }
}

