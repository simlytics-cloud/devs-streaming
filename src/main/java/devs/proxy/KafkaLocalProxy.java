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
import com.google.common.base.Optional;
import com.typesafe.config.Config;
import devs.SimulatorProvider;
import devs.msg.DevsMessage;
import devs.msg.InitSimMessage;
import devs.msg.time.SimTime;
import devs.utils.DevsObjectMapper;
import devs.utils.ModelUtils;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
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


/**
 * This class represents a local proxy for interfacing with Kafka for sending and receiving
 * simulation messages. It extends the {@code KafkaDevsStreamProxy} class and provides capabilities
 * to consume records from a Kafka topic and forward them as DEVS messages to a local actor system.
 *
 * @param <T> The type parameter that extends {@code SimTime}, representing the simulation time.
 */
public class KafkaLocalProxy<T extends SimTime> extends KafkaDevsStreamProxy<T> {

  /**
   * Represents the configuration properties required for the operation of a Kafka proxy. This
   * record encapsulates details about the Kafka producer and consumer, including their topics,
   * configurations, and the component name associated with the proxy.
   * <p>
   * Fields: - componentName: Specifies the name of the component using the proxy, which helps in
   * identifying the proxy's role. - producerTopic: Defines the Kafka topic to which the producer
   * sends messages. - kafkaProducerConfig: Holds optional configuration for initializing the Kafka
   * producer. - consumerTopic: Specifies the Kafka topic from which the consumer reads messages. -
   * kafkaConsumerConfig: Contains optional configuration for initializing the Kafka consumer.
   */
  public static record ProxyProperties(String componentName, String producerTopic,
                                       Config kafkaProducerConfig, String consumerTopic,
                                       Config kafkaConsumerConfig) {

  }

  public static class KafkaProxySimulatorProvider<T extends SimTime> implements SimulatorProvider<T> {
    protected final ProxyProperties properties;

    public KafkaProxySimulatorProvider(ProxyProperties properties) {
      this.properties = properties;
    }

    @Override
    public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context,
        T initialTime) {
      return context.spawn(KafkaLocalProxy.create(properties), ModelUtils.toLegalActorName(properties.componentName()));
    }

    @Override
    public String getModelIdentifier() {
      return properties.componentName();
    }
    
  }

  /**
   * Creates a new behavior that sets up a KafkaLocalProxy instance with the specified proxy
   * properties.
   *
   * @param <TT>  The type parameter that extends SimTime, representing the simulation time.
   * @param props The proxy properties used to configure and initialize the KafkaLocalProxy.
   * @return A behavior that initializes and returns a KafkaLocalProxy actor.
   */
  public static <TT extends SimTime> Behavior<DevsMessage> create(ProxyProperties props) {
    return Behaviors.setup(context -> new KafkaLocalProxy<TT>(context, props));
  }

  protected final Consumer.DrainingControl<Done> control;
  protected final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
  protected Optional<ActorRef<DevsMessage>> localParentCoordinator;

  /**
   * Initializes a new instance of the KafkaLocalProxy class. This constructor sets up a Kafka
   * consumer and integrates it with the DEVS framework. It uses the Pekko Kafka project for better
   * thread management compared to the default Kafka consumer.
   *
   * @param context The actor context in which this proxy will operate.
   * @param props   The proxy properties containing configuration for the Kafka consumer and
   *                producer, including topics, component name, and Kafka configurations.
   */
  public KafkaLocalProxy(ActorContext<DevsMessage> context, ProxyProperties props) {
    super(context, props.componentName(), props.producerTopic(), props.kafkaProducerConfig());

    ConsumerSettings<String, String> consumerSettings = ConsumerSettings
        .create(props.kafkaConsumerConfig(), new StringDeserializer(), new StringDeserializer())
        .withGroupId(UUID.randomUUID().toString());

    // Using a Kafka consumer from the Pekko Kafka project because this consumer
    // does a better job of managing
    // threads. For example, the Java Kafka consumer uses an infinite loop to poll
    // for data
    // consuming an entire thread for this purpose
    // The planSource consumer does not auto commit and subscribes to the
    // webLvcTopic
    // The consumer's auto.offset.reset property is set to earliest so it always
    // reads all data
    this.control = Consumer
        .plainSource(consumerSettings, Subscriptions.topics(props.consumerTopic())).map(record -> {
          System.out.println("Kafka received record: " + record.value());
          processRecord(record);
          return NotUsed.notUsed();
        })
        // This supervisor strategy will drop the current record being processed in the
        // event of an
        // error and will continue consuming with the next message
        .withAttributes(ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()))
        // This statement enables logging of messages in the previous step of the stream
        .log("LopConsumerLog")
        // Connect to a sink to continuously run the stream and a materializer that
        // gives a control
        // to shut down the stream on command.
        .toMat(Sink.ignore(), Consumer::createDrainingControl).run(getContext().getSystem());
  }

  /**
   * Creates a message processing behavior for the actor. This method constructs and returns a
   * {@link Receive} object that defines how the actor should handle messages of type
   * {@link DevsMessage}.
   *
   * @return A {@link Receive} instance configured to handle {@link DevsMessage} objects.
   */
  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsMessage.class, this::onDevsMessage);
    return builder.build();
  }

  /**
   * Handles an incoming {@link DevsMessage} and performs specific actions based on the message
   * type. If the message is an instance of {@link InitSimMessage}, it sets the local parent
   * coordinator. The method then passes the message to the superclass for Kafka-related
   * processing.
   *
   * @param devsMessage the incoming DEVS message to process. This can be any instance implementing
   *                    the {@code DevsMessage} interface, including specialized messages such as
   *                    {@link InitSimMessage}.
   * @return a {@code Behavior<DevsMessage>} representing the next behavior of the actor, which is
   * the same behavior in this case to continue processing messages.
   */
  @Override
  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    // Set the local coordinator
    if (devsMessage instanceof InitSimMessage initSimMessage) {
      this.localParentCoordinator = Optional.of(initSimMessage.getParent());
    }
    // Then pass the message to super to be sent to Kafka
    super.onDevsMessage(devsMessage);
    return Behaviors.same();
  }

  /**
   * Processes a Kafka record and deserializes it into a {@link DevsMessage} object. The method
   * checks the presence of the local parent coordinator and forwards the message to it if
   * available. If the local parent coordinator is not set, it logs an error and terminates the
   * application.
   *
   * @param record the Kafka record to process, containing a key-value pair where both the key and
   *               the value are strings. The value is expected to represent a JSON-encoded
   *               {@link DevsMessage}.
   */
  private void processRecord(ConsumerRecord<String, String> record) {
    DevsMessage devsMessage = null;
    try {
      devsMessage = objectMapper.readValue(record.value(), DevsMessage.class);
    } catch (JsonProcessingException e) {
      System.err.println("Could not deserialize JSON record " + record.value());
      e.printStackTrace();
      System.exit(1);
    }
    if (localParentCoordinator.isPresent()) {
      localParentCoordinator.get().tell(devsMessage);
    } else {
      System.err.println(
          "ERROR: Received the following DevsMessage from Kafka before learning the identity of \n"
              + "the local parent coordinator via an InitSimMessage: " + record.value());
      System.exit(1);
    }
  }

}
