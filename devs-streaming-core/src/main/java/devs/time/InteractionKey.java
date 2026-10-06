package devs.time;

import java.util.Objects;

/**
 * Identifies a directed model-to-model port interaction for delivery-rule lookup.
 * @param sourceModel source model identifier
 * @param sourcePort source port identifier
 * @param targetModel destination model identifier
 * @param targetPort destination port identifier
 */
public record InteractionKey(String sourceModel, String sourcePort,
                             String targetModel, String targetPort) {
  /**
   * Creates an interaction key without interpreting or rejecting blank identifiers.
   * @throws NullPointerException if any identifier is null
   */
  public InteractionKey {
    Objects.requireNonNull(sourceModel);
    Objects.requireNonNull(sourcePort);
    Objects.requireNonNull(targetModel);
    Objects.requireNonNull(targetPort);
  }
}