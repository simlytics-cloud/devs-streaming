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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.typesafe.config.Config;
import devs.msg.DevsMessage;
import devs.msg.InitSimMessage;
import devs.msg.ModelDone;
import devs.msg.SimulationDone;
import devs.msg.time.SimTime;
import devs.utils.ConfigUtils;
import devs.utils.DevsObjectMapper;
import devs.utils.KafkaUtils;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class KafkaDevsStreamProxy<T extends SimTime> extends AbstractBehavior<DevsMessage> {


  private final KafkaProducer<Long, String> producer;
  private final String producerTopic;
  private long index;
  private final String componentName;
  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  private final Logger logger = LoggerFactory.getLogger(this.getClass());


  public static Behavior<DevsMessage> create(
      String componentName,
      String producerTopic,
      Config akkaProducerConfig
  ) {
    return Behaviors.setup(context -> new KafkaDevsStreamProxy(context, componentName,
        producerTopic, akkaProducerConfig));
  }


  public KafkaDevsStreamProxy(ActorContext<DevsMessage> context,
      String componentName, String producerTopic,
      Config akkaProducerConfig) {
    super(context);
    this.componentName = componentName;
    this.producerTopic = producerTopic;
    this.objectMapper.registerModule(new Jdk8Module());

    Properties producerProperties = ConfigUtils.toProperties(akkaProducerConfig);
    this.producer = KafkaUtils.createProducer(producerProperties);

  }

  @Override
  public Receive<DevsMessage> createReceive() {
    ReceiveBuilder<DevsMessage> builder = newReceiveBuilder();
    builder.onMessage(DevsMessage.class, this::onDevsMessage);
    return builder.build();
  }


  Behavior<DevsMessage> onDevsMessage(DevsMessage devsMessage) {
    String record = null;
    try {
      if (devsMessage instanceof InitSimMessage<?> initSimMessage) {
        record = objectMapper.writeValueAsString(initSimMessage.getInitSim());
      } else {
        record = objectMapper.writeValueAsString(devsMessage);
      }
    } catch (JsonProcessingException e) {
      getContext().getLog().error("Could not deserialize message to string: " + devsMessage);
      throw new RuntimeException(e);
    }
    producer.send(new ProducerRecord<Long, String>(producerTopic, index,
        record), new Callback() {
      @Override
      public void onCompletion(RecordMetadata m, Exception e) {
        if (e != null) {
          System.err.println(
              componentName + " threw error writing to Kafka topic " + producerTopic);
          e.printStackTrace();
        } else {
          logger.debug(
              componentName + " sending " + devsMessage.getClass().getCanonicalName() + " to "
                  + producerTopic);
        }
      }
    });
    index = index + 1;
    if (devsMessage instanceof SimulationDone<?> || devsMessage instanceof ModelDone<?>) {
      this.producer.close();
      return Behaviors.stopped();
    }
    return Behaviors.same();
  }

}
