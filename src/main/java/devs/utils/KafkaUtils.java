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

package devs.utils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility class providing static methods for Kafka operations such as topic management, admin
 * client creation, and producer/consumer construction. This class is not meant to be instantiated.
 */
public class KafkaUtils {

  private static final Logger logger = LoggerFactory.getLogger(KafkaUtils.class);

  /**
   * Creates multiple topics in Kafka using the provided {@link AdminClient}. The number of
   * partitions and replication factor for each topic can be optionally specified. If any topics
   * already exist, they are ignored and treated as successfully created.
   *
   * @param topics            a list of topic names to be created
   * @param adminClient       an instance of {@link AdminClient} to connect to the Kafka cluster
   * @param partitions        an optional number of partitions for the topics; if absent, default
   *                          configuration is used
   * @param replicationFactor an optional replication factor for the topics; if absent, default
   *                          configuration is used
   * @return true if the topics were successfully created or already existed, false otherwise
   * @throws ExecutionException   if an error occurs during topic creation
   * @throws InterruptedException if the operation is interrupted
   */
  public static boolean createTopics(List<String> topics, AdminClient adminClient,
      Optional<Integer> partitions, Optional<Short> replicationFactor)
      throws ExecutionException, InterruptedException {
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

  /**
   * Creates a topic in Kafka using the provided {@link AdminClient}. The number of partitions and
   * replication factor for the topic can be optionally specified. If the topic already exists, it
   * is ignored and treated as successfully created.
   *
   * @param topic             the name of the topic to be created
   * @param adminClient       an instance of {@link AdminClient} to connect to the Kafka cluster
   * @param partitions        an optional number of partitions for the topic; if absent, the default
   *                          configuration is used
   * @param replicationFactor an optional replication factor for the topic; if absent, the default
   *                          configuration is used
   * @return true if the topic was successfully created or already existed, false otherwise
   * @throws ExecutionException   if an error occurs during the topic creation
   * @throws InterruptedException if the operation is interrupted
   */
  public static boolean createTopic(String topic, AdminClient adminClient,
      Optional<Integer> partitions, Optional<Short> replicationFactor)
      throws ExecutionException, InterruptedException {
    return createTopics(Collections.singletonList(topic), adminClient, partitions,
        replicationFactor);
  }

  /**
   * Deletes the specified topics from the Kafka cluster using the provided {@link AdminClient}.
   * Logs the deletion process and returns whether the deletion operation was completed
   * successfully.
   *
   * @param topics      a list of topic names to be deleted
   * @param adminClient an instance of {@link AdminClient} to connect to the Kafka cluster
   * @return true if the deletion operation was marked as done, false otherwise
   * @throws ExecutionException   if an error occurs during the topic deletion process
   * @throws InterruptedException if the operation is interrupted
   */
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

  /**
   * Lists all the topics available in the Kafka cluster using the given {@link AdminClient}. This
   * method connects to the cluster, retrieves the topic names, and logs the count of topics
   * retrieved. It also prints each topic name to the console.
   *
   * @param adminClient an instance of {@link AdminClient} used to connect to the Kafka cluster
   * @throws ExecutionException   if an error occurs during the retrieval of topic names
   * @throws InterruptedException if the operation is interrupted while fetching topic names
   */
  static void listTopics(AdminClient adminClient) throws ExecutionException, InterruptedException {
    logger.debug("Getting topics");
    ListTopicsResult topics = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000));
    Set<String> topicNames = topics.names().get();
    logger.debug("Got " + topicNames.size() + " topics");
    topicNames.forEach(System.out::println);
  }

  /**
   * Creates an {@link AdminClient} instance with the provided configuration properties. An
   * additional default timeout configuration is set for the client.
   *
   * @param props the configuration properties for connecting to the Kafka cluster
   * @return an instance of {@link AdminClient} initialized with the provided properties
   */
  public static AdminClient createAdminClient(Properties props) {
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    return AdminClient.create(props);
  }

  /**
   * Creates a KafkaConsumer configured to read all messages from the beginning of the topics. The
   * method modifies the provided {@link Properties} object to include necessary consumer
   * configurations such as unique group ID, key and value deserializers, disabling auto-commit, and
   * setting the offset reset policy to "earliest".
   *
   * @param consumerProperties the properties to configure the KafkaConsumer, which will be updated
   *                           with additional settings for reading all messages
   * @return a {@link KafkaConsumer} instance configured based on the input and additional settings
   */
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

  /**
   * Creates a KafkaProducer instance with the provided properties. The method configures the
   * producer to use appropriate serializers for keys and values, and ensures that acknowledgments
   * are required for all sent messages.
   *
   * @param producerProperties the properties to configure the KafkaProducer
   * @return a KafkaProducer instance initialized with the provided properties
   */
  public static KafkaProducer<Long, String> createProducer(Properties producerProperties) {
    producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
    producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.LongSerializer");
    producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    return new KafkaProducer<>(producerProperties);
  }

  /**
   * Utility class providing static methods for Kafka operations such as topic management, admin
   * client creation, and producer/consumer construction. This class is not meant to be
   * instantiated.
   */
  private KafkaUtils() {
  }
}
