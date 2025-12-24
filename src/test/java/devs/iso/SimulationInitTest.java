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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimulationInitTest {

  public SimulationInitTest() throws JsonProcessingException {
  }

  @Test
  public void createSimulationInit() {
    SimulationInit<LongSimTime> simulationInit = SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(ModelIdPayload.builder().modelId("vehicle").build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();
    assert simulationInit.getEventTime().getT() == 0L;
    assert simulationInit.getPayload().getModelId().equals("vehicle");
    assert simulationInit.getSimulationId().equals("run1");
    assert simulationInit.getMessageId().equals("id");
    assert simulationInit.getSenderId().equals("irp");
    assert simulationInit.getMessageType().equals(SimMessageType.SimulationInit);
  }

  @Test
  public void serializeDeserializeSimulationInit() throws JsonProcessingException {
    SimulationInit<LongSimTime> simulationInit = SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(ModelIdPayload.builder().modelId("vehicle").build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simulationInit);
    DevsSimMessage deserializedSimMessage = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserializedSimMessage instanceof SimulationInit;
    assert ((SimulationInit<?>) deserializedSimMessage).getEventTime() instanceof LongSimTime;
    SimulationInit<LongSimTime> deserializedSimulationInit = (SimulationInit<LongSimTime>) deserializedSimMessage;
    assert deserializedSimulationInit.getEventTime().getT() == 0L;
    assert deserializedSimulationInit.getPayload().getModelId().equals("vehicle");
    assert deserializedSimulationInit.getSimulationId().equals("run1");
    assert deserializedSimulationInit.getMessageId().equals("id");
    assert deserializedSimulationInit.getSenderId().equals("irp");

    // Try with a Double value
    SimulationInit<DoubleSimTime> doubleSimulationInit = SimulationInit.<DoubleSimTime>builder()
        .eventTime(DoubleSimTime.create(0.5))
        .payload(ModelIdPayload.builder().modelId("vehicle").build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();

    String jsonDouble = objectMapper.writerWithDefaultPrettyPrinter()
        .writeValueAsString(doubleSimulationInit);
    SimulationInit<DoubleSimTime> deserializedSimulationInitDouble = objectMapper.readValue(jsonDouble,
        SimulationInit.class);
    assert deserializedSimulationInitDouble.getEventTime() instanceof DoubleSimTime;
    assertEquals(0.5, doubleSimulationInit.getEventTime().getT(), 0.00000001);
    assert deserializedSimulationInitDouble.getPayload().getModelId().equals("vehicle");
    assert deserializedSimulationInitDouble.getSimulationId().equals("run1");
    assert deserializedSimulationInitDouble.getMessageId().equals("id");
    assert deserializedSimulationInitDouble.getSenderId().equals("irp");
    assert deserializedSimulationInit.getMessageType().equals(SimMessageType.SimulationInit);
}


}
