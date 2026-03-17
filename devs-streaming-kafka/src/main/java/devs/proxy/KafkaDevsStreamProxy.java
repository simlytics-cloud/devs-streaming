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

import devs.iso.DevsMessage;
import devs.iso.ModelTerminated;
import devs.iso.SimulationTerminate;
import java.util.Properties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.typesafe.config.Config;
import devs.iso.SimulationInitMessage;
import devs.iso.time.SimTime;
import devs.utils.ConfigUtils;
import devs.utils.DevsObjectMapper;
import devs.utils.KafkaUtils;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KafkaDevsStreamProxy is a Pekko Typed Actor designed to bridge DEVS framework messages with a
 * Kafka messaging system. This class serializes DEVS messages and publishes them to a specified
 * Kafka topic.
 *
 * @param <T> The type that extends SimTime to represent simulation time in the DEVS framework.
 */
public class KafkaDevsStreamProxy<T extends SimTime> extends AbstractBehavior<DevsMessage> {


  private final KafkaProducer<Long, String> producer;
  private final String producerTopic;
  private long index;
  private final String componentName;
  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  private final Logger logger = LoggerFactory.getLogger(this.getClass());


  /**
   * Creates a new instance of KafkaDevsStreamProxy actor with the specified configuration. This
   * method initializes the actor and prepares it to handle DEVS messages, enabling the integration
   * with a Kafka messaging system.
   *
   * @param componentName       the name of the component being represented by the actor
   * @param producerTopic       the Kafka topic to which DEVS messages will be published
   * @param pekkoProducerConfig the Pekko configuration containing Kafka producer properties
   * @return a Behavior instance for the KafkaDevsStreamProxy actor
   */
  public static Behavior<DevsMessage> create(String componentName, String producerTopic,
                                             Config pekkoProducerConfig) {
    return Behaviors.setup(context -> new KafkaDevsStreamProxy<>(context, componentName,
        producerTopic, pekkoProducerConfig));
  }


  /**
   * Constructs an instance of the KafkaDevsStreamProxy actor. This constructor initializes the
   * actor with the provided parameters, sets up the Kafka producer, and integrates with the DEVS
   * framework.
   *
   * @param context             the actor context in which this actor operates
   * @param componentName       the name of the component represented by the actor
   * @param producerTopic       the Kafka topic to which DEVS messages will be published
   * @param pekkoProducerConfig the Pekko configuration containing Kafka producer properties
   */
  public KafkaDevsStreamProxy(ActorContext<DevsMessage> context, String componentName,
                              String producerTopic, Config pekkoProducerConfig) {
    super(context);
    this.componentName = componentName;
    this.producerTopic = producerTopic;
    this.objectMapper.registerModule(new Jdk8Module());

    Properties producerProperties = ConfigUtils.toProperties(pekkoProducerConfig);
    this.producer = KafkaUtils.createProducer(producerProperties);

  }

  /**
   * Creates the message receive handler for the KafkaDevsStreamProxy actor. This method sets up
   * behavior to process incoming messages of type DevsMessage, delegating their handling to
   * specific logic within the actor.
   *
   * @return a Receive object configured to handle DevsMessage instances
   */
  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsMessage.class, this::onDevsMessage);
    return builder.build();
  }


  /**
   * Processes an incoming DEVS message and handles its serialization and publishing to a Kafka
   * topic. The method is responsible for managing message-specific logic, including serialization,
   * communication with Kafka, and actor behavior updates based on the type of the message.
   *
   * @param devsMessage the incoming DEVS message to process. This can be any instance implementing
   *                    the {@code DevsMessage} interface, including specialized messages such as
   *                    {@code InitSimMessage} or {@code SimulationDone}.
   * @return a {@code Behavior<DevsMessage>} representing the next behavior of the actor. This could
   * be the same behavior for continuing operations or a stopped behavior if a termination message
   * is processed.
   */
  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    String record = null;
    try {
      if (devsMessage instanceof SimulationInitMessage<?> initSimMessage) {
        record = objectMapper.writeValueAsString(initSimMessage.getSimulationInit());
      } else {
        record = objectMapper.writeValueAsString(devsMessage);
      }
    } catch (JsonProcessingException e) {
      getContext().getLog().error("Could not deserialize message to string: " + devsMessage);
      throw new RuntimeException(e);
    }
    producer.send(new ProducerRecord<Long, String>(producerTopic, index, record), new Callback() {
      @Override
      public void onCompletion(RecordMetadata m, Exception e) {
        if (e != null) {
          System.err
              .println(componentName + " threw error writing to Kafka topic " + producerTopic);
          e.printStackTrace();
        } else {
          logger.debug(componentName + " sending " + devsMessage.getClass().getCanonicalName()
              + " to " + producerTopic);
        }
      }
    });
    index = index + 1;
    if (devsMessage instanceof SimulationTerminate<?> || devsMessage instanceof ModelTerminated<?>) {
      this.producer.close();
      return Behaviors.stopped();
    }
    return Behaviors.same();
  }

}
