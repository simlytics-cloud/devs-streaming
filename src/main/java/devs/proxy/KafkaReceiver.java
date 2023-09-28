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
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.stream.ActorAttributes;
import akka.stream.Supervision;
import akka.stream.javadsl.Sink;
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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;


public class KafkaReceiver extends AbstractBehavior<DevsMessage> {

  private final Consumer.DrainingControl<Done> control;
  private final ActorRef<DevsMessage> sender;

  private final ActorRef<DevsMessage> devsComponent;

  public static <TT extends SimTime> Behavior<DevsMessage> create(
      ActorRef<DevsMessage> devsComponent,
      ActorRef<DevsMessage> sender,
      Config akkaKafkaConsumerConfig, String consumerTopic) {
    return Behaviors.setup(context -> new KafkaReceiver(context,
        devsComponent, sender, akkaKafkaConsumerConfig, consumerTopic));
  }

  public KafkaReceiver(ActorContext<DevsMessage> context, ActorRef<DevsMessage> devsComponent,
      ActorRef<DevsMessage> sender,
      Config akkaKafkaConsumerConfig, String consumerTopic) {
    super(context);
    this.devsComponent = devsComponent;
    this.sender = sender;
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
    this.control = Consumer.plainSource(consumerSettings, Subscriptions.topics(consumerTopic))
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
        .run(getContext().getSystem());
  }

  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
  ;


  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsMessage.class, this::onDevsMessage);
    return builder.build();
  }

  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    if (devsMessage instanceof InitSim<?> initSim) {
      devsMessage = new InitSimMessage((InitSim<?>) initSim, sender);
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
      getContext().getLog().error("Could not deserialize " + record.value() + " as DevsMessage");
      throw new RuntimeException(e);
    }
    getContext().getSelf().tell(devsMessage);
  }

}

