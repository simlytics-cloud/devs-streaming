/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and
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

package devs.msg.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.DevsSimMessage;
import devs.iso.NextInternalTimeReport;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.SimTime;
import devs.utils.DevsObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

public class SimTimeTest {

  public SimTimeTest() throws JsonProcessingException {
  }


  @Test
  public void testSimTimeSerialization() throws JsonProcessingException {
    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    SimTime longSimTime = LongSimTime.create(12345L);
    String longJson = objectMapper.writeValueAsString(longSimTime);
    assertEquals("{\"timeType\":\"devs.iso.time.LongSimTime\",\"t\":12345}", longJson);
    assertEquals(longSimTime, objectMapper.readValue(longJson, SimTime.class));
    assertEquals(longSimTime, objectMapper.readValue(longJson, LongSimTime.class));
    assertEquals(longJson, objectMapper.writerFor(SimTime.class).writeValueAsString(longSimTime));

    SimTime doubleSimTime = DoubleSimTime.create(12345.67);
    String doubleJson = objectMapper.writeValueAsString(doubleSimTime);
    assertEquals("{\"timeType\":\"devs.iso.time.DoubleSimTime\",\"t\":12345.67}", doubleJson);
    assertEquals(doubleSimTime, objectMapper.readValue(doubleJson, SimTime.class));
    assertEquals(doubleSimTime, objectMapper.readValue(doubleJson, DoubleSimTime.class));
    assertEquals(doubleJson, objectMapper.writerFor(SimTime.class).writeValueAsString(doubleSimTime));
  }

  @Test
  public void legacyObjectReadsPreserveFieldOrderAndUnknownPropertyTolerance()
      throws JsonProcessingException {
    ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();
    assertEquals(LongSimTime.create(7), mapper.readValue(
        "{\"t\":7,\"ignored\":{\"nested\":[1,2]},\"timeType\":\"devs.iso.time.LongSimTime\"}",
        SimTime.class));
    assertEquals(DoubleSimTime.create(0.25), mapper.readValue(
        "{\"ignored\":true,\"t\":0.25,\"timeType\":\"devs.iso.time.DoubleSimTime\"}",
        DoubleSimTime.class));
  }

  @Test
  public void defaultMessageFieldsRemainLegacyNumeric() throws JsonProcessingException {
    ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();
    NextInternalTimeReport<LongSimTime> message = NextInternalTimeReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationRunId("run1").messageId("id").senderId("model").receiverId("parent")
        .nextInternalTime(LongSimTime.create(3)).build();
    String json = mapper.writeValueAsString(message);
    assertEquals(0, mapper.readTree(json).get("eventTime").longValue());
    assertEquals(3, mapper.readTree(json).get("nextInternalTime").longValue());
    assertEquals(message, mapper.readValue(json, DevsSimMessage.class));

    NextInternalTimeReport<DecimalSimTime> exactMessage = NextInternalTimeReport.<DecimalSimTime>builder()
        .eventTime(DecimalSimTime.of(BigDecimal.ZERO))
        .simulationRunId("run1").messageId("id").senderId("model").receiverId("parent")
        .nextInternalTime(DecimalSimTime.infinity()).build();
    assertThrows(JsonProcessingException.class, () -> mapper.writeValueAsString(exactMessage));
  }
}
