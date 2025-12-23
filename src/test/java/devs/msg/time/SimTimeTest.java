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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.DevsSimMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.ImmutablePortValue;
import devs.iso.SimMessageType;
import devs.iso.TestCustomer;
import devs.utils.DevsObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SimTimeTest {

  public SimTimeTest() throws JsonProcessingException {
  }


  @Test
  public void testSimTimeSerialization() throws JsonProcessingException {
    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    // Serialize LongSimTime
    SimTime longSimTime = LongSimTime.create(12345L);
    String longJson = objectMapper.writeValueAsString(longSimTime);
    System.out.println("Serialized LongSimTime: " + longJson);

    // Deserialize LongSimTime
    SimTime deserializedLong = objectMapper.readValue(longJson, SimTime.class);
    System.out.println("Deserialized LongSimTime: " + deserializedLong);

    // Serialize DoubleSimTime
    SimTime doubleSimTime = DoubleSimTime.create(12345.67);
    String doubleJson = objectMapper.writeValueAsString(doubleSimTime);
    System.out.println("Serialized DoubleSimTime: " + doubleJson);

    // Deserialize DoubleSimTime
    SimTime deserializedDouble = objectMapper.readValue(doubleJson, SimTime.class);
    System.out.println("Deserialized DoubleSimTime: " + deserializedDouble);

  }




}
