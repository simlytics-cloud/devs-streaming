/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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
 */

package devs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.msg.Bag;
import devs.msg.DevsMessage;
import devs.msg.InitSim;
import devs.msg.InitSimMessage;
import devs.msg.ModelDone;
import devs.msg.ModelOutputMessage;
import devs.msg.NextTime;
import devs.msg.SendOutput;
import devs.msg.SimulationDone;
import devs.msg.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RootCoordinatorTest {
  static final ActorTestKit testKit = ActorTestKit.create();
  ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  @Test
  @DisplayName("Test Root Coordinator")
  void rootCoordinatorTest() throws JsonProcessingException {
    TestProbe<DevsMessage> probe = testKit.createTestProbe();
    ActorRef<DevsMessage> rootCoordinator =
        testKit.spawn(Behaviors.setup(context -> new RootCoordinator<LongSimTime>(context,
            LongSimTime.builder().t(10L).build(), probe.getRef())));
    rootCoordinator.tell(InitSim.builder().time(LongSimTime.builder().t(0L).build()).build());

    DevsMessage message1 = probe.receiveMessage();
    assert (message1 instanceof InitSimMessage<?>);
    InitSimMessage<LongSimTime> initSimMessage = (InitSimMessage<LongSimTime>) message1;
    String initSimJson = objectMapper.writeValueAsString(initSimMessage.getInitSim());
    InitSim<LongSimTime> initSimDeserialized = objectMapper.readValue(initSimJson, InitSim.class);
    assert (initSimDeserialized.getTime().getT() == 0L);
    rootCoordinator
        .tell(NextTime.builder().time(LongSimTime.builder().t(2L).build()).sender("child").build());

    DevsMessage message2 = probe.receiveMessage();
    assert (message2 instanceof SendOutput<?>);
    SendOutput<LongSimTime> sendOutput = (SendOutput<LongSimTime>) message2;
    assert (sendOutput.getTime().getT() == 2L);
    rootCoordinator.tell(ModelOutputMessage.builder().modelOutput(Bag.builder().build())
        .nextTime(LongSimTime.builder().t(5L).build()).time(LongSimTime.builder().t(2L).build())
        .sender("child").build());

    DevsMessage message3 = probe.receiveMessage();
    assert (message3 instanceof SendOutput<?>);
    SendOutput<LongSimTime> sendOutput2 = (SendOutput<LongSimTime>) message3;
    assert (sendOutput2.getTime().getT() == 5L);
    rootCoordinator.tell(ModelOutputMessage.builder().modelOutput(Bag.builder().build())
        .nextTime(LongSimTime.builder().t(11L).build()).time(sendOutput.getTime()).sender("child")
        .build());


    DevsMessage message4 = probe.receiveMessage();
    assert (message4 instanceof SimulationDone<?>);
    SimulationDone<LongSimTime> simulationDone = (SimulationDone<LongSimTime>) message4;
    assert (simulationDone.getTime().getT() == 5L);
    rootCoordinator
        .tell(ModelDone.builder().time(simulationDone.getTime()).sender("child").build());
  }
}
