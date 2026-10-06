package devs.observation.postgresql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.observation.Observation;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresObservationSerializationTest {
  private final ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();

  static List<SimTime> times() {
    return List.of(LongSimTime.create(9007199254740993L), DoubleSimTime.create(0.25),
        DecimalSimTime.of(new BigDecimal("0.12345678901234567890123456789")),
        DecimalSimTime.of(new BigDecimal("1E+1000")), DecimalSimTime.of(new BigDecimal("1E-1000")),
        DecimalSimTime.of(new BigDecimal("1.000")),
        QuantizedSimTime.of(Rational.of(1, 3)), QuantizedSimTime.of(Rational.of(1, 60)),
        QuantizedSimTime.of(Rational.of(new BigInteger("123456789012345678901234567890"),
            BigInteger.valueOf(7))), DecimalSimTime.infinity(), QuantizedSimTime.infinity());
  }

  static Observation<SimTime, String> observation(SimTime time, String id) {
    return Observation.<SimTime, String>builder()._id(id).runId("run").branchId("branch")
        .time(time).producerModel("producer").observationType("ExactTime")
        .payload("unchanged").build();
  }

  @Test
  void actorJsonColumnsPreserveExactTimePayloadAndWholeDocument() throws Exception {
    int index = 0;
    for (SimTime time : times()) {
      Observation<SimTime, String> observation = observation(time, "id-" + index++);
      String timeJson = PostgresObservationActor.writeJson(mapper, observation.getTime());
      String payloadJson = PostgresObservationActor.writeJson(mapper, observation.getPayload());
      String documentJson = PostgresObservationActor.writeJson(mapper, observation);
      JsonNode document = mapper.readTree(documentJson);
      assertEquals(mapper.readTree(timeJson), document.get("time"));
      assertEquals(mapper.readTree(mapper.writeValueAsString(observation)), document);
      assertEquals(mapper.readTree("\"unchanged\""), mapper.readTree(payloadJson));
      assertEquals(mapper.readTree(payloadJson), document.get("payload"));
      SimTime standalone = mapper.readValue(timeJson, SimTime.class);
      Observation<?, ?> restored = mapper.readValue(documentJson, Observation.class);
      assertEquals(time.getClass(), standalone.getClass());
      assertEquals(time.getClass(), restored.getTime().getClass());
      assertEquals(0, time.compareTo(standalone));
      assertEquals(0, time.compareTo(restored.getTime()));
      assertEquals(observation._id(), restored._id());
      assertEquals("unchanged", restored.getPayload());
      if (time == DecimalSimTime.infinity() || time == QuantizedSimTime.infinity()) {
        assertEquals("positive", document.get("time").get("infinity").textValue());
        assertEquals(2, document.get("time").size());
      }
    }
  }

  @Test
  void historicalLongAndDoubleJsonRemainReadableAndUnchanged() throws Exception {
    for (String json : List.of(
        "{\"timeType\":\"devs.iso.time.LongSimTime\",\"t\":9007199254740993}",
        "{\"timeType\":\"devs.iso.time.DoubleSimTime\",\"t\":0.25}")) {
      SimTime time = mapper.readValue(json, SimTime.class);
      assertEquals(mapper.readTree(json),
          mapper.readTree(PostgresObservationActor.writeJson(mapper, time)));
      JsonNode document = mapper.readTree(PostgresObservationActor.writeJson(mapper,
          observation(time, "historical")));
      assertEquals(mapper.readTree(json), document.get("time"));
    }
  }
}