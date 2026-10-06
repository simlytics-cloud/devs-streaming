package devs.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.*;
import devs.msg.state.ScheduleState;
import devs.msg.state.TimeState;
import devs.proxy.TimeWireProfile;
import devs.time.Rational;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExactTimeScheduleTest {
  private final ObjectMapper mapper = DevsObjectMapper.buildObjectMapper(TimeWireProfile.EXACT_TIME_V1);

  @Test
  void mutableImmutableSchedulesAndStateKeepExactKeysAndNestedTimes() throws Exception {
    for (SimTime time : List.of(DecimalSimTime.of(new BigDecimal("1.2300")), DecimalSimTime.infinity(),
        QuantizedSimTime.of(Rational.of(1, 3)), QuantizedSimTime.infinity())) {
      Schedule<SimTime> schedule = new Schedule<>();
      schedule.scheduleInternalEvent(time, "event");
      String json = mapper.writeValueAsString(schedule);
      assertTrue(json.contains("exact-v1:"));
      Schedule<?> decoded = mapper.readValue(json, Schedule.class);
      assertEquals(schedule.schedule.keySet(), decoded.schedule.keySet());
      assertEquals(mapper.readTree(json), mapper.readTree(mapper.writeValueAsString(decoded)));
      ImmutableSchedule<?> immutable = mapper.readValue(mapper.writeValueAsString(schedule.toImmutable()), ImmutableSchedule.class);
      assertEquals(schedule.schedule.keySet(), immutable.getSchedule().keySet());
      TimeState<?> state = mapper.readValue(mapper.writeValueAsString(new TimeState<>(time)), TimeState.class);
      assertEquals(time, state.getCurrentTime());
      ScheduleState<?> scheduleState = mapper.readValue(mapper.writeValueAsString(new ScheduleState<>(time, schedule)), ScheduleState.class);
      assertEquals(time, scheduleState.getCurrentTime());
      assertEquals(schedule.schedule.keySet(), scheduleState.getSchedule().schedule.keySet());
      assertThrows(Exception.class, () -> DevsObjectMapper.buildObjectMapper().readValue(json, Schedule.class));
    }
  }

  @Test
  void exactKeysNormalizeAndLegacyPrefixesRemainSupported() throws Exception {
    Schedule<?> rational = mapper.readValue("{\"schedule\":{\"exact-v1:rational:2/6\":[]}}", Schedule.class);
    assertTrue(rational.schedule.containsKey(QuantizedSimTime.of(Rational.of(1, 3))));
    Schedule<?> legacy = mapper.readValue("{\"schedule\":{\"LongSimTime: 1\":[]}}", Schedule.class);
    assertTrue(legacy.schedule.containsKey(LongSimTime.create(1)));
    for (String key : List.of("exact-v1:rational:1/0", "exact-v1:rational:1/-3", "exact-v1:decimal:01", "exact-v1:decimal:1E10001")) {
      assertThrows(Exception.class, () -> mapper.readValue("{\"schedule\":{\"" + key + "\":[]}}", Schedule.class));
    }
    String escapedKey = "exact-v1:decimal:" + "\\u0031".repeat(1400);
    Exception error = assertThrows(Exception.class, () -> mapper.readValue("{\"schedule\":{\"" + escapedKey + "\":[]}}", Schedule.class));
    assertTrue(error.getMessage().contains("time-limit-exceeded"));
  }
}