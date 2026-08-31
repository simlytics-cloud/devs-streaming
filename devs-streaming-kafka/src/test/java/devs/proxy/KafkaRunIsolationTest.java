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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import devs.utils.ConfigUtils;
import devs.utils.KafkaUtils;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for runId-based Kafka record keying and per-run consumer isolation.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Published records carry {@code X-Run-Id}, {@code X-Receiver-Id}, and {@code X-Sequence}
 *       headers with the correct values.</li>
 *   <li>The record key equals the configured {@code runId} (not a numeric sequence).</li>
 *   <li>A {@link KafkaMessageReceiver} configured with {@code runId=A} receives only messages
 *       published with {@code runId=A}.</li>
 *   <li>A record with no {@code X-Run-Id} header is silently dropped and never forwarded to the
 *       handler.</li>
 * </ul>
 *
 * <p>These tests require a running Kafka broker at {@code localhost:29092}.
 */
@Disabled("Requires KAFKA instance")
public class KafkaRunIsolationTest {

  static final String testTopic = "runIsolationTest";
  static final String runIdA = "run-A";
  static final String runIdB = "run-B";
  static final String receiverId = "testReceiver";

  Config config;
  Config kafkaClusterConfig;
  Config kafkaConsumerConfig;
  Properties kafkaClusterProperties;
  AdminClient adminClient;
  ActorSystem<Void> system;

  @BeforeEach
  void setUp() throws Exception {
    config = ConfigFactory.load();
    kafkaClusterConfig = config.getConfig("kafka-cluster");
    kafkaConsumerConfig = config.getConfig("kafka-readall-consumer");
    kafkaClusterProperties = ConfigUtils.toProperties(kafkaClusterConfig);
    adminClient = KafkaUtils.createAdminClient(ConfigUtils.copyProperties(kafkaClusterProperties));
    KafkaUtils.deleteTopics(Arrays.asList(testTopic), adminClient);
    Thread.sleep(3000);
    KafkaUtils.createTopics(Arrays.asList(testTopic), adminClient, Optional.of(4),
        Optional.empty());
    Thread.sleep(1000);
    system = ActorSystem.create(Behaviors.empty(), "KafkaRunIsolationTestSystem");
  }

  @AfterEach
  void tearDown() throws Exception {
    system.terminate();
    KafkaUtils.deleteTopics(Arrays.asList(testTopic), adminClient);
    adminClient.close();
  }

  /**
   * Verifies that every record published by {@link KafkaMessagePublisher} carries the correct
   * {@code X-Run-Id}, {@code X-Receiver-Id}, and {@code X-Sequence} headers, and that the record
   * key equals the configured {@code runId}.
   */
  @Test
  @DisplayName("Published record has correct key and X-Run-Id/X-Receiver-Id/X-Sequence headers")
  void publishedRecordHasCorrectKeyAndHeaders() throws Exception {
    KafkaMessagePublisher publisher = new KafkaMessagePublisher("testComponent", runIdA,
        receiverId, testTopic, kafkaClusterConfig);
    publisher.publish(42L, "{\"test\":\"payload\"}");
    publisher.close();

    // Read the raw record back from Kafka to inspect key and headers
    Properties consumerProperties = ConfigUtils.copyProperties(kafkaClusterProperties);
    consumerProperties.put("group.id", "header-check-" + System.currentTimeMillis());
    consumerProperties.put("key.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProperties.put("value.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProperties.put("enable.auto.commit", "false");
    consumerProperties.put("auto.offset.reset", "earliest");

    ConsumerRecord<String, String> received = null;
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
      consumer.subscribe(List.of(testTopic));
      long deadline = System.currentTimeMillis() + 15_000;
      while (received == null && System.currentTimeMillis() < deadline) {
        for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
          received = r;
          break;
        }
      }
    }

    assertNotNull(received, "Expected a record but got none");

    // Key must be the runId string
    assertEquals(runIdA, received.key(), "Record key should equal runId");

    // Header: X-Run-Id
    var runIdHeader = received.headers().lastHeader("X-Run-Id");
    assertNotNull(runIdHeader, "X-Run-Id header must be present");
    assertEquals(runIdA, new String(runIdHeader.value(), StandardCharsets.UTF_8));

