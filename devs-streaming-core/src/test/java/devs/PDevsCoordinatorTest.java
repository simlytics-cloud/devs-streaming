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

import devs.time.DecimalTestValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import devs.couplings.CouplingResolver;
import devs.couplings.CouplingTarget;
import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.ModelTerminated;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.OutputReportPayload;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;
import devs.iso.SimulationTerminate;
import devs.iso.SimulationTerminatePayload;
import devs.iso.TransitionComplete;
import devs.iso.time.LongSimTime;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.SimTime;
import devs.time.ChildBinding;
import devs.time.CoordinatorExecutionConfig;
import devs.time.EnhancedClockContext;
import devs.time.LongTimeCodec;
import devs.time.DecimalTimeMapping;
import devs.time.TimeSemantics;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestInbox;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
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
        generatorName), 2, 80, 40, 40);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP,
        storageName), 3, 60, 20, 20);

    String summary = tracker.formatSummary(coordinatorName);
    assertTrue(summary.contains("Aggregate step metrics:"));
    assertTrue(summary.contains("Per-model RequestOutput -> OutputReport:"));
    assertTrue(summary.contains("Per-model ExecuteTransition -> TransitionComplete:"));
    assertTrue(summary.contains(generatorName + ": count=2"));
    assertTrue(summary.contains(storageName + ": count=3"));
  }

  @Test
  void legacyLifecycleAndBagsUseNaturalOrCustomOrderRegardlessOfArrivalOrder() {
    assertOrderedLifecycle(false);
  }

  @Test
  void legacyPartialImminenceInterleavesRecipientsWithInputFreeImminents() {
    assertPartialImminence(false);
  }

  @Test
  void legacyParentInputsUseChildOrderAndEmptyInputDoesNotAdvanceLastTime() {
    assertParentInputOrder(false);
  }

  @Test
  void legacyRejectsUnknownOutputAndParentInputRecipients() {
    assertUnknownRecipients(false);
  }

  static void assertOrderedLifecycle(boolean enhanced) {
    for (int variant = 0; variant < 6; variant++) {
      OrderingFixture fixture = new OrderingFixture(enhanced, variant, false);
      fixture.initialize(fixture.order, 2);
      fixture.request(fixture.order, 2);
      for (String child : shuffled(fixture.order, 20 + variant)) {
        fixture.kit.run(fixture.output(child, 2,
            List.of(port("out", child + "-second"), port("out", child + "-first"))));
      }
      List<PortValue<?>> expectedExternal = new ArrayList<>();
      List<PortValue<?>> expectedInputs = new ArrayList<>();
      for (String child : fixture.order) {
        for (String suffix : List.of("-second", "-first")) {
          expectedExternal.add(port("external-z", child + suffix));
          expectedExternal.add(port("external-a", child + suffix));
          expectedInputs.add(port("in-z", child + suffix));
          expectedInputs.add(port("in-a", child + suffix));
        }
      }
      if (enhanced) {
        fixture.assertOutput(expectedExternal, 2, 2);
        assertFalse(fixture.children.hasMessages());
        fixture.kit.run(fixture.execute(2, List.of()));
      } else {
        assertFalse(fixture.parent.hasMessages());
      }
      fixture.assertTransitions(fixture.order, 2,
          Map.of(fixture.order.get(1), expectedInputs));
      fixture.complete(fixture.order, 2, 5, 40 + variant);
      if (enhanced) {
        fixture.assertComplete(2, 5);
      } else {
        fixture.assertOutput(expectedExternal, 2, 5);
      }
      fixture.kit.run(SimulationTerminate.<SimTime>builder()
          .eventTime(fixture.parentTime(3)).simulationRunId(simulationId)
          .messageId("ordered-terminate").senderId(rootName).receiverId(coordinatorName)
          .payload(SimulationTerminatePayload.builder().reason("done").build()).build());
      for (String child : fixture.order) {
        SimulationTerminate<?> message = assertInstanceOf(SimulationTerminate.class,
            fixture.children.receiveMessage());
        assertEquals(child, message.getReceiverId());
        assertEquals(LongSimTime.create(enhanced ? 2 : 3), message.getEventTime());
      }
      assertFalse(fixture.children.hasMessages());
      List<String> terminationOrder = shuffled(fixture.order, 60 + variant);
      for (int i = 0; i < terminationOrder.size(); i++) {
        fixture.kit.run(ModelTerminated.<LongSimTime>builder().simulationRunId(simulationId)
            .messageId("terminated-" + terminationOrder.get(i)).senderId(terminationOrder.get(i))
            .receiverId(coordinatorName).build());
        if (i < terminationOrder.size() - 1) {
          assertFalse(fixture.parent.hasMessages());
        }
      }
      assertEquals(coordinatorName, assertInstanceOf(ModelTerminated.class,
          fixture.parent.receiveMessage()).getSenderId());
      assertFalse(fixture.kit.isAlive());
    }
  }

  static void assertPartialImminence(boolean enhanced) {
    for (int variant : List.of(0, 3)) {
      OrderingFixture fixture = new OrderingFixture(enhanced, variant, false);
      List<String> imminent = List.of(fixture.order.get(0), fixture.order.get(2));
      String recipient = fixture.order.get(1);
      fixture.initialize(imminent, 2);
      fixture.request(imminent, 2);
      fixture.kit.run(fixture.output(imminent.get(1), 2, List.of()));
      assertFalse(fixture.children.hasMessages());
      assertFalse(fixture.parent.hasMessages());
      fixture.kit.run(fixture.output(imminent.get(0), 2, List.of(port("out", "routed"))));
      List<PortValue<?>> expectedExternal = List.of(port("external-z", "routed"),
          port("external-a", "routed"));
      if (enhanced) {
        fixture.assertOutput(expectedExternal, 2, 2);
        fixture.kit.run(fixture.execute(2, List.of()));
      }
      List<String> affected = fixture.order.subList(0, 3);
      fixture.assertTransitions(affected, 2,
          Map.of(recipient, List.of(port("in-z", "routed"), port("in-a", "routed"))));
      fixture.complete(affected, 2, 3, 91);
      if (enhanced) {
        fixture.assertComplete(2, 3);
      } else {
        fixture.assertOutput(expectedExternal, 2, 3);
      }

      fixture.request(affected, 3);
      for (String child : shuffled(affected, 92)) {
        fixture.kit.run(fixture.output(child, 3, List.of()));
      }
      if (enhanced) {
        fixture.assertOutput(List.of(), 3, 3);
        fixture.kit.run(fixture.execute(3, List.of()));
      }
      fixture.assertTransitions(affected, 3, Map.of());
      fixture.complete(affected, 3, 8, 93);
      if (enhanced) {
        fixture.assertComplete(3, 8);
      } else {
        fixture.assertOutput(List.of(), 3, 8);
      }
    }
  }

  static void assertParentInputOrder(boolean enhanced) {
    for (int variant : List.of(1, 4)) {
      OrderingFixture fixture = new OrderingFixture(enhanced, variant, false);
      fixture.initialize(fixture.order, 5);
      if (enhanced) {
        assertThrows(IllegalStateException.class,
            () -> fixture.kit.run(fixture.execute(2, List.of())));
        assertFalse(fixture.parent.hasMessages());
      } else {
        fixture.kit.run(fixture.execute(2, List.of()));
        fixture.assertComplete(2, 5);
      }
      assertFalse(fixture.children.hasMessages());
      // Legacy empty-input acknowledgments must not commit timeLast: an earlier input is legal.
      long event = enhanced ? 3 : 1;
      fixture.kit.run(fixture.execute(event,
          List.of(port("input", "second"), port("input", "first"))));
      List<PortValue<?>> expected = List.of(port("parent-z", "second"),
          port("parent-a", "second"), port("parent-z", "first"), port("parent-a", "first"));
      Map<String, List<PortValue<?>>> inputs = new LinkedHashMap<>();
      fixture.order.forEach(child -> inputs.put(child, expected));
      fixture.assertTransitions(fixture.order, event, inputs);
      fixture.complete(fixture.order, event, 7, 111);
      fixture.assertComplete(event, 7);
    }
  }

  static void assertUnknownRecipients(boolean enhanced) {
    for (boolean parentInput : List.of(false, true)) {
      OrderingFixture fixture = new OrderingFixture(enhanced, 3, true);
      fixture.initialize(parentInput ? List.of() : List.of(fixture.order.get(0)), 2);
      RuntimeException failure;
      if (parentInput) {
        failure = assertThrows(RuntimeException.class,
            () -> fixture.kit.run(fixture.execute(1, List.of(port("input", "bad")))));
      } else {
        fixture.request(List.of(fixture.order.get(0)), 2);
        if (enhanced) {
          fixture.kit.run(fixture.output(fixture.order.get(0), 2, List.of(port("out", "bad"))));
          fixture.assertOutput(List.of(port("external-z", "bad"), port("external-a", "bad")), 2, 2);
          failure = assertThrows(IllegalArgumentException.class,
              () -> fixture.kit.run(fixture.execute(2, List.of())));
        } else {
          failure = assertThrows(RuntimeException.class, () -> fixture.kit.run(fixture.output(
              fixture.order.get(0), 2, List.of(port("out", "bad")))));
        }
      }
      assertTrue(failure.getMessage().contains("unknown-child"));
      assertFalse(fixture.children.hasMessages());
      assertFalse(fixture.parent.hasMessages());
    }
  }

  private static List<String> shuffled(List<String> ids, long seed) {
    List<String> result = new ArrayList<>(ids);
    Collections.shuffle(result, new Random(seed));
    return result;
  }

  private static PortValue<?> port(String name, Object value) {
    return PortValue.builder().value(value).portName(name).build();
  }

  private static final class OrderingFixture {
    final TestInbox<DevsMessage> children = TestInbox.create();
    final TestInbox<DevsMessage> parent = TestInbox.create();
    final List<String> order;
    final BehaviorTestKit<DevsMessage> kit;
    final boolean enhanced;
    final int variant;
    final EnhancedClockContext clock = new EnhancedClockContext("ordering", BigDecimal.ZERO);

    OrderingFixture(boolean enhanced, int variant, boolean unknownRecipient) {
      this.enhanced = enhanced;
      this.variant = variant;
      order = variant < 3 ? List.of("Alpha", "Zulu", "alpha", "beta")
          : List.of("beta", "alpha", "Zulu", "Alpha");
      Map<String, ActorRef<DevsMessage>> actors = new LinkedHashMap<>();
      Map<String, ChildBinding<?>> bindings = new LinkedHashMap<>();
      for (String child : shuffled(order, 100 + variant)) {
        actors.put(child, children.getRef());
      }
      for (String child : shuffled(order, 200 + variant)) {
        bindings.put(child, ChildBinding.nativeActor(child, children.getRef(), LongSimTime.class,
            new DecimalTimeMapping<>(new LongTimeCodec(), TimeSemantics.of(BigDecimal.ONE, BigDecimal.ZERO))));
      }
      String recipient = unknownRecipient ? "unknown-child" : order.get(1);
      CouplingResolver outputResolver = new CouplingResolver() {
        @Override
        public List<CouplingTarget> resolve(String sender, PortValue<?> value) {
          return List.of(CouplingTarget.of(coordinatorName, port("external-z", value.getValue())),
              CouplingTarget.of(coordinatorName, port("external-a", value.getValue())),
              CouplingTarget.of(recipient, port("in-z", value.getValue())),
              CouplingTarget.of(recipient, port("in-a", value.getValue())));
        }
      };
      CouplingResolver inputResolver = new CouplingResolver() {
        @Override
        public List<CouplingTarget> resolve(String sender, PortValue<?> value) {
          List<CouplingTarget> targets = new ArrayList<>();
          List<String> targetOrder = new ArrayList<>(order);
          Collections.reverse(targetOrder);
          for (String child : unknownRecipient ? List.of("unknown-child") : targetOrder) {
            targets.add(CouplingTarget.of(child, port("parent-z", value.getValue())));
            targets.add(CouplingTarget.of(child, port("parent-a", value.getValue())));
          }
          return targets;
        }
      };
      var routes = PDevsCouplings.builder(coordinatorName)
          .addResolver(coordinatorName, "input", inputResolver);
      order.forEach(child -> routes.addResolver(child, "out", outputResolver));
      PDevsCouplings couplings = routes.build();
      CoordinatorExecutionConfig config = enhanced ? new CoordinatorExecutionConfig(clock) : null;
      StepTimingTracker tracker = StepTimingTracker.disabled();
      Behavior<DevsMessage> behavior;
      if (variant % 3 == 2) {
        behavior = Behaviors.setup(context -> variant < 3
            ? new PDevsCoordinator<>(coordinatorName, actors, couplings, context, tracker,
                config, enhanced ? bindings : Map.of())
            : new PDevsCoordinator<>(coordinatorName, actors, couplings, context, tracker,
                config, enhanced ? bindings : Map.of(), order));
      } else if (enhanced) {
        behavior = variant < 3
            ? (variant == 0 ? PDevsCoordinator.enhanced(coordinatorName, actors, couplings, config, bindings)
                : PDevsCoordinator.enhanced(coordinatorName, actors, couplings, config, bindings, tracker))
            : (variant == 3 ? PDevsCoordinator.enhanced(coordinatorName, actors, couplings, config, bindings, order)
                : PDevsCoordinator.enhanced(coordinatorName, actors, couplings, config, bindings, order, tracker));
      } else {
        behavior = variant < 3
            ? (variant == 0 ? PDevsCoordinator.create(coordinatorName, actors, couplings)
                : PDevsCoordinator.create(coordinatorName, actors, couplings, tracker))
            : (variant == 3 ? PDevsCoordinator.create(coordinatorName, actors, couplings, order)
                : PDevsCoordinator.create(coordinatorName, actors, couplings, order, tracker));
      }
      kit = BehaviorTestKit.create(behavior);
    }

    SimTime parentTime(long time) {
      return enhanced ? DecimalSimTime.of(DecimalTestValues.of(time)) : LongSimTime.create(time);
    }

    void initialize(List<String> imminent, long time) {
      kit.run(new SimulationInitMessage<>(SimulationInit.<SimTime>builder()
          .eventTime(parentTime(0)).simulationRunId(simulationId).messageId("ordered-init")
          .senderId(rootName).receiverId(coordinatorName).build(), parent.getRef(),
          enhanced ? clock : null));
      for (String child : order) {
        SimulationInitMessage<?> message = assertInstanceOf(SimulationInitMessage.class,
            children.receiveMessage());
        assertEquals(child, message.getSimulationInit().getReceiverId());
        assertEquals(LongSimTime.create(0), message.getSimulationInit().getEventTime());
        assertTrue(message.getEnhancedClock().isEmpty());
      }
      assertFalse(children.hasMessages());
      List<String> replies = shuffled(order, 300 + variant);
      for (int i = 0; i < replies.size(); i++) {
        String child = replies.get(i);
        kit.run(NextInternalTimeReport.<LongSimTime>builder().eventTime(LongSimTime.create(0))
            .simulationRunId(simulationId).messageId("ordered-next-" + child).senderId(child)
            .receiverId(coordinatorName)
            .nextInternalTime(LongSimTime.create(imminent.contains(child) ? time : 8)).build());
        if (i < replies.size() - 1) {
          assertFalse(parent.hasMessages());
        }
      }
      assertEquals(parentTime(imminent.isEmpty() ? 8 : time),
          assertInstanceOf(NextInternalTimeReport.class, parent.receiveMessage()).getNextInternalTime());
    }

    void request(List<String> imminent, long time) {
      kit.run(RequestOutput.<SimTime>builder().eventTime(parentTime(time))
          .simulationRunId(simulationId).messageId("ordered-request-" + time)
          .senderId(rootName).receiverId(coordinatorName).build());
      for (String child : imminent) {
        RequestOutput<?> request = assertInstanceOf(RequestOutput.class, children.receiveMessage());
        assertEquals(child, request.getReceiverId());
        assertEquals(LongSimTime.create(time), request.getEventTime());
      }
      assertFalse(children.hasMessages());
    }

    OutputReport<LongSimTime> output(String child, long time, List<PortValue<?>> values) {
      return OutputReport.<LongSimTime>builder().eventTime(LongSimTime.create(time))
          .payload(OutputReportPayload.builder().addAllOutputs(values).build())
          .simulationRunId(simulationId).messageId("ordered-output-" + child + "-" + time)
          .senderId(child).receiverId(coordinatorName).nextInternalTime(LongSimTime.create(time)).build();
    }

    ExecuteTransition<SimTime> execute(long time, List<PortValue<?>> values) {
      return ExecuteTransition.<SimTime>builder().eventTime(parentTime(time))
          .payload(ExecuteTransitionPayload.builder().addAllInputs(values).build())
          .simulationRunId(simulationId).messageId("ordered-execute-" + time)
          .senderId(rootName).receiverId(coordinatorName).build();
    }

    void assertTransitions(List<String> affected, long time, Map<String, List<PortValue<?>>> inputs) {
      for (String child : affected) {
        ExecuteTransition<?> transition = assertInstanceOf(ExecuteTransition.class,
            children.receiveMessage());
        assertEquals(child, transition.getReceiverId());
        assertEquals(LongSimTime.create(time), transition.getEventTime());
        assertEquals(inputs.getOrDefault(child, List.of()), transition.getPayload().getInputs());
      }
      assertFalse(children.hasMessages());
      assertFalse(parent.hasMessages());
    }

    void complete(List<String> affected, long time, long next, long seed) {
      List<String> replies = shuffled(affected, seed);
      for (int i = 0; i < replies.size(); i++) {
        String child = replies.get(i);
        kit.run(TransitionComplete.<LongSimTime>builder().eventTime(LongSimTime.create(time))
            .simulationRunId(simulationId).messageId("ordered-complete-" + child + "-" + time)
            .senderId(child).receiverId(coordinatorName).nextInternalTime(LongSimTime.create(next)).build());
        if (i < replies.size() - 1) {
          assertFalse(parent.hasMessages());
        }
      }
      assertFalse(children.hasMessages());
    }

    void assertOutput(List<PortValue<?>> values, long time, long next) {
      OutputReport<?> report = assertInstanceOf(OutputReport.class, parent.receiveMessage());
      assertEquals(values, report.getPayload().getOutputs());
      assertEquals(parentTime(time), report.getEventTime());
      assertEquals(parentTime(next), report.getNextInternalTime());
      assertFalse(parent.hasMessages());
    }

    void assertComplete(long time, long next) {
      TransitionComplete<?> report = assertInstanceOf(TransitionComplete.class, parent.receiveMessage());
      assertEquals(parentTime(time), report.getEventTime());
      assertEquals(parentTime(next), report.getNextInternalTime());
      assertFalse(parent.hasMessages());
    }
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
        .payload(SimulationTerminatePayload.builder().reason("Normal termination").build())
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
