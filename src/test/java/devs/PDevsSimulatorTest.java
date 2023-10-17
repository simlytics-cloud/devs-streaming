/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import devs.msg.*;
import devs.msg.time.LongSimTime;
import example.generator.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class PDevsSimulatorTest {

    static final ActorTestKit testKit = ActorTestKit.create();

    @AfterAll
    public static void cleanup() {
        testKit.shutdownTestKit();
    }

    @Test
    @DisplayName("Test PDEVS Simulator")
    void pDevsSimulatorTest() {
        TestProbe<DevsMessage> probe = testKit.createTestProbe();
        ActorRef<DevsMessage> simulator = testKit.spawn(Behaviors.setup(context ->
                new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(
                        new GeneratorModel(0),
                        LongSimTime.builder().t(0L).build(),
                        context
                )));

        // Initialize and expect next sim time to be 1
        simulator.tell(new InitSimMessage(InitSim.builder().time(LongSimTime.builder().t(0L).build()).build(), probe.getRef()));
        DevsMessage receivedMessage = probe.receiveMessage();
        assert (receivedMessage instanceof NextTime<?>);
        NextTime nextTime = (NextTime) receivedMessage;
        assert (nextTime.getTime() instanceof LongSimTime);
        assert (((LongSimTime) nextTime.getTime()).getT() == 1L);
        assert (nextTime.getSender().equals("generator"));

        // Get output and expect it to be 0
        simulator.tell(SendOutput.builder().time(LongSimTime.builder().t(1L).build()).build());
        DevsMessage message2 = probe.receiveMessage();
        assert (message2 instanceof ModelOutputMessage<?>);
        ModelOutputMessage<LongSimTime> modelOutputMessage = (ModelOutputMessage<LongSimTime>) message2;
        assert (modelOutputMessage.getNextTime().getT() == 1L);
        Bag generatorOutput = modelOutputMessage.getModelOutput();
        assert((Integer) generatorOutput.getPortValueList().get(0).getValue() == 0);

        // Execute transition and expect next time to be 1
        simulator.tell(ExecuteTransition.builder().time(
                LongSimTime.builder().t(1L).build()).build());
        DevsMessage message3 = probe.receiveMessage();
        assert (message3 instanceof TransitionDone<?>);
        TransitionDone<LongSimTime> transitionDone = (TransitionDone<LongSimTime>) message3;
        assert (transitionDone.getTime().getT() == 1L);

        // Get output and expect it to be 1
        simulator.tell(SendOutput.builder().time(LongSimTime.builder().t(1L).build()).build());
        DevsMessage message4 = probe.receiveMessage();
        assert (message4 instanceof ModelOutputMessage<?>);
        ModelOutputMessage<LongSimTime> modelOutputMessage4 = (ModelOutputMessage<LongSimTime>) message4;
        Bag generatorOutput4 = modelOutputMessage4.getModelOutput();
        assert((Integer)generatorOutput4.getPortValueList().get(0).getValue() == 1);

        // Execute transition and expect next time to be 2
        simulator.tell(ExecuteTransition.builder().time(
                LongSimTime.builder().t(1L).build()).build());
        DevsMessage message5 = probe.receiveMessage();
        assert (message5 instanceof TransitionDone<?>);
        TransitionDone<LongSimTime> transitionDone2 = (TransitionDone<LongSimTime>) message5;
        assert (transitionDone2.getNextTime().getT() == 2L);
        assert (transitionDone2.getTime().getT()  == 1L);

        // Get output and expect it to be 0
        simulator.tell(SendOutput.builder().time(LongSimTime.builder().t(2L).build()).build());
        DevsMessage message6 = probe.receiveMessage();
        assert (message6 instanceof ModelOutputMessage<?>);
        ModelOutputMessage<LongSimTime> modelOutputMessage6 = (ModelOutputMessage<LongSimTime>) message6;
        Bag generatorOutput6 = modelOutputMessage6.getModelOutput();
        assert((Integer) generatorOutput6.getPortValueList().get(0).getValue() == 0);
    }

}