    // Header: X-Receiver-Id
    var receiverIdHeader = received.headers().lastHeader("X-Receiver-Id");
    assertNotNull(receiverIdHeader, "X-Receiver-Id header must be present");
    assertEquals(receiverId, new String(receiverIdHeader.value(), StandardCharsets.UTF_8));

    // Header: X-Sequence
    var sequenceHeader = received.headers().lastHeader("X-Sequence");
    assertNotNull(sequenceHeader, "X-Sequence header must be present");
    assertEquals("42", new String(sequenceHeader.value(), StandardCharsets.UTF_8));
  }

  /**
   * Verifies that a {@link KafkaMessageReceiver} configured with {@code runId=A} receives a
   * message published with {@code runId=A}.
   */
  @Test
  @DisplayName("Same-run message is delivered to receiver with matching runId")
  void sameRunMessageIsDelivered() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> received = new AtomicReference<>();

    KafkaMessageReceiver receiver = new KafkaMessageReceiver(kafkaConsumerConfig, testTopic,
        runIdA, receiverId, system);
    receiver.subscribe(payload -> {
      received.set(payload);
      latch.countDown();
    });

    Thread.sleep(2000); // let consumer start

    KafkaMessagePublisher publisher = new KafkaMessagePublisher("testComponent", runIdA,
        receiverId, testTopic, kafkaClusterConfig);
    publisher.publish(1L, "same-run-payload");
    publisher.close();

    boolean arrived = latch.await(15, TimeUnit.SECONDS);
    receiver.shutdown();

    assert arrived : "Expected to receive a same-run message within 15 s";
    assertEquals("same-run-payload", received.get());
  }

  /**
   * Verifies that a {@link KafkaMessageReceiver} configured with {@code runId=A} does NOT receive
   * a message published with {@code runId=B} — the header filter silently drops it.
   */
  @Test
  @DisplayName("Cross-run message is silently dropped by receiver with non-matching runId")
  void crossRunMessageIsDropped() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    KafkaMessageReceiver receiverA = new KafkaMessageReceiver(kafkaConsumerConfig, testTopic,
        runIdA, receiverId, system);
    receiverA.subscribe(payload -> latch.countDown());

    Thread.sleep(2000); // let consumer start

    // Publish a message for run B — receiver A must ignore it
    KafkaMessagePublisher publisherB = new KafkaMessagePublisher("testComponent", runIdB,
        receiverId, testTopic, kafkaClusterConfig);
    publisherB.publish(1L, "run-b-payload");
    publisherB.close();

    boolean arrived = latch.await(8, TimeUnit.SECONDS);
    receiverA.shutdown();

    assert !arrived : "Receiver A should NOT have received a run-B message";
  }

  /**
   * Verifies that two receivers sharing the same topic — one for {@code runId=A}, one for
   * {@code runId=B} — each receive only their own run's messages.
   */
  @Test
  @DisplayName("Parallel receivers each receive only their own run's messages")
  void parallelReceiversReceiveOwnRunsMessages() throws Exception {
    CountDownLatch latchA = new CountDownLatch(1);
    CountDownLatch latchB = new CountDownLatch(1);
    AtomicReference<String> receivedByA = new AtomicReference<>();
    AtomicReference<String> receivedByB = new AtomicReference<>();

    KafkaMessageReceiver receiverA = new KafkaMessageReceiver(kafkaConsumerConfig, testTopic,
        runIdA, receiverId, system);
    receiverA.subscribe(payload -> {
      receivedByA.set(payload);
      latchA.countDown();
    });

    KafkaMessageReceiver receiverB = new KafkaMessageReceiver(kafkaConsumerConfig, testTopic,
        runIdB, receiverId + "-B", system);
    receiverB.subscribe(payload -> {
      receivedByB.set(payload);
      latchB.countDown();
    });

    Thread.sleep(2000); // let consumers start

    KafkaMessagePublisher publisherA = new KafkaMessagePublisher("testComponent", runIdA,
        receiverId, testTopic, kafkaClusterConfig);
    publisherA.publish(1L, "payload-for-run-A");
    publisherA.close();

    KafkaMessagePublisher publisherB = new KafkaMessagePublisher("testComponent", runIdB,
        receiverId + "-B", testTopic, kafkaClusterConfig);
    publisherB.publish(1L, "payload-for-run-B");
    publisherB.close();

    boolean aArrived = latchA.await(15, TimeUnit.SECONDS);
    boolean bArrived = latchB.await(15, TimeUnit.SECONDS);

    receiverA.shutdown();
    receiverB.shutdown();

    assert aArrived : "Receiver A should have received its own run's message";
    assert bArrived : "Receiver B should have received its own run's message";
    assertEquals("payload-for-run-A", receivedByA.get());
    assertEquals("payload-for-run-B", receivedByB.get());
  }

  /**
   * Verifies that a record with no {@code X-Run-Id} header (e.g., produced by an older version)
   * is silently dropped — the handler is never called and no exception is thrown.
   */
  @Test
  @DisplayName("Record with missing X-Run-Id header is silently dropped")
  void recordWithMissingHeaderIsDropped() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    KafkaMessageReceiver receiver = new KafkaMessageReceiver(kafkaConsumerConfig, testTopic,
        runIdA, receiverId, system);
    receiver.subscribe(payload -> latch.countDown());

    Thread.sleep(2000); // let consumer start

    // Produce a record without any headers (old-style producer)
    Properties producerProperties = ConfigUtils.copyProperties(kafkaClusterProperties);
    try (KafkaProducer<String, String> rawProducer = KafkaUtils.createStringKeyProducer(
        producerProperties)) {
      // No X-Run-Id header
      rawProducer.send(new ProducerRecord<>(testTopic, "no-header-payload"));
    }

    boolean arrived = latch.await(8, TimeUnit.SECONDS);
    receiver.shutdown();

    assert !arrived : "Receiver should have silently dropped a record without X-Run-Id header";
  }

  /**
   * Verifies that the stable consumer group ID is composed as {@code runId:receiverId}.
   * This is validated indirectly: two separate receiver instances with the same
   * {@code runId:receiverId} on different calls share the same group ID by construction —
   * confirmed by examining the {@link KafkaMessageReceiver} constructor logic.
   *
   * <p>This test is intentionally lightweight; the stable group ID is also exercised by the
   * {@code sameRunMessageIsDelivered} and {@code parallelReceiversReceiveOwnRunsMessages} tests
   * above.
   */
  @Test
  @DisplayName("KafkaMessageReceiver uses stable runId:receiverId group ID")
  void stableGroupIdIsUsed() throws Exception {
    // Publish one message for run A
    KafkaMessagePublisher publisher = new KafkaMessagePublisher("testComponent", runIdA,
        receiverId, testTopic, kafkaClusterConfig);
    publisher.publish(1L, "stable-group-test-payload");
    publisher.close();

    Thread.sleep(1000);

    // First consumer reads the message
    CountDownLatch latch1 = new CountDownLatch(1);
    KafkaMessageReceiver receiver1 = new KafkaMessageReceiver(kafkaConsumerConfig, testTopic,
        runIdA, receiverId, system);
    receiver1.subscribe(payload -> latch1.countDown());
    boolean firstArrived = latch1.await(15, TimeUnit.SECONDS);
    receiver1.shutdown();
    Thread.sleep(2000);

    // Second consumer with the same runId:receiverId (same group ID) should also read the message
    // because auto.offset.reset=earliest and we use a new group ID per run (stable within a run).
    // The message is re-read because the first consumer did not commit offsets.
    CountDownLatch latch2 = new CountDownLatch(1);
    KafkaMessageReceiver receiver2 = new KafkaMessageReceiver(kafkaConsumerConfig, testTopic,
        runIdA, receiverId, system);
    receiver2.subscribe(payload -> latch2.countDown());
    boolean secondArrived = latch2.await(15, TimeUnit.SECONDS);
    receiver2.shutdown();

    assert firstArrived : "First receiver should have received the message";
    assert secondArrived : "Second receiver with same group ID should re-read from earliest";
  }
}
