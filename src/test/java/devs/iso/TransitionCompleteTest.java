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

public class TransitionCompleteTest {

  public TransitionCompleteTest() throws JsonProcessingException {
  }

  @Test
  public void createTransitionComplete() {
    TransitionComplete<LongSimTime> transitionComplete = TransitionComplete.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .nextInternalTime(LongSimTime.create(3))
        .build();
    assert transitionComplete.getEventTime().getT() == 0L;
    assert transitionComplete.getSimulationId().equals("run1");
    assert transitionComplete.getMessageId().equals("id");
    assert transitionComplete.getSenderId().equals("irp");
    assert transitionComplete.getNextInternalTime().getT() == 3L;
    assert transitionComplete.getMessageType().equals(SimMessageType.TransitionComplete);
  }

  @Test
  public void serializeDeserializeTransitionComplete() throws JsonProcessingException {
    TransitionComplete<LongSimTime> transitionComplete = TransitionComplete.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .nextInternalTime(LongSimTime.create(3))
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(transitionComplete);
    DevsSimMessage deserializedSimMessage = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserializedSimMessage instanceof TransitionComplete<?>;
    assert ((TransitionComplete<?>) deserializedSimMessage).getEventTime() instanceof LongSimTime;
    TransitionComplete<LongSimTime> deserializedTransitionCompete = (TransitionComplete<LongSimTime>)
        deserializedSimMessage;
    assert deserializedTransitionCompete.getEventTime().getT() == 0L;
    assert deserializedTransitionCompete.getSimulationId().equals("run1");
    assert deserializedTransitionCompete.getMessageId().equals("id");
    assert deserializedTransitionCompete.getSenderId().equals("irp");
    assert deserializedTransitionCompete.getNextInternalTime().getT() == 3L;
    assert deserializedTransitionCompete.getMessageType().equals(SimMessageType.TransitionComplete);
}


}
