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

public class SimulationTerminateTest {

  @Test
  public void createSimulationTerminate() {
    SimulationTerminate<LongSimTime> simulationTerminate = SimulationTerminate.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .receiverId("vehicle")
        .payload(SimulationTerminatePayload.builder().reason("ended").build())
        .build();
    assert simulationTerminate.getEventTime().getT() == 0L;
    assert simulationTerminate.getSimulationId().equals("run1");
    assert simulationTerminate.getMessageId().equals("id");
    assert simulationTerminate.getSenderId().equals("irp");
    assert simulationTerminate.getMessageType().equals(SimMessageType.SimulationTerminate);
  }

  @Test
  public void serializeDeserializeSimulationInit() throws JsonProcessingException {
    SimulationTerminate<LongSimTime> simulationTerminate = SimulationTerminate.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .receiverId("vehicle")
        .payload(SimulationTerminatePayload.builder().reason("ended").build())
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simulationTerminate);
    DevsSimMessage deserializedSimMessage = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserializedSimMessage instanceof SimulationTerminate;
    assert ((SimulationTerminate<?>) deserializedSimMessage).getEventTime() instanceof LongSimTime;
    SimulationTerminate<LongSimTime> deserializedSimulationTerminate =
        (SimulationTerminate<LongSimTime>) deserializedSimMessage;
    assert deserializedSimulationTerminate.getEventTime().getT() == 0L;
    assert deserializedSimulationTerminate.getPayload().getReason().equals("ended");
    assert deserializedSimulationTerminate.getSimulationId().equals("run1");
    assert deserializedSimulationTerminate.getMessageId().equals("id");
    assert deserializedSimulationTerminate.getSenderId().equals("irp");
    assert deserializedSimulationTerminate.getMessageType().equals(SimMessageType.SimulationTerminate);
  }

}
