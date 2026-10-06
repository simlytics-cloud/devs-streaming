package devs.observation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.*;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExactObservationSerializationTest {
  private final ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();

  private List<SimTime> values() {
    return List.of(LongSimTime.create(7), DoubleSimTime.create(0.1),
        DecimalSimTime.of(new BigDecimal("0.12345678901234567890123456789")), DecimalSimTime.infinity(),
        QuantizedSimTime.of(Rational.of(1, 3)), QuantizedSimTime.infinity());
  }

  private Observation<SimTime, Map<String, Object>> observation(SimTime time, int id) {
    return Observation.<SimTime, Map<String, Object>>builder()._id("observation-" + id)
        .runId("run").branchId("branch").time(time).producerModel("model")
        .observationType("exact-observations").payload(Map.of("data", "unchanged", "count", id)).build();
  }

  private void assertObservation(String json, SimTime time, int id) throws Exception {
    Observation<?, ?> decoded = assertInstanceOf(Observation.class, mapper.readValue(json, DevsObservationMessage.class));
    assertEquals(time.getClass(), decoded.getTime().getClass());
    assertEquals(time, decoded.getTime());
    assertEquals("observation-" + id, decoded._id());
    assertEquals("run", decoded.getRunId());
    assertEquals("branch", decoded.getBranchId());
    assertEquals("model", decoded.getProducerModel());
    assertEquals("exact-observations", decoded.getObservationType());
    assertEquals(Map.of("data", "unchanged", "count", id), decoded.getPayload());
    assertEquals(decoded, mapper.readValue(json, Observation.class));
  }

  @Test
  void defaultMapperAndPrintStreamPreserveOldAndNewObservationsTogether() throws Exception {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    BehaviorTestKit<DevsObservationMessage> sink = BehaviorTestKit.create(DevsObservationActor.create(stream, "run"));
    List<SimTime> values = values();
    for (int i = 0; i < values.size(); i++) {
      Observation<?, ?> observation = observation(values.get(i), i);
      assertObservation(mapper.writeValueAsString(observation), values.get(i), i);
      sink.run(observation);
    }
    sink.run(StopLogger.builder().build());
    List<String> lines = stream.toString(StandardCharsets.UTF_8).lines().toList();
    assertEquals(values.size(), lines.size());
    for (int i = 0; i < values.size(); i++) {
      assertObservation(lines.get(i), values.get(i), i);
    }
    assertEquals("devs.iso.time.LongSimTime", mapper.readTree(lines.get(0)).path("time").path("timeType").textValue());
    assertEquals("devs.iso.time.DoubleSimTime", mapper.readTree(lines.get(1)).path("time").path("timeType").textValue());
    assertEquals("0.12345678901234567890123456789", mapper.readTree(lines.get(2)).path("time").path("value").textValue());
    assertEquals("positive", mapper.readTree(lines.get(3)).path("time").path("infinity").textValue());
    assertEquals("3", mapper.readTree(lines.get(4)).path("time").path("denominator").textValue());
  }

  @Test
  void maximumRationalObservationRemainsReadableWithPrettyPrinting() throws Exception {
    SimTime time = QuantizedSimTime.of(Rational.of(new BigInteger("9".repeat(4096)), BigInteger.TEN.pow(4095)));
    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(observation(time, 99));
    assertObservation(json, time, 99);
    assertObservation(mapper.writeValueAsString(mapper.valueToTree(observation(time, 99))), time, 99);
  }

  @Test
  void fragmentLimitsCannotBeBypassedByLateObservationClassMetadata() throws Exception {
    String json = mapper.writeValueAsString(observation(DecimalSimTime.of(BigDecimal.ONE), 1));
    String classProperty = "\"@class\":\"devs.observation.Observation\"";
    String reordered = json.replace(classProperty + ",", "");
    reordered = reordered.substring(0, reordered.length() - 1) + "," + classProperty + "}";
    assertObservation(reordered, DecimalSimTime.of(BigDecimal.ONE), 1);
    String oversized = reordered.replace("\"time\":{", "\"time\":{" + " ".repeat(8256));
    Exception error = assertThrows(Exception.class, () -> mapper.readValue(oversized, DevsObservationMessage.class));
    assertTrue(error.getMessage().contains("time-limit-exceeded"), error.getMessage());
  }

  @Test
  void jsonLinesKeepsCatalogAndTypedTimesAcrossHistoricalAndExactRecords(@TempDir Path directory) throws Exception {
    ActorTestKit kit = ActorTestKit.create();
    try {
      var sink = kit.spawn(FileObservationActor.create(new FileObservationConfig(directory)));
      List<SimTime> values = values();
      for (int i = 0; i < values.size(); i++) {
        sink.tell(observation(values.get(i), i));
      }
      sink.tell(StopLogger.builder().build());
      kit.createTestProbe().expectTerminated(sink);
      List<String> lines = Files.readAllLines(directory.resolve(
          new ObservationFileNameStrategy().filenameForObservationType("exact-observations")));
      assertEquals(values.size(), lines.size());
      for (int i = 0; i < values.size(); i++) {
        assertObservation(lines.get(i), values.get(i), i);
      }
      assertEquals(1, Files.readAllLines(directory.resolve(FileObservationActor.OBSERVATION_TYPES_FILE)).size());
    } finally {
      kit.shutdownTestKit();
    }
  }
}