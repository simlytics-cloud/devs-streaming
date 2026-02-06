/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and
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

package devs.utils;


import static example.generator.ScheduledGeneratorModel.generatorOutputPort;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import devs.iso.time.SimTime;
import devs.utils.Schedule.Event;
import example.generator.ScheduledGeneratorModel.FlipState;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ScheduleTest {
  
  @Test
  public void testScheduleCreation() {
    Schedule<LongSimTime> schedule = new Schedule<>();
    schedule.scheduleInternalEvent(LongSimTime.create(1), new FlipState());
    schedule.scheduleOutputEvent(LongSimTime.create(1), generatorOutputPort.createPortValue(0));
    assert schedule.getFirstEventTime().equals(LongSimTime.create(1));
    List<PortValue<?>> outputs = schedule.getCurrentScheduledOutput();
    assert outputs.get(0).getValue().equals(0);
    schedule.removeCurrentScheduledOutput(LongSimTime.create(1));
    assert schedule.getCurrentScheduledOutput().isEmpty();
    List<Object> events = schedule.removeCurrentScheduledEvents(LongSimTime.create(1));
    assert events.size() == 1;
    assert events.get(0) instanceof FlipState;
    assert schedule.isEmpty();
  }
  
  @Test
  public void testScheduleMutabilityImmutability() {
    Schedule<LongSimTime> schedule = new Schedule<>();
    schedule.scheduleInternalEvent(LongSimTime.create(1), new FlipState());
    schedule.scheduleOutputEvent(LongSimTime.create(1), generatorOutputPort.createPortValue(0));
    
    ImmutableSchedule<LongSimTime> immutableSchedule = schedule.toImmutable();
    schedule = immutableSchedule.toMutable();
    assert schedule.getFirstEventTime().equals(LongSimTime.create(1));
    List<PortValue<?>> outputs = schedule.getCurrentScheduledOutput();
    assert outputs.get(0).getValue().equals(0);
    schedule.removeCurrentScheduledOutput(LongSimTime.create(1));
    assert schedule.getCurrentScheduledOutput().isEmpty();
    List<Object> events = schedule.removeCurrentScheduledEvents(LongSimTime.create(1));
    assert events.size() == 1;
    assert events.get(0) instanceof FlipState;
    assert schedule.isEmpty();
  }

  @Test
  public void testScheduleSerializatioDeserialization() throws JsonProcessingException {
    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    Schedule<LongSimTime> schedule = new Schedule<>();
    schedule.scheduleInternalEvent(LongSimTime.create(1), new FlipState());
    schedule.scheduleOutputEvent(LongSimTime.create(1), generatorOutputPort.createPortValue(0));

    ImmutableSchedule<LongSimTime> immutableSchedule = schedule.toImmutable();
    String json = objectMapper.writeValueAsString(immutableSchedule);
    immutableSchedule = objectMapper.readValue(json, ImmutableSchedule.class);
    schedule = immutableSchedule.toMutable();
    assert schedule.getFirstEventTime().equals(LongSimTime.create(1));
    List<PortValue<?>> outputs = schedule.getCurrentScheduledOutput();
    assert outputs.get(0).getValue().equals(0);
    schedule.removeCurrentScheduledOutput(LongSimTime.create(1));
    assert schedule.getCurrentScheduledOutput().isEmpty();
    List<Object> events = schedule.removeCurrentScheduledEvents(LongSimTime.create(1));
    assert events.size() == 1;
    assert events.get(0) instanceof FlipState;
    assert schedule.isEmpty();
  }

}
