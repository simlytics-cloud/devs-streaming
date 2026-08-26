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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Tracks optional per-step timing information for coordinator-managed workflows.
 */
public class StepTimingTracker {

  public enum AggregateCategory {
    OUTPUT_ROUND_TRIP("RequestOutput -> OutputReport"),
    COORDINATOR_PROCESSING("OutputReport receipt -> ExecuteTransition dispatch"),
    TRANSITION_ROUND_TRIP("ExecuteTransition -> TransitionComplete"),
    TOTAL_STEP("Total step time");

    private final String label;

    AggregateCategory(String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  public enum ModelCategory {
    OUTPUT_ROUND_TRIP("Per-model RequestOutput -> OutputReport"),
    TRANSITION_ROUND_TRIP("Per-model ExecuteTransition -> TransitionComplete");

    private final String label;

    ModelCategory(String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  public static final class StatsSnapshot {
    private final long count;
    private final long totalNanos;
    private final long minNanos;
    private final long maxNanos;

    private StatsSnapshot(long count, long totalNanos, long minNanos, long maxNanos) {
      this.count = count;
      this.totalNanos = totalNanos;
      this.minNanos = minNanos;
      this.maxNanos = maxNanos;
    }

    public long getCount() {
      return count;
    }

    public long getTotalNanos() {
      return totalNanos;
    }

    public long getAverageNanos() {
      return count == 0 ? 0 : totalNanos / count;
    }

    public long getMinNanos() {
      return minNanos;
    }

    public long getMaxNanos() {
      return maxNanos;
    }
  }

  private static final StepTimingTracker DISABLED = new StepTimingTracker(false, () -> 0L);

  private final boolean enabled;
  private final LongSupplier nanoClock;
  private final EnumMap<AggregateCategory, StatsAccumulator> aggregateStats =
      new EnumMap<>(AggregateCategory.class);
  private final EnumMap<ModelCategory, Map<String, StatsAccumulator>> modelStats =
      new EnumMap<>(ModelCategory.class);
  private final Map<String, Long> pendingOutputRequests = new LinkedHashMap<>();
  private final Map<String, Long> pendingTransitionRequests = new LinkedHashMap<>();

  private Long stepStartTime;
  private Long outputPhaseStartTime;
  private Long allOutputsReceivedTime;
  private Long transitionPhaseStartTime;

  protected StepTimingTracker(boolean enabled, LongSupplier nanoClock) {
    this.enabled = enabled;
    this.nanoClock = nanoClock;
    for (AggregateCategory category : AggregateCategory.values()) {
      aggregateStats.put(category, new StatsAccumulator());
    }
    for (ModelCategory category : ModelCategory.values()) {
      modelStats.put(category, new LinkedHashMap<>());
    }
  }

  public static StepTimingTracker disabled() {
    return DISABLED;
  }

  public static StepTimingTracker enabled(LongSupplier nanoClock) {
    return new StepTimingTracker(true, Objects.requireNonNull(nanoClock, "nanoClock"));
  }

  public boolean isEnabled() {
    return enabled;
  }

  protected long now() {
    return nanoClock.getAsLong();
  }

  public void stepStarted() {
    if (!enabled) {
      return;
    }
    long timestamp = now();
    stepStartTime = timestamp;
    outputPhaseStartTime = timestamp;
    allOutputsReceivedTime = null;
    transitionPhaseStartTime = null;
    pendingOutputRequests.clear();
    pendingTransitionRequests.clear();
  }

  public void outputRequestSent(String modelId) {
    if (!enabled) {
      return;
    }
    pendingOutputRequests.put(modelId, now());
  }

  public void outputReportReceived(String modelId) {
    if (!enabled) {
      return;
    }
    Long startedAt = pendingOutputRequests.remove(modelId);
    if (startedAt != null) {
      recordModel(ModelCategory.OUTPUT_ROUND_TRIP, modelId, now() - startedAt);
    }
  }

  public void allOutputsReceived() {
    if (!enabled || outputPhaseStartTime == null) {
      return;
    }
    long timestamp = now();
    recordAggregate(AggregateCategory.OUTPUT_ROUND_TRIP, timestamp - outputPhaseStartTime);
    allOutputsReceivedTime = timestamp;
  }

  public void transitionDispatchStarted() {
    if (!enabled || allOutputsReceivedTime == null) {
      return;
    }
  }

  public void transitionSent(String modelId) {
    if (!enabled) {
      return;
    }
    long timestamp = now();
    if (allOutputsReceivedTime != null && transitionPhaseStartTime == null) {
      transitionPhaseStartTime = timestamp;
    }
    pendingTransitionRequests.put(modelId, timestamp);
  }

  public void transitionDispatchCompleted() {
    if (!enabled || allOutputsReceivedTime == null) {
      return;
    }
    recordAggregate(AggregateCategory.COORDINATOR_PROCESSING,
        now() - allOutputsReceivedTime);
  }

  public void transitionCompleteReceived(String modelId) {
    if (!enabled) {
      return;
    }
    Long startedAt = pendingTransitionRequests.remove(modelId);
    if (startedAt != null) {
      recordModel(ModelCategory.TRANSITION_ROUND_TRIP, modelId, now() - startedAt);
    }
  }

  public void allTransitionsCompleted() {
    if (!enabled || transitionPhaseStartTime == null) {
      return;
    }
    recordAggregate(AggregateCategory.TRANSITION_ROUND_TRIP, now() - transitionPhaseStartTime);
  }

  public void stepCompleted() {
    if (!enabled || stepStartTime == null) {
      return;
    }
    recordAggregate(AggregateCategory.TOTAL_STEP, now() - stepStartTime);
    stepStartTime = null;
    outputPhaseStartTime = null;
    allOutputsReceivedTime = null;
    transitionPhaseStartTime = null;
    pendingOutputRequests.clear();
    pendingTransitionRequests.clear();
  }

  public StatsSnapshot getAggregateStats(AggregateCategory category) {
    return aggregateStats.get(category).snapshot();
  }

  public StatsSnapshot getModelStats(ModelCategory category, String modelId) {
    Map<String, StatsAccumulator> categoryStats = modelStats.get(category);
    StatsAccumulator accumulator = categoryStats.get(modelId);
    if (accumulator == null) {
      return new StatsSnapshot(0, 0, 0, 0);
    }
    return accumulator.snapshot();
  }

  public Map<String, StatsSnapshot> getModelStats(ModelCategory category) {
    Map<String, StatsSnapshot> snapshots = new LinkedHashMap<>();
    modelStats.get(category).forEach((modelId, accumulator) ->
        snapshots.put(modelId, accumulator.snapshot()));
    return snapshots;
  }

  public String formatSummary(String ownerId) {
    if (!enabled) {
      return "";
    }
    StringBuilder summary = new StringBuilder();
    summary.append("Step timing summary for ").append(ownerId).append(System.lineSeparator());
    summary.append("Aggregate step metrics:");
    for (AggregateCategory category : AggregateCategory.values()) {
      summary.append(System.lineSeparator())
          .append("  ")
          .append(category.getLabel())
          .append(": ")
          .append(formatStats(getAggregateStats(category)));
    }
    appendModelSection(summary, ModelCategory.OUTPUT_ROUND_TRIP);
    appendModelSection(summary, ModelCategory.TRANSITION_ROUND_TRIP);
    return summary.toString();
  }

  private void appendModelSection(StringBuilder summary, ModelCategory category) {
    summary.append(System.lineSeparator()).append(category.getLabel()).append(":");
    List<Map.Entry<String, StatsAccumulator>> entries = new ArrayList<>(modelStats.get(category).entrySet());
    entries.sort(Comparator.comparingLong((Map.Entry<String, StatsAccumulator> entry) ->
        entry.getValue().getAverageNanos()).reversed().thenComparing(Map.Entry::getKey));
    if (entries.isEmpty()) {
      summary.append(System.lineSeparator()).append("  none");
      return;
    }
    for (Map.Entry<String, StatsAccumulator> entry : entries) {
      summary.append(System.lineSeparator())
          .append("  ")
          .append(entry.getKey())
          .append(": ")
          .append(formatStats(entry.getValue().snapshot()));
    }
  }

  private String formatStats(StatsSnapshot snapshot) {
    return "count=" + snapshot.getCount()
        + ", total=" + formatNanos(snapshot.getTotalNanos())
        + ", avg=" + formatNanos(snapshot.getAverageNanos())
        + ", min=" + formatNanos(snapshot.getMinNanos())
        + ", max=" + formatNanos(snapshot.getMaxNanos());
  }

  private String formatNanos(long nanos) {
    return String.format("%.3f ms", nanos / 1_000_000.0);
  }

  private void recordAggregate(AggregateCategory category, long durationNanos) {
    aggregateStats.get(category).record(durationNanos);
  }

  private void recordModel(ModelCategory category, String modelId, long durationNanos) {
    modelStats.get(category)
        .computeIfAbsent(modelId, ignored -> new StatsAccumulator())
        .record(durationNanos);
  }

  private static final class StatsAccumulator {
    private long count;
    private long totalNanos;
    private long minNanos = Long.MAX_VALUE;
    private long maxNanos = Long.MIN_VALUE;

    private void record(long durationNanos) {
      count++;
      totalNanos += durationNanos;
      minNanos = Math.min(minNanos, durationNanos);
      maxNanos = Math.max(maxNanos, durationNanos);
    }

    private long getAverageNanos() {
      return count == 0 ? 0 : totalNanos / count;
    }

    private StatsSnapshot snapshot() {
      if (count == 0) {
        return new StatsSnapshot(0, 0, 0, 0);
      }
      return new StatsSnapshot(count, totalNanos, minNanos, maxNanos);
    }
  }
}