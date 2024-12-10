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

package devs.utils;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class KafkaUtils {

  private final static Logger logger = LoggerFactory.getLogger(KafkaUtils.class);

  public static boolean createTopics(List<String> topics, AdminClient adminClient,
      Optional<Integer> partitions,
      Optional<Short> replicationFactor) throws ExecutionException, InterruptedException {
    logger.debug("Creating topics: " + String.join(", ", topics));
    boolean succeeded = false;
    try {
      CreateTopicsResult createTopicsResult = adminClient.createTopics(
          topics.stream().map(topic -> new NewTopic(topic, partitions, replicationFactor)).toList(),
          new CreateTopicsOptions().timeoutMs(5000));

      createTopicsResult.all().get();
      succeeded = createTopicsResult.all().isDone();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof TopicExistsException) {
        logger.debug("The topic exists");
        succeeded = true;
      }
    }
    logger.debug("Result for creating topics is " + succeeded);
    return succeeded;
  }

  public static boolean createTopic(String topic, AdminClient adminClient,
      Optional<Integer> partitions,
      Optional<Short> replicationFactor) throws ExecutionException, InterruptedException {
    return createTopics(Collections.singletonList(topic), adminClient, partitions,
        replicationFactor);
  }

  public static boolean deleteTopics(List<String> topics, AdminClient adminClient)
      throws ExecutionException, InterruptedException {
    System.out.print("Deleting topics " + String.join(", ", topics));
    boolean succeeded = false;
    DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(topics);
    try {
      deleteTopicsResult.all().get();
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
    succeeded = deleteTopicsResult.all().isDone();
    logger.debug("Result for deleting topics is " + succeeded);
    return succeeded;
  }

  static void listTopics(AdminClient adminClient) throws ExecutionException, InterruptedException {
    logger.debug("Getting topics");
    ListTopicsResult topics = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000));
    Set<String> topicNames = topics.names().get();
    logger.debug("Got " + topicNames.size() + " topics");
    topicNames.forEach(System.out::println);
  }

  public static AdminClient createAdminClient(Properties props) {
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    return AdminClient.create(props);
  }

  public static KafkaConsumer<String, String> buildReadAllConsumer(Properties consumerProperties) {
    consumerProperties.put("group.id", UUID.randomUUID().toString());
    consumerProperties.put("key.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProperties.put("value.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProperties.put("enable.auto.commit", "false");
    consumerProperties.put("auto.offset.reset", "earliest");
    return new KafkaConsumer<>(consumerProperties);
  }

  public static KafkaProducer<Long, String> createProducer(Properties producerProperties) {
    producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
    producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.LongSerializer");
    producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    return new KafkaProducer<>(producerProperties);
  }

  private KafkaUtils() {
  }
}
