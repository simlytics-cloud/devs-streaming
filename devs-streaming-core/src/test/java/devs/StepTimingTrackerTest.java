/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and DEVS Streaming Framework
 * Java contributors. All rights reserved.
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

import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StepTimingTrackerTest {

  @Test
  @DisplayName("Enabled tracker accumulates aggregate and per-model statistics")
  void enabledTrackerAccumulatesAggregateAndPerModelStatistics() {
    StepTimingTracker tracker = StepTimingTracker.enabled(new SequenceNanoClock(
        0,
        5, 10,
        30, 60,
        70,
        80, 90,
        100,
        140, 180,
        200,
        210,
        220,
        240,
        250,
        260,
        270,
        300,
        320,
        330,
        340));

    tracker.stepStarted();
    tracker.outputRequestSent("storage");
    tracker.outputRequestSent("generator");
    tracker.outputReportReceived("generator");
    tracker.outputReportReceived("storage");
    tracker.allOutputsReceived();
    tracker.transitionDispatchStarted();
    tracker.transitionSent("generator");
    tracker.transitionSent("storage");
    tracker.transitionDispatchCompleted();
    tracker.transitionCompleteReceived("storage");
    tracker.transitionCompleteReceived("generator");
    tracker.allTransitionsCompleted();
    tracker.stepCompleted();

    tracker.stepStarted();
    tracker.outputRequestSent("storage");
    tracker.outputReportReceived("storage");
    tracker.allOutputsReceived();
    tracker.transitionDispatchStarted();
    tracker.transitionSent("storage");
    tracker.transitionDispatchCompleted();
    tracker.transitionCompleteReceived("storage");
    tracker.allTransitionsCompleted();
    tracker.stepCompleted();

    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.OUTPUT_ROUND_TRIP),
        2, 110, 40, 70);
    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.COORDINATOR_PROCESSING),
        2, 70, 30, 40);
    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.TRANSITION_ROUND_TRIP),
        2, 180, 60, 120);
    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.TOTAL_STEP),
        2, 330, 120, 210);

    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.OUTPUT_ROUND_TRIP,
        "generator"), 1, 20, 20, 20);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.OUTPUT_ROUND_TRIP,
        "storage"), 2, 65, 10, 55);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP,
        "generator"), 1, 100, 100, 100);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP,
        "storage"), 2, 100, 50, 50);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.OUTPUT_ROUND_TRIP,
        "missing"), 0, 0, 0, 0);

    String summary = tracker.formatSummary("coordinator");
    assertTrue(summary.contains("Aggregate step metrics:"));
    assertTrue(summary.contains("Per-model RequestOutput -> OutputReport:"));
    assertTrue(summary.contains("Per-model ExecuteTransition -> TransitionComplete:"));
    assertTrue(summary.indexOf("storage: count=2") < summary.indexOf("generator: count=1"));
  }

  @Test
  @DisplayName("Disabled tracker is inert")
  void disabledTrackerIsInert() {
    StepTimingTracker tracker = StepTimingTracker.disabled();

    tracker.stepStarted();
    tracker.outputRequestSent("generator");
    tracker.outputReportReceived("generator");
    tracker.allOutputsReceived();
    tracker.transitionDispatchStarted();
    tracker.transitionSent("generator");
    tracker.transitionDispatchCompleted();
    tracker.transitionCompleteReceived("generator");
    tracker.allTransitionsCompleted();
    tracker.stepCompleted();

    assertStats(tracker.getAggregateStats(StepTimingTracker.AggregateCategory.OUTPUT_ROUND_TRIP),
        0, 0, 0, 0);
    assertStats(tracker.getModelStats(StepTimingTracker.ModelCategory.TRANSITION_ROUND_TRIP,
        "generator"), 0, 0, 0, 0);
    assertEquals("", tracker.formatSummary("coordinator"));
  }

  private void assertStats(StepTimingTracker.StatsSnapshot snapshot, long count, long total,
      long min, long max) {
    assertEquals(count, snapshot.getCount());
    assertEquals(total, snapshot.getTotalNanos());
    assertEquals(count == 0 ? 0 : total / count, snapshot.getAverageNanos());
    assertEquals(min, snapshot.getMinNanos());
    assertEquals(max, snapshot.getMaxNanos());
  }

  private static final class SequenceNanoClock implements LongSupplier {
    private final long[] values;
    private int index;

    private SequenceNanoClock(long... values) {
      this.values = values;
    }

    @Override
    public long getAsLong() {
      return values[index++];
    }
  }
}