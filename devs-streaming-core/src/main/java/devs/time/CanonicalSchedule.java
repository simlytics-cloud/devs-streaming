package devs.time;

import devs.iso.PortValue;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Exact event and effective-delivery ordering, independent of actor response order. */
public final class CanonicalSchedule {
  private final CoordinatorExecutionConfig config;
  private final Map<String, ChildBinding<?>> bindings;
  private final List<String> resolvedChildOrder;
  private final Map<String, QuantizedSimTime> candidates = new HashMap<>();
  private final PriorityQueue<PendingDelivery> pending = new PriorityQueue<>(
      Comparator.comparing(PendingDelivery::effectiveTime)
          .thenComparingLong(PendingDelivery::sequence));
  private long sequence;

  /**
   * Creates an empty schedule with immutable copies of the bindings and resolved child order.
   * The coordinator supplies an order already validated to cover every child exactly once.
   * @throws IllegalArgumentException if a binding identifier differs from its key,
   *     or an enhanced child has a different clock
   * @throws NullPointerException if configuration, bindings, a binding key, a binding,
   *     resolved child order, or an order element is null
   */
  public CanonicalSchedule(CoordinatorExecutionConfig config, Map<String, ChildBinding<?>> bindings,
      List<String> resolvedChildOrder) {
    this.config = Objects.requireNonNull(config);
    this.bindings = Map.copyOf(bindings);
    this.resolvedChildOrder = List.copyOf(resolvedChildOrder);
    this.bindings.forEach((id, binding) -> {
      if (!id.equals(binding.modelIdentifier())) {
        throw new IllegalArgumentException("Child binding identifier mismatch");
      }
      binding.validateClock(config.clock());
    });
  }

  /** Returns whether every child has a candidate next event; an empty schedule is initialized. */
  public boolean initialized() {
    return candidates.size() == bindings.size();
  }

  /**
   * Converts a child's native timestamp to exact canonical seconds, preserving infinity.
   * @throws IllegalArgumentException if the child identifier is unknown
   * @throws ClassCastException if the timestamp has the wrong native type
   * @throws ArithmeticException if the child's mapping rejects the timestamp
   */
  public QuantizedSimTime decode(String child, SimTime nativeTime) {
    ChildBinding<?> binding = bindings.get(child);
    if (binding == null) {
      throw new IllegalArgumentException("Unknown child " + child);
    }
    return binding.toCanonical(nativeTime);
  }

  /**
   * Stores a child's next native event after exact canonical conversion.
   * The next time may equal completed or be infinite; completed must be finite.
   * @param completed last completed logical time in canonical seconds
   * @throws IllegalArgumentException if completed is infinite or the candidate precedes it
   */
  public void update(String child, SimTime next, QuantizedSimTime completed) {
    QuantizedSimTime candidate = decode(child, next);
    if (completed.isInfinite() || candidate.compareTo(completed) < 0) {
      throw new IllegalArgumentException("Child next event rolls back logical time");
    }
    candidates.put(child, candidate);
  }

  /**
   * Returns the earliest child event or effective delivery time in canonical seconds,
   * or infinity when neither provides a finite next time.
   * @throws IllegalStateException if any child candidate is missing
   */
  public QuantizedSimTime next() {
    if (!initialized()) {
      throw new IllegalStateException("Missing child next times");
    }
    QuantizedSimTime next = candidates.values().stream()
        .min(QuantizedSimTime::compareTo).orElse(QuantizedSimTime.infinity());
    return pending.isEmpty() || next.compareTo(pending.peek().effectiveTime()) <= 0
        ? next : pending.peek().effectiveTime();
  }

  /**
   * Returns children whose stored candidates equal the canonical-second time, in the
   * supplied resolved order. Children with no candidate do not match.
   */
  public List<String> imminent(QuantizedSimTime time) {
    return resolvedChildOrder.stream().filter(id -> time.equals(candidates.get(id))).toList();
  }

  /**
   * Returns the child's stored candidate in canonical seconds, possibly infinity.
   * @throws NullPointerException if no candidate is stored for the identifier
   */
  public QuantizedSimTime candidate(String child) {
    return Objects.requireNonNull(candidates.get(child), "Missing child candidate");
  }

  /**
   * Adapts a finite canonical-second source time upward using the target's codec,
   * observes any error budget, then queues the delivery by effective time and insertion order.
   * Budget callback failures propagate before an entry is queued.
   * @throws IllegalArgumentException if the target is missing, or adaptation violates causality
   * @throws ArithmeticException if adaptation fails or the insertion sequence overflows
   */
  public PendingDelivery enqueue(InteractionKey interaction, PortValue<?> value,
      QuantizedSimTime source) {
    ChildBinding<?> target = bindings.get(interaction.targetModel());
    if (target == null) {
      throw new IllegalArgumentException("Unknown delivery target " + interaction.targetModel());
    }
    SimTime local = target.adapt(source);
    QuantizedSimTime effective = target.toCanonical(local);
    config.observe(interaction, source, effective);
    PendingDelivery delivery = new PendingDelivery(interaction, value, source, effective,
        local, sequence);
    sequence = Math.incrementExact(sequence);
    pending.add(delivery);
    return delivery;
  }

  /**
   * Removes and returns deliveries exactly due at the canonical-second time, in insertion
   * order, as an immutable list. Later deliveries remain queued.
   * @throws IllegalStateException if an earlier effective delivery time was missed
   */
  public List<PendingDelivery> drain(QuantizedSimTime time) {
    if (!pending.isEmpty() && pending.peek().effectiveTime().compareTo(time) < 0) {
      throw new IllegalStateException("Missed effective delivery time");
    }
    List<PendingDelivery> due = new ArrayList<>();
    while (!pending.isEmpty() && pending.peek().effectiveTime().equals(time)) {
      due.add(pending.remove());
    }
    return List.copyOf(due);
  }

  /** Removes all pending deliveries and child candidates and resets the insertion sequence. */
  public void clear() {
    pending.clear();
    candidates.clear();
    sequence = 0;
  }
}
