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
import devs.iso.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

public class OutputReportTest {



  public OutputReportTest() throws JsonProcessingException {
  }

  @Test
  public void createOutputReport() {
    TestCustomer customer = new TestCustomer(1.0, 2.0, 3.0);
    //PortValue<Customer> pv = new PortValue<>(customer, "customerOut");
    PortValue<TestCustomer> immutablePortValue = PortValue.<TestCustomer>builder()
        .value(customer).portName("customerOut").build();
    OutputReport<LongSimTime> outputReport = OutputReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(OutputReportPayload.builder().addOutputs(immutablePortValue).build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .nextInternalTime(LongSimTime.create(3L))
        .build();
    assert outputReport.getEventTime().getT() == 0L;
    List<PortValue<?>> outputs = outputReport.getPayload().getOutputs();
    assert outputs.size() == 1;
    assert outputs.get(0).getPortName().equals("customerOut");
    assert outputs.get(0).getValue() instanceof TestCustomer;
    assert ((TestCustomer) outputs.get(0).getValue()).gettEnter() == 1.0;
    assert outputReport.getSimulationId().equals("run1");
    assert outputReport.getMessageId().equals("id");
    assert outputReport.getSenderId().equals("irp");
    assert outputReport.getNextInternalTime().getT() == 3L;
    assert outputReport.getMessageType().equals(SimMessageType.OutputReport);
  }

  @Test
  public void serializeDeserializeOutputReport() throws JsonProcessingException {
    TestCustomer customer = new TestCustomer(1.0, 2.0, 3.0);
    //PortValue<Customer> pv = new PortValue<>(customer, "customerOut");
    PortValue<TestCustomer> immutablePortValue = PortValue.<TestCustomer>builder()
        .value(customer).portName("customerOut").build();
    OutputReport<LongSimTime> outputReport = OutputReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .payload(OutputReportPayload.builder().addOutputs(immutablePortValue).build())
        .simulationId("run1")
        .messageId("id")
        .senderId("irp")
        .nextInternalTime(LongSimTime.create(3))
        .build();

    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(outputReport);
    System.out.println(json);
    DevsSimMessage deserializedSimMessage = objectMapper.readValue(json,
        DevsSimMessage.class);
    assert deserializedSimMessage instanceof OutputReport;
    assert ((OutputReport<?>) deserializedSimMessage).getEventTime() instanceof LongSimTime;
    OutputReport<LongSimTime> deserializedOutputReport = (OutputReport<LongSimTime>) deserializedSimMessage;
    assert deserializedOutputReport.getEventTime().getT() == 0L;
    List<PortValue<?>> outputs = deserializedOutputReport.getPayload().getOutputs();
    assert outputs.size() == 1;
    assert outputs.get(0).getPortName().equals("customerOut");
    assert outputs.get(0).getValue() instanceof TestCustomer;
    assert ((TestCustomer) outputs.get(0).getValue()).gettEnter() == 1.0;
    assert deserializedOutputReport.getSimulationId().equals("run1");
    assert deserializedOutputReport.getMessageId().equals("id");
    assert deserializedOutputReport.getSenderId().equals("irp");
    assert deserializedOutputReport.getNextInternalTime().getT() == 3L;
    assert deserializedOutputReport.getMessageType().equals(SimMessageType.OutputReport);
}


}
