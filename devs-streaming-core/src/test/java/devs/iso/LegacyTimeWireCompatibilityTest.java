package devs.iso;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.*;
import devs.utils.DevsObjectMapper;
import org.junit.jupiter.api.Test;

class LegacyTimeWireCompatibilityTest {
  private final ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();

  private SimTime decode(String token) throws Exception {
    String json = "{\"messageType\":\"SimulationInit\",\"simulationRunId\":\"r\",\"messageId\":\"i\",\"senderId\":\"s\",\"receiverId\":\"d\",\"eventTime\":" + token + "}";
    return ((SimulationInit<?>) mapper.readValue(json, DevsSimMessage.class)).getEventTime();
  }

  @Test
  void freezesLongFirstDoubleFallbackIncludingLegacyStringsAndOverflow() throws Exception {
    for (String token : new String[]{"1", "\"1\"", "-0", "\"+1\"", "9223372036854775807", "-9223372036854775808"}) {
      assertInstanceOf(LongSimTime.class, decode(token));
    }
    for (String token : new String[]{"1.0", "1e0", "\"1.0\"", "-0.0", "9223372036854775808", "1.7976931348623157E308", "1e309", "\"Infinity\""}) {
      assertInstanceOf(DoubleSimTime.class, decode(token));
    }
    assertEquals(1.0, ((DoubleSimTime) decode("1e0")).getT());
    assertEquals(Double.POSITIVE_INFINITY, ((DoubleSimTime) decode("1e309")).getT());
    assertTrue(Double.isNaN(((DoubleSimTime) decode("\"NaN\"")).getT()));
  }
}