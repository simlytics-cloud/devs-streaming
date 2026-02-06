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

package example.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.PortValue;
import devs.iso.time.LongSimTime;
import devs.msg.state.ScheduleState;
import devs.utils.DevsObjectMapper;
import example.generator.ScheduledGeneratorModel.FlipState;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ScheduledGeneratorModelStateTest {
  
  @Test
  public void serializeScheduledGeneratorModelState() throws JsonProcessingException {
    ScheduledGeneratorModel model = new ScheduledGeneratorModel(0);
    ScheduledGeneratorModelState state = model.getModelState();
    ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
    String json = objectMapper.writeValueAsString(state);
    state = objectMapper.readValue(json, ScheduledGeneratorModelState.class);
    assert state.getiState() == 0;
    assert state.getCurrentTime().getT() == 0L;
    assert state.getSchedule().getFirstEventTime().getT() == 1L;
    List<PortValue<?>> output = state.getSchedule().getCurrentScheduledOutput();
    assert output.size() == 1;
    assert output.get(0).getValue().equals(0);
    state.getSchedule().removeCurrentScheduledOutput(LongSimTime.create(1));
    assert state.getSchedule().getCurrentScheduledOutput().isEmpty();
    List<Object> events = state.getSchedule().removeCurrentScheduledEvents(LongSimTime.create(1));
    assert events.size() == 1;
    assert events.get(0) instanceof FlipState;
    assert state.getSchedule().isEmpty();
  }

}
