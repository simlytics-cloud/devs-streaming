/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
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

package devs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.DevsMessage;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.OutputReportPayload;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.time.LongSimTime;
import devs.utils.DevsObjectMapper;
import example.generator.GeneratorModel;
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

  private static final String simulationId = "RootCoordinatorTest";

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
            LongSimTime.builder().t(10L).build(), probe.getRef(), "child")));
    rootCoordinator.tell(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId(simulationId)
        .messageId("SimulationInit")
        .senderId("TestActor")
        .receiverId("root")
        .build());

    DevsMessage message1 = probe.receiveMessage();
    assert (message1 instanceof SimulationInitMessage<?>);
    SimulationInitMessage<LongSimTime> initSimMessage = (SimulationInitMessage<LongSimTime>) message1;
    String initSimJson = objectMapper.writeValueAsString(initSimMessage.getSimulationInit());
    SimulationInit<LongSimTime> initSimDeserialized = objectMapper.readValue(initSimJson, SimulationInit.class);
    assert (initSimDeserialized.getEventTime().getT() == 0L);
    rootCoordinator.tell(NextInternalTimeReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId(simulationId)
        .messageId("NextInternalTimeReport")
        .senderId("TestActor")
        .receiverId("root")
        .nextInternalTime(LongSimTime.create(2))
        .build());

    DevsMessage message2 = probe.receiveMessage();
    assert (message2 instanceof RequestOutput<?>);
    RequestOutput<LongSimTime> sendOutput = (RequestOutput<LongSimTime>) message2;
    assert (sendOutput.getEventTime().getT() == 2L);
    rootCoordinator.tell(OutputReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(2))
        .payload(OutputReportPayload.builder().build())
        .simulationId(simulationId)
        .messageId("OutputReport")
        .senderId("child")
        .receiverId("root")
        .nextInternalTime(LongSimTime.create(5))
        .build());

    DevsMessage message3 = probe.receiveMessage();
    assert (message3 instanceof RequestOutput<?>);
    RequestOutput<LongSimTime> sendOutput2 = (RequestOutput<LongSimTime>) message3;
    assert (sendOutput2.getEventTime().getT() == 5L);
    rootCoordinator.tell(OutputReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(5))
        .payload(OutputReportPayload.builder().build())
        .simulationId(simulationId)
        .messageId("OutputReport")
        .senderId("child")
        .receiverId("root")
        .nextInternalTime(LongSimTime.create(11))
        .build());

    DevsMessage message4 = probe.receiveMessage();
    assert (message4 instanceof SimulationTerminate<?>);
    SimulationTerminate<LongSimTime> simulationDone = (SimulationTerminate<LongSimTime>) message4;
    assert (simulationDone.getEventTime().getT() == 5L);
    rootCoordinator.tell(ModelTerminated.<LongSimTime>builder()
        .simulationId(simulationId)
        .messageId("ModelTerminated")
        .senderId("child")
        .receiverId("root")
        .build());
  }
}
