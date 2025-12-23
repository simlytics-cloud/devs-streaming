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
import devs.msg.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import org.junit.jupiter.api.Test;

public class ModelTerminatedTest {

  public ModelTerminatedTest() throws JsonProcessingException {
  }

  @Test
  public void createModelTerminated() {
    ModelTerminated<LongSimTime> modelTerminated = ModelTerminated.<LongSimTime>builder()
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();
    assert modelTerminated.getSimulationId().equals("run1");
    assert modelTerminated.getMessageId().equals("id");
    assert modelTerminated.getSenderId().equals("irp");
    assert modelTerminated.getMessageType().equals(SimMessageType.ModelTerminated);

  }

  @Test
  public void serializeModelTerminated() throws JsonProcessingException {
    ModelTerminated<LongSimTime> modelTerminated = ModelTerminated.<LongSimTime>builder()
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(modelTerminated);
    DevsSimMessage deserialized = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserialized instanceof ModelTerminated;
    ModelTerminated<LongSimTime> deserializedModelTerminated = (ModelTerminated<LongSimTime>)
        deserialized;
    assert deserializedModelTerminated.getSimulationId().equals("run1");
    assert deserializedModelTerminated.getMessageId().equals("id");
    assert deserializedModelTerminated.getSenderId().equals("irp");
    assert deserializedModelTerminated.getMessageType().equals(SimMessageType.ModelTerminated);

}


}
