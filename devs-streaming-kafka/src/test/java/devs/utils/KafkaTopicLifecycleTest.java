/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and
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
 */

package devs.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires KAFKA instance")
class KafkaTopicLifecycleTest {

  @Test
  void createsRetainsAndExplicitlyRecreatesTopics() throws Exception {
    Config kafkaConfig = ConfigFactory.load().getConfig("kafka.properties");
    String topic = "topic-lifecycle-" + UUID.randomUUID();

    try (AdminClient adminClient = KafkaUtils.createAdminClient(kafkaConfig)) {
      KafkaUtils.ensureTopic(topic, adminClient, TopicResetMode.USE_OR_CREATE);
      assertTopicExists(adminClient, topic);

      KafkaUtils.ensureTopic(topic, adminClient, TopicResetMode.USE_OR_CREATE);
      assertTopicExists(adminClient, topic);

      KafkaUtils.ensureTopic(topic, adminClient, TopicResetMode.RECREATE);
      assertTopicExists(adminClient, topic);

      adminClient.deleteTopics(List.of(topic)).all().get(30, TimeUnit.SECONDS);
    }
  }

  private void assertTopicExists(AdminClient adminClient, String topic) throws Exception {
    assertTrue(adminClient.listTopics().names().get(30, TimeUnit.SECONDS).contains(topic));
  }
}
