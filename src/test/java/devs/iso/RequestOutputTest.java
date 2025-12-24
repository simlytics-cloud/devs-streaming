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

public class RequestOutputTest {

  public RequestOutputTest() throws JsonProcessingException {
  }

  @Test
  public void createRequestOutput() {
    RequestOutput<LongSimTime> requestOutput = RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(ModelIdPayload.builder().modelId("vehicle").build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();
    assert requestOutput.getEventTime().getT() == 0L;
    assert requestOutput.getPayload().getModelId().equals("vehicle");
    assert requestOutput.getSimulationId().equals("run1");
    assert requestOutput.getMessageId().equals("id");
    assert requestOutput.getSenderId().equals("irp");
    assert requestOutput.getMessageType().equals(SimMessageType.RequestOutput);
  }

  @Test
  public void serializeDeserializeRequestOutput() throws JsonProcessingException {
    RequestOutput<LongSimTime> requestOutput = RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(ModelIdPayload.builder().modelId("vehicle").build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestOutput);
    DevsSimMessage deserializedSimMessage = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserializedSimMessage instanceof RequestOutput;
    assert ((RequestOutput<?>) deserializedSimMessage).getEventTime() instanceof LongSimTime;
    RequestOutput<LongSimTime> deserializedRequestOutput = (RequestOutput<LongSimTime>) deserializedSimMessage;
    assert deserializedRequestOutput.getEventTime().getT() == 0L;
    assert deserializedRequestOutput.getPayload().getModelId().equals("vehicle");
    assert deserializedRequestOutput.getSimulationId().equals("run1");
    assert deserializedRequestOutput.getMessageId().equals("id");
    assert deserializedRequestOutput.getSenderId().equals("irp");
    assert deserializedRequestOutput.getMessageType().equals(SimMessageType.RequestOutput);


}


}
