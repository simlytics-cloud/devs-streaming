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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.OutputReportPayload;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.TransitionComplete;
import devs.iso.time.LongSimTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


/**
 * This class performs unit tests for the PDevsCoordinator, focusing on its interactions with
 * simulated models and root coordinator in a Distributed Discrete Event System (DEVS) simulation.
 * It uses the ActorTestKit for testing Akka actor behaviors, verifies message passing, and checks
 * the correctness of simulation transitions, initialization, and output behaviors.
 * <p>
 * Key functionality being tested: - Initialization of the simulation through InitSimMessage. -
 * Coordination and message passing with coupled models. - Internal, external model transitions and
 * the aggregation of outputs. - Root coordinator's orchestration of simulation cycles.
 * <p>
 * The test validates that the PDevsCoordinator interacts correctly with its sub-models, passes
 * required messages at appropriate times, and ensures simulation progression with correct time
 * values.
 */
public class PDevsCoordinatorTest {

  static final ActorTestKit testKit = ActorTestKit.create();
  private static final String simulationId = "PDevsCoordinatorTest";
  private static final String coordinatorName = "genStoreCoupled";
  private static final String rootName = "root";
  private static final String generatorName = "generator";
  private static final String storageName = "storage";
  private static final long noEventTime = Long.MAX_VALUE;

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  @Test
  @DisplayName("Default coordinator creation remains backward compatible")
  void defaultCoordinatorCreationStillCoordinatesExpectedMessageFlow() {
    TestProbe<DevsMessage> generatorProbe = testKit.createTestProbe();
    TestProbe<DevsMessage> storageProbe = testKit.createTestProbe();
    TestProbe<DevsMessage> rootProbe = testKit.createTestProbe();
    ActorRef<DevsMessage> coordinator = testKit.spawn(PDevsCoordinator.create(coordinatorName,
        modelSimulators(generatorProbe, storageProbe), couplings()));

    runThreeStepScenario(coordinator, generatorProbe, storageProbe, rootProbe);
  }

