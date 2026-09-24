package devs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Map;
import java.util.Properties;
import org.apache.pekko.kafka.ConsumerSettings;
import org.junit.jupiter.api.Test;

class KafkaUtilsTest {

  @Test
  void adaptsSharedKafkaConfigForNativeAndPekkoClients() {
    Config kafkaConfig = ConfigFactory.parseString("""
        bootstrap.servers = "broker.example:9092"
        security.protocol = SASL_SSL
        sasl.mechanism = PLAIN
        sasl.jaas.config = "login-module"
        """);

    Properties properties = KafkaUtils.toProperties(kafkaConfig);
    Map<String, String> requestProperties = KafkaUtils.toStringProperties(kafkaConfig);
    ConsumerSettings<String, String> consumerSettings =
        KafkaUtils.createStringConsumerSettings(kafkaConfig, "test-group");

    assertEquals("broker.example:9092", properties.getProperty("bootstrap.servers"));
    assertEquals("SASL_SSL", requestProperties.get("security.protocol"));
    assertEquals("login-module", consumerSettings.getProperty("sasl.jaas.config"));
    assertEquals("test-group", consumerSettings.getProperty("group.id"));
    assertEquals("false", consumerSettings.getProperty("enable.auto.commit"));
    assertEquals("earliest", consumerSettings.getProperty("auto.offset.reset"));
  }
}
