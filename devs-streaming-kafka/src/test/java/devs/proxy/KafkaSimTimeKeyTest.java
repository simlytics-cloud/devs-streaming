package devs.proxy;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class KafkaSimTimeKeyTest {
  @Test
  void nestedExactTimeRoundTripsWithoutNumericCoercionOrJavaClassTags() throws Exception {
    ObjectMapper mapper = DevsObjectMapper.buildObjectMapper(TimeWireProfile.EXACT_TIME_V1);
    for (SimTime time : List.of(DecimalSimTime.of(new BigDecimal("0.12345678901234567890123456789")),
        QuantizedSimTime.of(Rational.of(1, 3)), DecimalSimTime.infinity(), QuantizedSimTime.infinity())) {
      var key = new KafkaSimTimeKey<>(time, 42L);
      String json = mapper.writeValueAsString(key);
      var tree = mapper.readTree(json);
      assertEquals(mapper.readTree(mapper.writeValueAsString(time)), tree.get("simTime"));
      assertEquals(42L, tree.get("index").longValue());
      assertFalse(json.contains("devs.iso.time"));
      var restored = mapper.readValue(json, KafkaSimTimeKey.class);
      assertEquals(time, restored.getSimTime());
      assertEquals(time.getClass(), restored.getSimTime().getClass());
      assertEquals(42L, restored.getIndex());
    }
  }

  @Test
  void rationalKeyUsesReducedStringNumeratorAndDenominator() throws Exception {
    ObjectMapper mapper = DevsObjectMapper.buildObjectMapper(TimeWireProfile.EXACT_TIME_V1);
    var tree = mapper.readTree(mapper.writeValueAsString(
        new KafkaSimTimeKey<>(QuantizedSimTime.of(Rational.of(2, 6)), 7L)));
    assertEquals(mapper.readTree("{\"timeType\":\"rational\",\"numerator\":\"1\",\"denominator\":\"3\"}"),
        tree.get("simTime"));
  }
}