  @Test
  @DisplayName("Enabled timing tracker records aggregate and per-model stats")
  void enabledTrackerAccumulatesAggregateAndPerModelStats() {
    IncrementingNanoClock nanoClock = new IncrementingNanoClock();
    StepTimingTracker tracker = StepTimingTracker.enabled(nanoClock);
    TestProbe<DevsMessage> generatorProbe = testKit.createTestProbe();
    TestProbe<DevsMessage> storageProbe = testKit.createTestProbe();
    TestProbe<DevsMessage> rootProbe = testKit.createTestProbe();
    ActorRef<DevsMessage> coordinator = testKit.spawn(PDevsCoordinator.create(coordinatorName,
        modelSimulators(generatorProbe, storageProbe), couplings(), tracker));

    runThreeStepScenario(coordinator, generatorProbe, storageProbe, rootProbe);

    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.OUTPUT_ROUND_TRIP),
        3, 110, 30, 50);
    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.COORDINATOR_PROCESSING),
        3, 80, 20, 30);
    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.TRANSITION_ROUND_TRIP),
        3, 130, 30, 50);
    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.TOTAL_STEP),
        3, 300, 80, 120);

    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.OUTPUT_ROUND_TRIP,
        generatorName), 2, 30, 10, 20);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.OUTPUT_ROUND_TRIP,
        storageName), 2, 30, 10, 20);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP,
        generatorName), 2, 60, 30, 30);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP,
        storageName), 3, 80, 20, 30);

    String summary = tracker.formatSummary(coordinatorName);
    assertTrue(summary.contains("Aggregate step metrics:"));
    assertTrue(summary.contains("Per-model RequestOutput -> OutputReport:"));
    assertTrue(summary.contains("Per-model ExecuteTransition -> TransitionComplete:"));
    assertTrue(summary.contains(generatorName + ": count=2"));
    assertTrue(summary.contains(storageName + ": count=3"));
  }

  private void runThreeStepScenario(ActorRef<DevsMessage> coordinator,
      TestProbe<DevsMessage> generatorProbe,
      TestProbe<DevsMessage> storageProbe,
      TestProbe<DevsMessage> rootProbe) {
    coordinator.tell(new SimulationInitMessage<>(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationRunId(simulationId)
        .messageId("SimulationInit")
        .senderId(rootName)
        .receiverId(coordinatorName)
        .build(), rootProbe.getRef()));

    assertInstanceOf(SimulationInitMessage.class, generatorProbe.receiveMessage());
    assertInstanceOf(SimulationInitMessage.class, storageProbe.receiveMessage());

    coordinator.tell(nextTimeReport(generatorName, 1));
    coordinator.tell(nextTimeReport(storageName, noEventTime));

    NextInternalTimeReport<LongSimTime> nextTime = assertInstanceOf(NextInternalTimeReport.class,
        rootProbe.receiveMessage());
    assertEquals(1L, nextTime.getNextInternalTime().getT());
    assertEquals(coordinatorName, nextTime.getSenderId());

    coordinator.tell(requestOutput(1));
    RequestOutput<LongSimTime> firstGeneratorRequest = assertInstanceOf(RequestOutput.class,
        generatorProbe.receiveMessage());
    assertEquals(1L, firstGeneratorRequest.getEventTime().getT());
    coordinator.tell(outputReport(generatorName, 1, 0));

    ExecuteTransition<LongSimTime> storageTransitionStep1 = assertInstanceOf(
        ExecuteTransition.class, storageProbe.receiveMessage());
    assertEquals(1, storageTransitionStep1.getPayload().getInputs().size());
    assertEquals(0, storageTransitionStep1.getPayload().getInputs().get(0).getValue());
    ExecuteTransition<LongSimTime> generatorTransitionStep1 = assertInstanceOf(
        ExecuteTransition.class, generatorProbe.receiveMessage());
    assertTrue(generatorTransitionStep1.getPayload().getInputs().isEmpty());

    coordinator.tell(transitionComplete(storageName, 1, 1));
    coordinator.tell(transitionComplete(generatorName, 1, 1));

    OutputReport<LongSimTime> firstRootOutput = assertInstanceOf(OutputReport.class,
        rootProbe.receiveMessage());
    assertEquals(1L, firstRootOutput.getNextInternalTime().getT());
    assertEquals(coordinatorName, firstRootOutput.getSenderId());

    coordinator.tell(requestOutput(1));
    RequestOutput<LongSimTime> secondGeneratorRequest = assertInstanceOf(RequestOutput.class,
        generatorProbe.receiveMessage());
    RequestOutput<LongSimTime> secondStorageRequest = assertInstanceOf(RequestOutput.class,
        storageProbe.receiveMessage());
    assertEquals(1L, secondGeneratorRequest.getEventTime().getT());
    assertEquals(1L, secondStorageRequest.getEventTime().getT());

    coordinator.tell(outputReport(generatorName, 1, 1));
    coordinator.tell(emptyOutputReport(storageName, 1));

    ExecuteTransition<LongSimTime> storageTransitionStep2 = assertInstanceOf(
        ExecuteTransition.class, storageProbe.receiveMessage());
    assertEquals(1, storageTransitionStep2.getPayload().getInputs().size());
    assertEquals(1, storageTransitionStep2.getPayload().getInputs().get(0).getValue());
    ExecuteTransition<LongSimTime> generatorTransitionStep2 = assertInstanceOf(
        ExecuteTransition.class, generatorProbe.receiveMessage());
    assertTrue(generatorTransitionStep2.getPayload().getInputs().isEmpty());

    coordinator.tell(transitionComplete(storageName, 1, 1));
    coordinator.tell(transitionComplete(generatorName, 1, 2));

    OutputReport<LongSimTime> secondRootOutput = assertInstanceOf(OutputReport.class,
        rootProbe.receiveMessage());
    assertEquals(1L, secondRootOutput.getNextInternalTime().getT());
    assertEquals(coordinatorName, secondRootOutput.getSenderId());

    coordinator.tell(requestOutput(1));
    RequestOutput<LongSimTime> thirdStorageRequest = assertInstanceOf(RequestOutput.class,
        storageProbe.receiveMessage());
    assertEquals(1L, thirdStorageRequest.getEventTime().getT());
    coordinator.tell(emptyOutputReport(storageName, 1));

    ExecuteTransition<LongSimTime> storageTransitionStep3 = assertInstanceOf(
        ExecuteTransition.class, storageProbe.receiveMessage());
    assertTrue(storageTransitionStep3.getPayload().getInputs().isEmpty());
    coordinator.tell(transitionComplete(storageName, 1, 2));

    OutputReport<LongSimTime> thirdRootOutput = assertInstanceOf(OutputReport.class,
        rootProbe.receiveMessage());
    assertEquals(2L, thirdRootOutput.getNextInternalTime().getT());
    assertEquals(coordinatorName, thirdRootOutput.getSenderId());

    coordinator.tell(SimulationTerminate.<LongSimTime>builder()
        .eventTime(LongSimTime.create(2))
        .simulationRunId(simulationId)
        .messageId("SimulationTerminate")
        .senderId(rootName)
        .receiverId(coordinatorName)
        .build());
    assertInstanceOf(SimulationTerminate.class, generatorProbe.receiveMessage());
    assertInstanceOf(SimulationTerminate.class, storageProbe.receiveMessage());

    coordinator.tell(ModelTerminated.<LongSimTime>builder()
        .simulationRunId(simulationId)
        .messageId("GeneratorModelTerminated")
        .senderId(generatorName)
        .receiverId(coordinatorName)
        .build());
    coordinator.tell(ModelTerminated.<LongSimTime>builder()
        .simulationRunId(simulationId)
        .messageId("StorageModelTerminated")
        .senderId(storageName)
        .receiverId(coordinatorName)
        .build());

    ModelTerminated<LongSimTime> terminated = assertInstanceOf(ModelTerminated.class,
        rootProbe.receiveMessage());
    assertEquals(coordinatorName, terminated.getSenderId());
  }

  private Map<String, ActorRef<DevsMessage>> modelSimulators(TestProbe<DevsMessage> generatorProbe,
      TestProbe<DevsMessage> storageProbe) {
    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put(generatorName, generatorProbe.getRef());
    modelSimulators.put(storageName, storageProbe.getRef());
    return modelSimulators;
  }

  private PDevsCouplings couplings() {
    return PDevsCouplings.builder(coordinatorName)
        .addConnection(generatorName, "OUTPUT", storageName, "INPUT")
        .build();
  }

  private RequestOutput<LongSimTime> requestOutput(long eventTime) {
    return RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(eventTime))
        .simulationRunId(simulationId)
        .messageId("RequestOutput-" + eventTime)
        .senderId(rootName)
        .receiverId(coordinatorName)
        .build();
  }

  private NextInternalTimeReport<LongSimTime> nextTimeReport(String senderId, long nextTime) {
    return NextInternalTimeReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .simulationRunId(simulationId)
        .messageId("NextTime-" + senderId + "-" + nextTime)
        .senderId(senderId)
        .receiverId(coordinatorName)
        .nextInternalTime(LongSimTime.create(nextTime))
        .build();
  }

  private OutputReport<LongSimTime> outputReport(String senderId, long eventTime, int value) {
    return OutputReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(eventTime))
        .payload(OutputReportPayload.builder()
            .addOutputs(PortValue.<Integer>builder().value(value).portName("OUTPUT").build())
            .build())
        .simulationRunId(simulationId)
        .messageId("OutputReport-" + senderId + "-" + value)
        .senderId(senderId)
        .receiverId(coordinatorName)
        .nextInternalTime(LongSimTime.create(eventTime))
        .build();
  }

  private OutputReport<LongSimTime> emptyOutputReport(String senderId, long eventTime) {
    return OutputReport.<LongSimTime>builder()
        .eventTime(LongSimTime.create(eventTime))
        .payload(OutputReportPayload.builder().build())
        .simulationRunId(simulationId)
        .messageId("OutputReport-" + senderId + "-empty")
        .senderId(senderId)
        .receiverId(coordinatorName)
        .nextInternalTime(LongSimTime.create(eventTime))
        .build();
  }

  private TransitionComplete<LongSimTime> transitionComplete(String senderId, long eventTime,
      long nextTime) {
    return TransitionComplete.<LongSimTime>builder()
        .eventTime(LongSimTime.create(eventTime))
        .simulationRunId(simulationId)
        .messageId("TransitionComplete-" + senderId + "-" + nextTime)
        .senderId(senderId)
        .receiverId(coordinatorName)
        .nextInternalTime(LongSimTime.create(nextTime))
        .build();
  }

  private void assertStats(StepTimingTracker.StatsSnapshot snapshot, long count, long total,
      long min, long max) {
    assertEquals(count, snapshot.getCount());
    assertEquals(total, snapshot.getTotalNanos());
    assertEquals(count == 0 ? 0 : total / count, snapshot.getAverageNanos());
    assertEquals(min, snapshot.getMinNanos());
    assertEquals(max, snapshot.getMaxNanos());
  }

  private static final class IncrementingNanoClock implements java.util.function.LongSupplier {
    private long current;

    @Override
    public long getAsLong() {
      current += 10;
      return current;
    }
  }
}
