package devs.iso.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.utils.DevsObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExactTimeJsonCodecDecimalTest {
  private final ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();

  @Test
  void exactProfileRoundTripsNormalizedDecimal() throws Exception {
    var input = DecimalSimTime.of(new BigDecimal("1.2300"));
    var json = mapper.writeValueAsString(input);
    assertEquals("{\"timeType\":\"decimal\",\"value\":\"1.23\"}", json);
    assertEquals(input, mapper.readValue(json, SimTime.class));
  }

  @Test
  void rejectsRemovedTimestampVariant() {
    assertThrows(Exception.class, () -> mapper.readValue(
        "{\"timeType\":\"obsolete\",\"first\":\"1\",\"second\":\"3\"}", SimTime.class));
  }
}
