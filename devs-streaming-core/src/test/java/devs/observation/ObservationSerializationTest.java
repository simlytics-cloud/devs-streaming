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

package devs.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.LongSimTime;
import devs.msg.Branch;
import devs.msg.Run;
import devs.utils.DevsObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ObservationSerializationTest {

  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  @Test
  public void testRunSerialization() throws JsonProcessingException {
    Run run = Run.builder()
        ._id(UUID.randomUUID().toString())
        .name("Test Run")
        .startTime(Instant.now())
        .status(Run.RunStatus.PENDING)
        .putConfig("seed", 12345L)
        .build();

    String json = objectMapper.writeValueAsString(run);
    Run deserialized = objectMapper.readValue(json, Run.class);

    assertEquals(run._id(), deserialized._id());
    assertEquals(run.getName(), deserialized.getName());
    assertEquals(run.getStatus(), deserialized.getStatus());
    assertEquals(run.getConfig().get("seed").toString(), deserialized.getConfig().get("seed").toString());
  }

  @Test
  public void testBranchSerialization() throws JsonProcessingException {
    Branch branch = Branch.builder()
        ._id(UUID.randomUUID().toString())
        .runId(UUID.randomUUID().toString())
        .forkTime(100.0)
        .parentBranchId(UUID.randomUUID().toString())
        .description("Test Branch")
        .build();

    String json = objectMapper.writeValueAsString(branch);
    Branch deserialized = objectMapper.readValue(json, Branch.class);

    assertEquals(branch._id(), deserialized._id());
    assertEquals(branch.getRunId(), deserialized.getRunId());
    assertEquals(branch.getForkTime(), deserialized.getForkTime());
    assertEquals(branch.getParentBranchId(), deserialized.getParentBranchId());
    assertEquals(branch.getDescription(), deserialized.getDescription());
  }

  @Test
  public void testObservationSerialization() throws JsonProcessingException {
    SamplePayload payload = new SamplePayload("Test Data", 42);
    Observation<LongSimTime, SamplePayload> observation = Observation.<LongSimTime, SamplePayload>builder()
        .runId(UUID.randomUUID().toString())
        .branchId(UUID.randomUUID().toString())
        .time(LongSimTime.create(1000L))
        .producerModel("test-model")
        .observationType(SamplePayload.class.getName())
        .payload(payload)
        .build();

    String json = objectMapper.writeValueAsString(observation);
    // Use DevsLogMessage to test polymorphic deserialization
    DevsObservationMessage deserializedMsg = objectMapper.readValue(json, DevsObservationMessage.class);

    assertTrue(deserializedMsg instanceof Observation);
    Observation<LongSimTime, SamplePayload> deserialized = (Observation<LongSimTime, SamplePayload>) deserializedMsg;
    assertEquals(observation.getRunId(), deserialized.getRunId());
    assertEquals(observation.getTime(), deserialized.getTime());
    // Note: Since SamplePayload is a plain class, we might need it to be a record or have proper equals/hashCode
    assertEquals(payload.getData(), ((Map)deserialized.getPayload()).get("data"));
  }

  public static class SamplePayload {
    private String data;
    private int value;

    public SamplePayload() {}
    public SamplePayload(String data, int value) {
      this.data = data;
      this.value = value;
    }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
  }
}
