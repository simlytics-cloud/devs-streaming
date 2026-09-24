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

package devs.utils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.kafka.ConsumerSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility class providing static methods for Kafka operations such as topic management, admin
 * client creation, and producer/consumer construction. This class is not meant to be instantiated.
 */
public class KafkaUtils {

  private static final Logger logger = LoggerFactory.getLogger(KafkaUtils.class);
  private static final long TOPIC_OPERATION_TIMEOUT_MS = 30_000;
  private static final long TOPIC_OPERATION_RETRY_DELAY_MS = 250;

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
   * Ensures a topic is ready for a remote model start.
   *
   * <p>{@link TopicResetMode#USE_OR_CREATE} retains an existing topic or creates an absent one.
   * {@link TopicResetMode#RECREATE} deletes an existing topic, waits for Kafka to remove it, then
   * creates a fresh topic. Kafka topic deletion is asynchronous, so both operations use a bounded
   * retry period.
   *
   * @param topic topic to retain, create, or recreate
   * @param adminClient Kafka admin client
   * @param resetMode requested topic lifecycle behavior
   * @throws ExecutionException if Kafka rejects a topic operation
   * @throws InterruptedException if the current thread is interrupted while waiting
   * @throws TimeoutException if Kafka does not finish deleting or creating the topic in time
   */
  public static void ensureTopic(String topic, AdminClient adminClient, TopicResetMode resetMode)
      throws ExecutionException, InterruptedException, TimeoutException {
    long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TOPIC_OPERATION_TIMEOUT_MS);

    if (resetMode == TopicResetMode.RECREATE && topicExists(topic, adminClient, deadlineNanos)) {
      deleteTopicIfPresent(topic, adminClient, deadlineNanos);
      waitForTopicAbsence(topic, adminClient, deadlineNanos);
    }

    createTopicWhenAvailable(topic, adminClient, deadlineNanos);
  }

  private static void deleteTopicIfPresent(String topic, AdminClient adminClient, long deadlineNanos)
      throws ExecutionException, InterruptedException, TimeoutException {
    try {
      adminClient.deleteTopics(List.of(topic)).all()
          .get(remainingMillis(topic, deadlineNanos), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof UnknownTopicOrPartitionException)) {
        throw e;
      }
    }
  }

  private static boolean topicExists(String topic, AdminClient adminClient, long deadlineNanos)
      throws ExecutionException, InterruptedException, TimeoutException {
    return adminClient.listTopics(new ListTopicsOptions())
        .names()
        .get(remainingMillis(topic, deadlineNanos), TimeUnit.MILLISECONDS)
        .contains(topic);
  }

  private static void waitForTopicAbsence(String topic, AdminClient adminClient, long deadlineNanos)
      throws ExecutionException, InterruptedException, TimeoutException {
    while (topicExists(topic, adminClient, deadlineNanos)) {
      sleepUntilRetry(topic, deadlineNanos);
    }
  }

  private static void createTopicWhenAvailable(String topic, AdminClient adminClient,
      long deadlineNanos) throws ExecutionException, InterruptedException, TimeoutException {
    while (true) {
      try {
        adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all()
            .get(remainingMillis(topic, deadlineNanos), TimeUnit.MILLISECONDS);
        return;
      } catch (ExecutionException e) {
        if (!(e.getCause() instanceof TopicExistsException)) {
          throw e;
        }
        if (e.getCause().getMessage() != null && e.getCause().getMessage()
            .toLowerCase(Locale.ROOT).contains("marked for deletion")) {
          sleepUntilRetry(topic, deadlineNanos);
          continue;
        }
        if (topicExists(topic, adminClient, deadlineNanos)) {
          return;
        }
        sleepUntilRetry(topic, deadlineNanos);
      }
    }
  }

