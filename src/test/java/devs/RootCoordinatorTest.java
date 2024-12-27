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

/**
 * Unit test class for the {@code RootCoordinator} within the DEVS Simulation Framework.
 * <p>
 * This test validates the functionality of the Root Coordinator component, which orchestrates the
 * simulation by handling initialization, time progression, output generation, and simulation
 * completion. The test leverages Akka TestKit for actor-based testing and Jackson ObjectMapper for
 * JSON serialization/deserialization.
 * <p>
 * Key operations tested include: - Initialization of simulation with starting time. - Handling of
 * "next time" events and verification of output messages. - Receiving and processing model output
 * messages. - Simulation completion and validation of final state and messages.
 * <p>
 * Dependencies: - {@code ActorTestKit} for actor system testing. - Jackson's {@code ObjectMapper}
 * for handling JSON serialization/deserialization.
 * <p>
 * Annotations: - {@code @AfterAll} for resource cleanup after all tests. - {@code @Test} for
 * defining the rootCoordinatorTest method. - {@code @DisplayName} to describe the purpose of the
 * test.
 * <p>
 * This test ensures that the Root Coordinator adheres to the expected behavior for simulation
 * management within the DEVS framework.
 */
public class RootCoordinatorTest {

  /**
   * A static final variable representing an instance of {@code ActorTestKit}.
   * <p>
   * This variable is utilized for testing actor-based systems in a simulated environment, enabling
   * the creation of test actors, probes, and managing lifecycle operations during tests. It
   * provides an isolated and controlled environment for verifying actor behaviors, interactions,
   * and message exchanges.
   * <p>
   * The variable is initialized once for use across all test cases within the test class, ensuring
   * consistent actor testing throughout. After all tests are executed, the test kit is shut down as
   * part of the cleanup process, releasing resources.
   */
  static final ActorTestKit testKit = ActorTestKit.create();
  /**
   * An instance of a custom-configured Jackson {@code ObjectMapper}.
   * <p>
   * The {@code ObjectMapper} is initialized using the {@code DevsObjectMapper.buildObjectMapper()}
   * method, which provides support for the following: - Serialization and deserialization of Java 8
   * types such as {@code Optional} using Jdk8Module. - Integration with Google Guava types through
   * GuavaModule. - Handling of Java 8 date and time types with JavaTimeModule. - Custom
   * deserialization for {@code PortValue} objects via a {@code PortValueDeserializer}.
   * <p>
   * This {@code ObjectMapper} is utilized for JSON processing with enhanced support for custom data
   * types and configurations tailored to specific application needs.
   */
  ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  /**
   * Cleans up resources after all tests in the test class have been executed.
   * <p>
   * This method shuts down the ActorTestKit instance used throughout the test class, ensuring
   * proper release of resources and cleanup of actor-related test environments.
   * <p>
   * It is annotated with @AfterAll, so it will be executed once after all tests in the class have
   * finished running. This helps maintain resource efficiency and avoids potential memory leaks by
   * properly terminating the test environment.
   */
  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  /**
   * Tests the behavior and interaction of the RootCoordinator actor within the DEVS streaming
   * framework.
   * <p>
   * This method verifies: 1. Proper initialization of the RootCoordinator with a longitudinal
   * simulation time and correct emission of initialization messages (`InitSimMessage`). 2. Accurate
   * handling of time progression messages (`NextTime`) and proper `SendOutput` response with the
   * expected time values. 3. Correct deserialization of initialization messages into expected
   * object structures using Jackson for JSON processing, ensuring compatibility and data integrity.
   * 4. Proper state progression through the sequence of simulation steps (e.g., sending
   * `ModelOutputMessage`) and ensuring expected transitions in simulation time and outputs. 5.
   * Accurate completion of the simulation scenario, verified through the final emission of
   * `SimulationDone` and subsequent processing of `ModelDone` message.
   * <p>
   * The test relies on the Akka ActorTestKit framework and utilizes message probes to intercept,
   * inspect, and assert actor message flows.
   * <p>
   * Throws:
   *
   * @throws JsonProcessingException if JSON serialization or deserialization fails during the test
   *                                 execution.
   */
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
