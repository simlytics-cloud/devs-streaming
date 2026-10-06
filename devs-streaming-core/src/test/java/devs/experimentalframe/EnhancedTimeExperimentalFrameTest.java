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

package devs.experimentalframe;

import devs.*;
import devs.iso.DevsMessage;
import devs.iso.SimulationInit;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.time.*;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Represents a test class for an experimental frame comprised of a coupled DEVS (Discrete Event
 * System Specification) model simulation. The class is responsible for setting up a test scenario,
 * executing the experimental frame, and validating the coupled model's overall functionality.
 */
public class EnhancedTimeExperimentalFrameTest {

  /**
   * A factory for creating coupled DEVS (Discrete Event System Specification) models used in the
   * experimental frame testing within the {@link EnhancedTimeExperimentalFrameTest} class. Encapsulates the
   * logic for initializing and connecting models, as well as defining their couplings, inputs, and
   * outputs to form a complete coupled model simulation.
   */
  protected final CoupledModelFactory<QuantizedSimTime> coupledModelFactory;
  private final EnhancedClockContext clock =
      new EnhancedClockContext("experimental-frame", Rational.ZERO);


  /**
   * Constructs an instance of the ExperimentalFrameTest class.
   * <p>
   * This constructor initializes the coupled DEVS model factory `coupledModelFactory`, which
   * represents a coupled simulation model configured with specific generators, calculators, and
   * acceptors. The coupled model factory is used to create a composite simulation system for
   * experimental frame testing.
   * <p>
   * The components that form the internal coupled model include: - `PowerOfTwoGenerator`: A
   * generator producing sequences of numbers and corresponding words. -
   * `LogBaseTwoCalculatorModel`: A model that performs logarithmic calculations and transforms
   * inputs. - `TestAcceptor`: A model designed to accept and validate the outputs against expected
   * results.
   * <p>
   * These models are connected using the `PDevsCouplings` class, which defines the couplings among
   * inputs, outputs, and specific coupling handlers. The overall coupled model is constructed with
   * these components and configured via the `coupledModelFactory`.
   */
  public EnhancedTimeExperimentalFrameTest() {

    PowerOfTwoGenerator generator = new PowerOfTwoGenerator();
    Instant now = Instant.now();
    double epochSeconds = (double) now.getEpochSecond() + now.getNano() / 1e9;
    BigDecimal start = new BigDecimal(String.valueOf(epochSeconds));
    LogBaseTwoDecimalCalculatorModel logCalculator = new LogBaseTwoDecimalCalculatorModel(start);
    TestAcceptor testAcceptor = new TestAcceptor();
    ChildBinding<LongSimTime> generatorBinding = ChildBinding.nativeProvider(new DevsSimulatorProvider<>(generator), LongSimTime.class,
            new TimeMapping<>(new LongTimeCodec(), TimeSemantics.of(Rational.ONE, Rational.ZERO)
            ));
    ChildBinding<DecimalSimTime> calculatorBinding = ChildBinding
            .defaultDecimalProvider(new DevsSimulatorProvider<>(logCalculator), 1.0);
    ChildBinding<LongSimTime> acceptorBinding = ChildBinding
            .defaultLongProvider(new DevsSimulatorProvider<>(testAcceptor), 1.0);
    PDevsCouplings couplings = PDevsCouplings.builder("experimentalFrameTest")
        .addConnection("PowerOfTwoGenerator", "numbers", LogBaseTwoCalculatorModel.MODEL_ID, "numberIn")
        .addConnection("PowerOfTwoGenerator", "words", LogBaseTwoCalculatorModel.MODEL_ID, "wordIn")
        .addConnection(LogBaseTwoCalculatorModel.MODEL_ID, "numberOut", TestAcceptor.modelIdentifier, "acceptNumber")
        .addConnection(LogBaseTwoCalculatorModel.MODEL_ID, "wordOut", TestAcceptor.modelIdentifier, "acceptWord")
        .build();
    coupledModelFactory = CoupledModelFactory.enhanced(
        "experimentalFrameTest", List.of(generatorBinding, calculatorBinding, acceptorBinding),
        couplings, new CoordinatorExecutionConfig(clock));
  }

  /**
   * Tests the execution of a coupled DEVS simulation model within a configured experimental frame.
   * <p>
   * This method verifies the behavior of a coupled model created using the `coupledModelFactory` by
   * executing it within a simulation range defined by a start and end simulation time. The test
   * uses the experimental frame components, such as generating inputs, calculating outputs, and
   * validating results through interactions and couplings within the model.
   * <p>
   * It calls the `executeExperimentalFrame` method to run the test.
   *
   * @throws InterruptedException if the test execution is interrupted unexpectedly
   */
  @Test
  @DisplayName("Test coupled model in experimental frame")
  protected void testCoupledModel() throws InterruptedException {
    executeExperimentalFrame(LongSimTime.builder().t(0L).build(),
        LongSimTime.builder().t(8L).build());
  }

  /**
   * Executes the simulation of a coupled DEVS (Discrete Event System Specification) model within a
   * configured experimental frame.
   * <p>
   * This method initializes the test environment using the ActorTestKit, spawns the necessary
   * actors (including the root coordinator and test frame), and triggers the simulation. The
   * simulation is executed within the range specified by the start and end simulation times. Upon
   * completing the simulation, the test environment is properly shut down.
   *
   * @param startTime The simulation start time for the experimental frame.
   * @param endTime   The simulation end time for the experimental frame.
   * @throws InterruptedException If the execution is interrupted while running the simulation.
   */
  protected void executeExperimentalFrame(LongSimTime startTime, LongSimTime endTime)
      throws InterruptedException {
    ActorTestKit testKit = ActorTestKit.create();
    QuantizedSimTime canonicalStart = QuantizedSimTime.of(Rational.of(startTime.getT()));
    QuantizedSimTime canonicalEnd = QuantizedSimTime.of(Rational.of(endTime.getT()));
    ActorRef<DevsMessage> testFrame =
        testKit.spawn(coupledModelFactory.create(canonicalStart), "experimentalFrameTest");
    ActorRef<DevsMessage> rootCoordinator =
        testKit.spawn(RootCoordinator.enhanced(canonicalEnd, testFrame, "experimentalFrameTest", clock), "root");
    rootCoordinator.tell(SimulationInit.<QuantizedSimTime>builder()
        .eventTime(canonicalStart)
        .simulationRunId("ExperimentalFrameTest")
        .messageId("SimulationInit")
        .senderId("TestActor")
        .receiverId("root")
        .build());
    TestProbe<DevsMessage> testProbe = testKit.createTestProbe();
    testProbe.expectTerminated(rootCoordinator, Duration.ofSeconds(100));
    // Thread.sleep(10 * 1000);
    testKit.shutdownTestKit();
  }

}
