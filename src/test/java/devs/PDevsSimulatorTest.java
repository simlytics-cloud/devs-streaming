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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.TransitionComplete;
import devs.iso.time.LongSimTime;
import example.generator.GeneratorModel;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class for validating the functionality of the PDEVS Simulator.
 * <p>
 * This test ensures the correct behavior of the simulator in terms of initialization, state
 * transitions, output generation, and time advancements within the framework of the Parallel
 * Discrete Event System Specification (PDEVS).
 * <p>
 * It utilizes the Akka TestKit for actor-based testing and verifies the communication and responses
 * of the simulator via defined DEVS framework message interfaces.
 * <p>
 * The test focuses on the following aspects: - Initialization of the simulator with initial time
 * and model state. - Sending input messages and verifying the corresponding outputs. - Executing
 * state transitions and validating changes in system behavior and time. - Ensuring the correct
 * processing of both transition and output generation steps.
 * <p>
 * The class includes a cleanup method to shut down the Akka TestKit after the tests, ensuring
 * proper resource management.
 */
public class PDevsSimulatorTest {


  /**
   * A static and final variable representing an instance of {@code ActorTestKit}. This test kit is
   * used for testing actor-based implementations in a simulated environment. It provides support
   * for creating test actors and probes, and managing the lifecycle of actors during testing.
   * <p>
   * This instance is initialized once for use across various test cases in the class. It enables
   * consistent testing by simulating actor interactions and capturing messages.
   * <p>
   * The test environment is shut down during cleanup to release resources.
   */
  static final ActorTestKit testKit = ActorTestKit.create();
  static final String generatorName = "generator";

  /**
   * Cleans up resources after all tests in the test class have been executed.
   * <br>
   * This method shuts down the ActorTestKit used in the test class, ensuring proper resource
   * management and cleanup of actor-related resources.
   */
  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  private static final String simulationId = "PDevsSimulatorTest";

  /**
   * Tests the functionality of the PDEVS simulator.
   * <p>
   * This test verifies the correct behavior of a discrete event system simulation using the PDEVS
   * (Parallel Discrete Event System) simulator implementation. The test focuses on initialization,
   * state transitions, output generation, and time progression within the simulator.
   * <p>
   * The test includes: 1. Validating initial setup and expectation of the next simulation time. 2.
   * Generating and validating outputs from the simulator. 3. Ensuring proper state transitions and
   * their effects on simulation time.
   * <p>
   * Assertions are made on: - The type and correctness of messages exchanged within the simulator.
   * - The accuracy of simulation timestamps after events and transitions. - The output values that
   * the simulated model generates at different stages.
   */
  @Test
  @DisplayName("Test PDEVS Simulator")
  void parallelDevsSimulatorTest() {
    TestProbe<DevsMessage> probe = testKit.createTestProbe();
    GeneratorModel generatorModel = new GeneratorModel(0, generatorName);
    ActorRef<DevsMessage> simulator = testKit.spawn(Behaviors.setup(
        context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(generatorModel,
            LongSimTime.builder().t(0L).build(), context)));

    // Initialize and expect next sim time to be 1
    simulator.tell(new SimulationInitMessage(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationId(simulationId)
        .messageId("SimulationInit")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build(), probe.getRef()));
    DevsMessage receivedMessage = probe.receiveMessage();
    assert (receivedMessage instanceof NextInternalTimeReport<?>);
    NextInternalTimeReport nextTime = (NextInternalTimeReport) receivedMessage;
    assert (nextTime.getNextInternalTime() instanceof LongSimTime);
    assert (((LongSimTime) nextTime.getNextInternalTime()).getT() == 1L);
    assert ("generator".equals(nextTime.getSenderId()));

    // Get output and expect it to be 0
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message2 = probe.receiveMessage();
    assert (message2 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage = (OutputReport<LongSimTime>) message2;
    assert (modelOutputMessage.getNextInternalTime().getT() == 1L);
    List<PortValue<?>> generatorOutput = modelOutputMessage.getPayload().getOutputs();
    assert ((Integer) generatorOutput.get(0).getValue() == 0);

    // Execute transition and expect next time to be 1
    simulator.tell(ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ExecuteTransitionPayload.builder().build())
        .simulationId(simulationId)
        .messageId("ExecuteTransition")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message3 = probe.receiveMessage();
    assert (message3 instanceof TransitionComplete<?>);
    TransitionComplete<LongSimTime> transitionDone = (TransitionComplete<LongSimTime>) message3;
    assert (transitionDone.getEventTime().getT() == 1L);

    // Get output and expect it to be 1
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message4 = probe.receiveMessage();
    assert (message4 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage4 =
        (OutputReport<LongSimTime>) message4;
    List<PortValue<?>> generatorOutput4 = modelOutputMessage4.getPayload().getOutputs();
    assert ((Integer) generatorOutput4.get(0).getValue() == 1);

    // Execute transition and expect next time to be 2
    simulator.tell(ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ExecuteTransitionPayload.builder().build())
        .simulationId(simulationId)
        .messageId("ExecuteTransition")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message5 = probe.receiveMessage();
    assert (message5 instanceof TransitionComplete<?>);
    TransitionComplete<LongSimTime> transitionDone2 = (TransitionComplete<LongSimTime>) message5;
    assert (transitionDone2.getNextInternalTime().getT() == 2L);
    assert (transitionDone2.getEventTime().getT() == 1L);

    // Get output and expect it to be 0
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(2))
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .receiverId(generatorName)
        .build());
    DevsMessage message6 = probe.receiveMessage();
    assert (message6 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage6 =
        (OutputReport<LongSimTime>) message6;
    List<PortValue<?>> generatorOutput6 = modelOutputMessage6.getPayload().getOutputs();
    assert ((Integer) generatorOutput6.get(0).getValue() == 0);
  }

}
