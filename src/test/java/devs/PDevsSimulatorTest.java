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

import devs.msg.Bag;
import devs.msg.DevsExternalMessage;
import devs.msg.DevsMessage;
import devs.msg.ExecuteTransition;
import devs.msg.InitSim;
import devs.msg.InitSimMessage;
import devs.msg.ModelOutputMessage;
import devs.msg.NextTime;
import devs.msg.SendOutput;
import devs.msg.TransitionDone;
import devs.msg.time.LongSimTime;
import devs.utils.HttpServiceActor;
import devs.utils.HttpServiceActor.Command;
import devs.utils.HttpServiceActor.ErrorResponse;
import devs.utils.HttpServiceActor.Request;
import devs.utils.HttpServiceActor.Response;
import devs.utils.HttpServiceActor.SucceffulResponse;
import example.generator.GeneratorModel;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.actor.typed.javadsl.Routers;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.checkerframework.checker.units.qual.g;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.FiniteDuration;

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
    GeneratorModel generatorModel = new GeneratorModel(0);
    ActorRef<DevsMessage> simulator = testKit.spawn(Behaviors.setup(
        context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(generatorModel,
            LongSimTime.builder().t(0L).build(), context)));

    // Initialize and expect next sim time to be 1
    simulator.tell(new InitSimMessage(
        InitSim.builder().time(LongSimTime.builder().t(0L).build()).build(), probe.getRef()));
    DevsMessage receivedMessage = probe.receiveMessage();
    assert (receivedMessage instanceof NextTime<?>);
    NextTime nextTime = (NextTime) receivedMessage;
    assert (nextTime.getTime() instanceof LongSimTime);
    assert (((LongSimTime) nextTime.getTime()).getT() == 1L);
    assert ("generator".equals(nextTime.getSender()));

    // Get output and expect it to be 0
    simulator.tell(SendOutput.builder().time(LongSimTime.builder().t(1L).build()).build());
    DevsMessage message2 = probe.receiveMessage();
    assert (message2 instanceof ModelOutputMessage<?>);
    ModelOutputMessage<LongSimTime> modelOutputMessage = (ModelOutputMessage<LongSimTime>) message2;
    assert (modelOutputMessage.getNextTime().getT() == 1L);
    Bag generatorOutput = modelOutputMessage.getModelOutput();
    assert ((Integer) generatorOutput.getPortValueList().get(0).getValue() == 0);

    // Execute transition and expect next time to be 1
    simulator.tell(ExecuteTransition.builder().time(LongSimTime.builder().t(1L).build()).build());
    DevsMessage message3 = probe.receiveMessage();
    assert (message3 instanceof TransitionDone<?>);
    TransitionDone<LongSimTime> transitionDone = (TransitionDone<LongSimTime>) message3;
    assert (transitionDone.getTime().getT() == 1L);

    // Get output and expect it to be 1
    simulator.tell(SendOutput.builder().time(LongSimTime.builder().t(1L).build()).build());
    DevsMessage message4 = probe.receiveMessage();
    assert (message4 instanceof ModelOutputMessage<?>);
    ModelOutputMessage<LongSimTime> modelOutputMessage4 =
        (ModelOutputMessage<LongSimTime>) message4;
    Bag generatorOutput4 = modelOutputMessage4.getModelOutput();
    assert ((Integer) generatorOutput4.getPortValueList().get(0).getValue() == 1);

    // Execute transition and expect next time to be 2
    simulator.tell(ExecuteTransition.builder().time(LongSimTime.builder().t(1L).build()).build());
    DevsMessage message5 = probe.receiveMessage();
    assert (message5 instanceof TransitionDone<?>);
    TransitionDone<LongSimTime> transitionDone2 = (TransitionDone<LongSimTime>) message5;
    assert (transitionDone2.getNextTime().getT() == 2L);
    assert (transitionDone2.getTime().getT() == 1L);

    // Get output and expect it to be 0
    simulator.tell(SendOutput.builder().time(LongSimTime.builder().t(2L).build()).build());
    DevsMessage message6 = probe.receiveMessage();
    assert (message6 instanceof ModelOutputMessage<?>);
    ModelOutputMessage<LongSimTime> modelOutputMessage6 =
        (ModelOutputMessage<LongSimTime>) message6;
    Bag generatorOutput6 = modelOutputMessage6.getModelOutput();
    assert ((Integer) generatorOutput6.getPortValueList().get(0).getValue() == 0);
  }

}
