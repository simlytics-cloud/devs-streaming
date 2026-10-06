package devs.iso;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.*;
import devs.proxy.TimeWireProfile;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExactTimeMessageSerializationTest {
  private final ObjectMapper exact = DevsObjectMapper.buildObjectMapper(TimeWireProfile.EXACT_TIME_V1);
  private final ObjectMapper legacy = DevsObjectMapper.buildObjectMapper();

  @Test
  void allImplementedTimestampFieldsPreserveTypeValueAndEnvelope() throws Exception {
    for (SimTime time : List.of(DecimalSimTime.of(new BigDecimal("0.1")), DecimalSimTime.infinity(),
        QuantizedSimTime.of(Rational.of(1, 3)), QuantizedSimTime.infinity())) {
      for (DevsSimMessage message : messages(time)) {
        String json = exact.writeValueAsString(message);
        DevsSimMessage decoded = exact.readValue(json, DevsSimMessage.class);
        assertEquals(message, decoded);
        assertEquals(message.getClass(), decoded.getClass());
        SimTime actual = ((TimedMessage<?>) decoded).getEventTime();
        assertEquals(time.getClass(), actual.getClass());
        assertEquals(time, actual);
        assertThrows(Exception.class, () -> legacy.writeValueAsString(message));
        assertThrows(Exception.class, () -> legacy.readValue(json, DevsSimMessage.class));
      }
    }
    ModelTerminated<?> terminated = ModelTerminated.builder().simulationRunId("run")
        .messageId("id").senderId("model").receiverId("parent").build();
    assertFalse(exact.readTree(exact.writeValueAsString(terminated)).has("eventTime"));
  }

  private List<DevsSimMessage> messages(SimTime time) {
    return List.of(
        SimulationInit.<SimTime>builder().eventTime(time).simulationRunId("run").messageId("id").senderId("model").receiverId("parent").build(),
        SimulationTerminate.<SimTime>builder().eventTime(time).simulationRunId("run").messageId("id").senderId("model").receiverId("parent").build(),
        RequestOutput.<SimTime>builder().eventTime(time).simulationRunId("run").messageId("id").senderId("model").receiverId("parent").build(),
        ExecuteTransition.<SimTime>builder().eventTime(time).payload(ExecuteTransitionPayload.builder().build()).simulationRunId("run").messageId("id").senderId("model").receiverId("parent").build(),
        NextInternalTimeReport.<SimTime>builder().eventTime(time).simulationRunId("run").messageId("id").senderId("model").receiverId("parent").nextInternalTime(time).build(),
        OutputReport.<SimTime>builder().eventTime(time).payload(OutputReportPayload.builder().build()).simulationRunId("run").messageId("id").senderId("model").receiverId("parent").nextInternalTime(time).build(),
        TransitionComplete.<SimTime>builder().eventTime(time).simulationRunId("run").messageId("id").senderId("model").receiverId("parent").nextInternalTime(time).build());
  }

  @Test
  void taggedWrongTargetIsNotCoercedAndErrorsHaveFieldPaths() {
    String json = "{\"messageType\":\"SimulationInit\",\"simulationRunId\":\"run\",\"messageId\":\"id\",\"senderId\":\"parent\",\"receiverId\":\"model\",\"eventTime\":{\"timeType\":\"rational\",\"numerator\":\"1\",\"denominator\":\"3\"}}";
    var type = exact.getTypeFactory().constructParametricType(SimulationInit.class, DecimalSimTime.class);
    Exception error = assertThrows(Exception.class, () -> exact.readValue(json, type));
    assertTrue(error.getMessage().contains("unsupported-time-type"), error.getMessage());
    assertTrue(error.getMessage().contains("eventTime"), error.getMessage());
  }
}