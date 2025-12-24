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

package devs.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import org.junit.jupiter.api.Test;

public class NextInternalTimeReportTest {

  public NextInternalTimeReportTest() throws JsonProcessingException {
  }

  @Test
  public void createNextInternalTimeReport() {
    NextInternalTimeReport<LongSimTime> nextInternalTimeReport = NextInternalTimeReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .nextInternalTime(LongSimTime.create(3))
        .build();
    assert nextInternalTimeReport.getEventTime().getT() == 0L;
    assert nextInternalTimeReport.getSimulationId().equals("run1");
    assert nextInternalTimeReport.getMessageId().equals("id");
    assert nextInternalTimeReport.getSenderId().equals("irp");
    assert nextInternalTimeReport.getNextInternalTime().getT() == 3L;
    assert nextInternalTimeReport.getMessageType().equals(SimMessageType.NextInternalTimeReport);
  }

  @Test
  public void serializeDeserializeSimulationInit() throws JsonProcessingException {
    NextInternalTimeReport<LongSimTime> nextInternalTimeReport = NextInternalTimeReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .nextInternalTime(LongSimTime.create(3))
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nextInternalTimeReport);
    DevsSimMessage deserializedSimMessage = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserializedSimMessage instanceof NextInternalTimeReport;
    assert ((NextInternalTimeReport<?>) deserializedSimMessage).getEventTime() instanceof LongSimTime;
    NextInternalTimeReport<LongSimTime> deserializedNextInternalTime = (NextInternalTimeReport<LongSimTime>)
        deserializedSimMessage;
    assert deserializedNextInternalTime.getEventTime().getT() == 0L;
    assert deserializedNextInternalTime.getSimulationId().equals("run1");
    assert deserializedNextInternalTime.getMessageId().equals("id");
    assert deserializedNextInternalTime.getSenderId().equals("irp");
    assert deserializedNextInternalTime.getNextInternalTime().getT() == 3L;
    assert deserializedNextInternalTime.getMessageType().equals(SimMessageType.NextInternalTimeReport);
}


}