  private static long remainingMillis(String topic, long deadlineNanos) throws TimeoutException {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0) {
      throw new TimeoutException("Timed out waiting for Kafka topic " + topic
          + " to become available; it may still be marked for deletion");
    }
    return Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
  }

  private static void sleepUntilRetry(String topic, long deadlineNanos)
      throws InterruptedException, TimeoutException {
    long delayMillis = Math.min(TOPIC_OPERATION_RETRY_DELAY_MS, remainingMillis(topic, deadlineNanos));
    Thread.sleep(delayMillis);
  }

  /**
   * Deletes the specified topics from the Kafka cluster using the provided {@link AdminClient}.
   * Logs the deletion process and returns whether the deletion operation was completed
   * successfully.
   *
   * <p><b>Note:</b> Topic deletion is no longer required for run isolation. Records are now
   * keyed by {@code runId} and consumers filter by the {@code X-Run-Id} header, so multiple runs
   * can safely share a single topic. Rely on Kafka's time/size retention policy for cleanup
   * instead of deleting topics between runs.
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
    Properties adminProperties = ConfigUtils.copyProperties(props);
    adminProperties.putIfAbsent(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    return AdminClient.create(adminProperties);
  }

  /**
   * Creates an admin client from the shared, user-facing Kafka client configuration.
   *
   * @param kafkaConfig standard Kafka client configuration
   * @return an admin client configured with an independent property set
   */
  public static AdminClient createAdminClient(Config kafkaConfig) {
    return createAdminClient(toProperties(kafkaConfig));
  }

  /**
   * Converts the user-facing Kafka configuration to independent client properties.
   *
   * @param kafkaConfig standard Kafka client configuration
   * @return independent Kafka client properties
   */
  public static Properties toProperties(Config kafkaConfig) {
    return ConfigUtils.toProperties(kafkaConfig);
  }

  /**
   * Converts the shared Kafka configuration into the string properties carried by a remote model
   * start request.
   *
   * @param kafkaConfig standard Kafka client configuration
   * @return Kafka client properties keyed and valued as strings
   */
  public static Map<String, String> toStringProperties(Config kafkaConfig) {
    return kafkaConfig.entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(
            Map.Entry::getKey, entry -> String.valueOf(entry.getValue().unwrapped())));
  }

  /**
   * Creates Pekko Kafka consumer settings from the shared Kafka client configuration.
   *
   * <p>The adapter keeps Pekko connector defaults internal while preserving all standard Kafka
   * client properties supplied by users.
   *
   * @param kafkaConfig standard Kafka client configuration
   * @param groupId consumer group id
   * @return settings for a String-keyed and String-valued Pekko Kafka consumer
   */
  public static ConsumerSettings<String, String> createStringConsumerSettings(Config kafkaConfig,
      String groupId) {
    Properties consumerProperties = toProperties(kafkaConfig);
    consumerProperties.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    consumerProperties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    Config pekkoConsumerConfig = ConfigFactory.parseProperties(consumerProperties)
        .atPath("kafka-clients")
        .withFallback(ConfigFactory.load().getConfig("pekko.kafka.consumer"));
    return ConsumerSettings.create(pekkoConsumerConfig, new StringDeserializer(),
        new StringDeserializer()).withGroupId(groupId);
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
   * Creates a {@link KafkaProducer}{@code <String, String>} configured to key records by
   * {@code runId} (a String). Acknowledgments are required for all sent messages.
   *
   * <p>Use this producer in conjunction with {@code runId}-based record keying so that all records
   * for a given run land in the same partition, preserving FIFO ordering within a run while
   * allowing multiple runs to share a single topic.
   *
   * @param producerProperties the base properties to configure the KafkaProducer
   * @return a {@link KafkaProducer}{@code <String, String>} initialized with the provided
   *     properties
   */
  public static KafkaProducer<String, String> createStringKeyProducer(
      Properties producerProperties) {
    Properties properties = ConfigUtils.copyProperties(producerProperties);
    properties.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    return new KafkaProducer<>(properties);
  }

  /**
   * Creates a String-keyed producer from the shared, user-facing Kafka client configuration.
   *
   * @param kafkaConfig standard Kafka client configuration
   * @return a producer configured with the supplied properties and library-owned serializers
   */
  public static KafkaProducer<String, String> createStringKeyProducer(Config kafkaConfig) {
    return createStringKeyProducer(toProperties(kafkaConfig));
  }

  /**
   * Utility class providing static methods for Kafka operations such as topic management, admin
   * client creation, and producer/consumer construction. This class is not meant to be
   * instantiated.
   */
  private KafkaUtils() {
  }
}
