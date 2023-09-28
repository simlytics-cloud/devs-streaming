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

package devs;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TopicExistsException;

class TestProducer {

  static String server = "pkc-2396y.us-east-1.aws.confluent.cloud:9092";
  //    static String sasl_jaas_config = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" password=\"Endpoint=sb://devs-kafka.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=m1rXj3BH4MXW7IS1uxzrU37phILf6nfYegOSL2ick8g=\";";
  static String sasl_jaas_config = "org.apache.kafka.common.security.plain.PlainLoginModule   required username='ZUGJB5NVWUO54D6T'   password='FsLpugdXoxIe4dGrw6ZxNok2vuNOi0mc3ZRhY+s9JPxeg5I2GQjoKVY0ROy2UAor';";
  private static boolean consuming = true;

  private static class DemoProducerCallback implements Callback {

    private final int messageNumber;

    public DemoProducerCallback(int messageNumber) {
      this.messageNumber = messageNumber;
    }

    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
      if (e != null) {
        System.out.println("Error in message %d".formatted(this.messageNumber));
        e.printStackTrace();
      } else {
        System.out.println("Successfully sent message %d".formatted(this.messageNumber));
        System.out.println(recordMetadata.toString());
      }
    }
  }

  public static void main(String[] args)
      throws InterruptedException, ExecutionException, TimeoutException {
    String topic = "test-topic";
    AdminClient adminClient = createAdminClient();
    createTopic("graphics", adminClient);
    //deleteTopic("test", adminClient);
    listTopics(adminClient);
    Runnable consumerRunnable = new Runnable() {
      @Override
      public void run() {
        consumeRecords(topic);
      }
    };
    Thread thread = new Thread(consumerRunnable);
    //thread.start();

    Thread.sleep(6 * 1000);
    //produceRecords(topic);

    Thread.sleep(10 * 1000);
    consuming = false;
    Thread.sleep(5 * 1000);
  }

  static void deleteTopic(String topic, AdminClient adminClient) {
    System.out.println("Deleting topic " + topic);
    boolean succeeded = false;
    try {
      DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(
          Collections.singleton(topic));
      deleteTopicsResult.all().get();
      succeeded = deleteTopicsResult.all().isDone();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    System.out.println("Result for deleting " + topic + " is " + succeeded);
  }

  static void createTopic(String topic, AdminClient adminClient)
      throws ExecutionException, InterruptedException {
    System.out.println("Creating topic");
    boolean succeeded = false;
    try {
      CreateTopicsResult createTopicsResult = adminClient.createTopics(
          Collections.singleton(new NewTopic(topic, 1, (short) 3)),
          new CreateTopicsOptions().timeoutMs(10000));

      createTopicsResult.all().get();
      succeeded = createTopicsResult.all().isDone();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof TopicExistsException) {
        System.out.println("The topic " + topic + " exists ");
        succeeded = true;
      }
    }
    System.out.println("Result for " + topic + " is " + succeeded);
  }

  static void listTopics(AdminClient adminClient) throws ExecutionException, InterruptedException {
    System.out.println("Getting topics");
    ListTopicsResult topics = adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000));
    Set<String> topicNames = topics.names().get();
    System.out.println("Got " + topicNames.size() + " topics");
    topicNames.forEach(System.out::println);
  }

  static void produceRecords(String topic) {

    Properties props = new Properties();
    props.put("bootstrap.servers", server);
    //props.put("security.protocol", "SASL_SSL");
    //props.put("sasl.mechanism", "PLAIN");
    //props.put("sasl.jaas.config", sasl_jaas_config);
    props.put("acks", "1");
    props.put("retries", 3);
    props.put("linger.ms", 1);
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);

    Producer<String, String> producer = new KafkaProducer<>(props);

    for (int i = 0; i < 100; i++) {
      producer.send(
          new ProducerRecord<String, String>(topic, Integer.toString(i), Integer.toString(i)),
          new DemoProducerCallback(i));
    }

    System.out.println("Closing producer");
    producer.close();
    System.out.println("Producer closed");
  }

  static void consumeRecords(String topic) {

    Properties props = new Properties();
    props.put("bootstrap.servers", server);
    //props.put("security.protocol", "SASL_SSL");
    //props.put("sasl.mechanism", "PLAIN");
    //props.put("sasl.jaas.config", sasl_jaas_config);
    props.put("group.id", "TestConsumer");
    props.put("key.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");

    KafkaConsumer<String, String> consumer =
        new KafkaConsumer<String, String>(props);

    consumer.subscribe(Collections.singletonList(topic));
    Duration timeout = Duration.ofMillis(1000);
    while (consuming) {
      ConsumerRecords<String, String> records =
          consumer.poll(timeout);
      System.out.println(System.currentTimeMillis() +
          "--  waiting for data...");
      for (ConsumerRecord<String, String> record : records) {
        System.out.printf("offset = %d, key = %s, value = %s\n",
            record.offset(), record.key(), record.value());
      }
      /** To commit manually, use the lines below and set enable.auto.commit to false in consumer properties
       for (TopicPartition tp: consumer.assignment())
       System.out.println("Committing offset at position:" +
       consumer.position(tp));
       consumer.commitSync();

       */
    }

    System.out.println("Closing consumer");
    consumer.close();
  }

  static AdminClient createAdminClient() {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, server);
    props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
    props.put("sasl.mechanism", "PLAIN");
    props.put("sasl.jaas.config", sasl_jaas_config);
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
    AdminClient admin = AdminClient.create(props);
    return admin;
  }
}
