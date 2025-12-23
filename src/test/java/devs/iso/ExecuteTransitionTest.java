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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ExecuteTransitionTest {

  public ExecuteTransitionTest() throws JsonProcessingException {
  }


  @Test
  public void createExecuteTransition() {
    TestCustomer TestCustomer = new TestCustomer(1.0, 2.0, 3.0);
    //PortValue<TestCustomer> pv = new PortValue<>(TestCustomer, "TestCustomerOut");
    ImmutablePortValue<TestCustomer> immutablePortValue = ImmutablePortValue.<TestCustomer>builder()
        .value(TestCustomer).portName("TestCustomerIn").build();
    ExecuteTransition<LongSimTime> executeTransition = ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(ExecuteTransitionPayload.builder()
            .modelId("vehicle")
            .addInputs(immutablePortValue)
            .build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();
    assert executeTransition.getEventTime().getT() == 0L;
    assert executeTransition.getPayload().getModelId().equals("vehicle");
    List<ImmutablePortValue<?>> inputs = executeTransition.getPayload().getInputs();
    assert inputs.size() == 1;
    assert inputs.get(0).getPortName().equals("TestCustomerIn");
    assert inputs.get(0).getValue() instanceof TestCustomer;
    assert ((TestCustomer) inputs.get(0).getValue()).gettEnter() == 1.0;
    assert executeTransition.getSimulationId().equals("run1");
    assert executeTransition.getMessageId().equals("id");
    assert executeTransition.getSenderId().equals("irp");
    assert executeTransition.getMessageType().equals(SimMessageType.ExecuteTransition);
  }

  @Test
  public void serializeDeserializeExecuteTransition() throws JsonProcessingException {
    TestCustomer TestCustomer = new TestCustomer(1.0, 2.0, 3.0);
    //PortValue<TestCustomer> pv = new PortValue<>(TestCustomer, "TestCustomerOut");
    ImmutablePortValue<TestCustomer> immutablePortValue = ImmutablePortValue.<TestCustomer>builder()
        .value(TestCustomer).portName("TestCustomerIn").build();
    ExecuteTransition<LongSimTime> executeTransition = ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(ExecuteTransitionPayload.builder()
            .modelId("vehicle")
            .addInputs(immutablePortValue)
            .build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(executeTransition);
    System.out.println(json);
    DevsSimMessage deserializedSimMessage = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserializedSimMessage instanceof ExecuteTransition<?>;
    assert ((ExecuteTransition<?>) deserializedSimMessage).getEventTime() instanceof LongSimTime;
    ExecuteTransition<LongSimTime> deserializedExecuteTransition = (ExecuteTransition<LongSimTime>) deserializedSimMessage;
    assert deserializedExecuteTransition.getEventTime().getT() == 0L;
    assert deserializedExecuteTransition.getPayload().getModelId().equals("vehicle");
    List<ImmutablePortValue<?>> inputs = deserializedExecuteTransition.getPayload().getInputs();
    assert inputs.size() == 1;
    assert inputs.get(0).getPortName().equals("TestCustomerIn");
    assert inputs.get(0).getValue() instanceof TestCustomer;
    assert ((TestCustomer) inputs.get(0).getValue()).gettEnter() == 1.0;
    assert deserializedExecuteTransition.getSimulationId().equals("run1");
    assert deserializedExecuteTransition.getMessageId().equals("id");
    assert deserializedExecuteTransition.getSenderId().equals("irp");
    assert deserializedExecuteTransition.getMessageType().equals(SimMessageType.ExecuteTransition);
}


}
