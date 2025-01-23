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

import devs.CoupledModelFactory;
import devs.PDEVSModel;
import devs.PDevsCouplings;
import devs.RootCoordinator;
import devs.SimulatorProvider;
import devs.msg.DevsMessage;
import devs.msg.InitSim;
import devs.msg.time.LongSimTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Represents a test class for an experimental frame comprised of a coupled DEVS (Discrete Event
 * System Specification) model simulation. The class is responsible for setting up a test scenario,
 * executing the experimental frame, and validating the coupled model's overall functionality.
 */
public class ExperimentalFrameTest {

  /**
   * A factory for creating coupled DEVS (Discrete Event System Specification) models used in the
   * experimental frame testing within the {@link ExperimentalFrameTest} class. Encapsulates the
   * logic for initializing and connecting models, as well as defining their couplings, inputs, and
   * outputs to form a complete coupled model simulation.
   */
  protected final CoupledModelFactory<LongSimTime> coupledModelFactory;


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
  public ExperimentalFrameTest() {

    PowerOfTwoGenerator generator = new PowerOfTwoGenerator();
    LogBaseTwoCalculatorModel logCalculator = new LogBaseTwoCalculatorModel();
    TestAcceptor testAcceptor = new TestAcceptor();
    List<SimulatorProvider<LongSimTime>> simulatorProviders =
        Arrays.asList(generator.getDevsSimulatorProvider(), 
        logCalculator.getDevsSimulatorProvider(),
        testAcceptor.getDevsSimulatorProvider());

    PDevsCouplings couplings = new PDevsCouplings(Collections.emptyList(),
        Collections.singletonList(new TestOutputCouplingHandler()));
    coupledModelFactory = new CoupledModelFactory<LongSimTime>(
      "experimentalFrameTest",
      simulatorProviders,
      couplings);
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
    ActorRef<DevsMessage> testFrame =
        testKit.spawn(coupledModelFactory.create(startTime), "experimentalFrameTest");
    ActorRef<DevsMessage> rootCoordinator =
        testKit.spawn(RootCoordinator.create(endTime, testFrame), "root");
    rootCoordinator.tell(InitSim.builder().time(startTime).build());
    TestProbe<DevsMessage> testProbe = testKit.createTestProbe();
    testProbe.expectTerminated(rootCoordinator, Duration.ofSeconds(10));
    // Thread.sleep(10 * 1000);
    testKit.shutdownTestKit();
  }

}
