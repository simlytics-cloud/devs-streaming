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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.typesafe.config.Config;
import devs.msg.DevsMessage;
import devs.msg.InitSim;
import devs.msg.InitSimMessage;
import devs.msg.ModelDone;
import devs.msg.SimulationDone;
import devs.msg.time.SimTime;
import devs.utils.DevsObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.ActorAttributes;
import org.apache.pekko.stream.Supervision;
import org.apache.pekko.stream.javadsl.Sink;
import org.slf4j.Logger;


/**
 * KafkaReceiver is a Pekko actor implementation that interacts with a Kafka topic to consume
 * messages using the Pekko Kafka library. It processes incoming messages, transforms them into DEVS
 * framework messages, and forwards them to a given DEVS simulation component.
 * <p>
 * This class makes use of Pekko Streams for consuming messages from Kafka and manages the stream
 * lifecycle, including starting and shutting down the stream. It uses a custom ObjectMapper to
 * deserialize Kafka records into DevsMessage objects.
 * <p>
 * Key features include: - Setting up an Pekko Kafka consumer. - Processing Kafka records into DEVS
 * framework messages. - Forwarding processed DEVS messages to the appropriate component. - Graceful
 * shutdown when the simulation completes.
 * <p>
 * KafkaReceiver extends AbstractBehavior to define and handle incoming DEVS framework messages
 * within the Pekko framework.
 */
public class KafkaReceiver extends AbstractBehavior<DevsMessage> {

  private final Consumer.DrainingControl<Done> control;
  private final ActorRef<DevsMessage> sender;

  private final ActorRef<DevsMessage> devsComponent;
  private final Logger logger;
  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  /**
   * Creates a new behavior instance of KafkaReceiver to handle Kafka message consumption and
   * process the data.
   *
   * @param <TT>                     A generic type that extends SimTime, representing the
   *                                 simulation time concept in DEVS framework.
   * @param devsComponent            The actor reference representing the DEVS component to interact
   *                                 with.
   * @param sender                   The actor reference responsible for sending messages to the
   *                                 KafkaReceiver.
   * @param pekkoKafkaConsumerConfig The configuration for the Pekko Kafka consumer.
   * @param consumerTopic            The Kafka topic to subscribe to and consume messages from.
   * @return A behavior instance of type Behavior, configured to handle messages for KafkaReceiver.
   */
  public static <TT extends SimTime> Behavior<DevsMessage> create(
      ActorRef<DevsMessage> devsComponent, ActorRef<DevsMessage> sender,
      Config pekkoKafkaConsumerConfig, String consumerTopic) {
    return Behaviors.setup(context -> new KafkaReceiver(context, devsComponent, sender,
        pekkoKafkaConsumerConfig, consumerTopic));
  }

  /**
   * Constructs a KafkaReceiver to handle Kafka message consumption and processing.
   *
   * @param context                  The actor context for this actor, providing access to the actor
   *                                 system, logging, and other contextual features.
   * @param devsComponent            The actor reference representing the DEVS component to interact
   *                                 with.
   * @param sender                   The actor reference responsible for sending messages to the
   *                                 KafkaReceiver.
   * @param pekkoKafkaConsumerConfig The configuration for the Pekko Kafka consumer.
   * @param consumerTopic            The Kafka topic to subscribe to and consume messages from.
   */
  public KafkaReceiver(ActorContext<DevsMessage> context, ActorRef<DevsMessage> devsComponent,
      ActorRef<DevsMessage> sender, Config pekkoKafkaConsumerConfig, String consumerTopic) {
    super(context);
    this.devsComponent = devsComponent;
    this.sender = sender;
    this.logger = context.getLog();
    ConsumerSettings<String, String> consumerSettings = ConsumerSettings
        .create(pekkoKafkaConsumerConfig, new StringDeserializer(), new StringDeserializer())
        .withGroupId(UUID.randomUUID().toString());

    // Using a Kafka consumer from the Pekko Kafka project because this consumer does a better job
    // of managing
    // threads. For example, the Java Kafka consumer uses an infinite loop to poll for data
    // consuming an entire thread for this purpose
    // The planSource consumer does not auto commit and subscribes to the webLvcTopic
    // The consumer's auto.offset.reset property is set to earliest so it always reads all data
    this.control = Consumer.plainSource(consumerSettings, Subscriptions.topics(consumerTopic))
        .map(record -> {
          logger.debug("Kafka received record: " + record.value());
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
        .toMat(Sink.ignore(), Consumer::createDrainingControl).run(getContext().getSystem());
  }


  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsMessage.class, this::onDevsMessage);
    return builder.build();
  }

  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    if (devsMessage instanceof InitSim<?> initSim) {
      devsMessage = new InitSimMessage<>(initSim, sender);
    }
    devsComponent.tell(devsMessage);
    if (devsMessage instanceof SimulationDone<?> || devsMessage instanceof ModelDone<?>) {
      control.shutdown();
      return Behaviors.stopped();
    } else {
      return Behaviors.same();
    }
  }

  private void processRecord(ConsumerRecord<String, String> record) {
    DevsMessage devsMessage = null;
    try {
      devsMessage = objectMapper.readValue(record.value(), DevsMessage.class);
    } catch (JsonProcessingException e) {
      logger.error("Could not deserialize JSON record " + record.value());
      e.printStackTrace();
    }
    getContext().getSelf().tell(devsMessage);
  }

}
